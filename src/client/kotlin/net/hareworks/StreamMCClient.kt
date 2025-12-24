package net.hareworks

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.hareworks.config.ConfigManager
import net.hareworks.config.StreamMCConfig
import net.hareworks.screen.StreamConfigScreen
import net.hareworks.youtube.ChatPoller
import net.hareworks.youtube.model.LiveChatMessage
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.time.Instant
import java.util.PriorityQueue

data class QueuedMessage(val message: LiveChatMessage, val displayTime: Long) : Comparable<QueuedMessage> {
    override fun compareTo(other: QueuedMessage): Int = this.displayTime.compareTo(other.displayTime)
}

enum class EventType {
    CHAT,
    SUPERCHAT,
    SUPERSTICKER,
    NEW_SPONSOR,
    MEMBER_MILESTONE
}

object StreamMCClient : ClientModInitializer {
    var poller: ChatPoller? = null
    var apiKey: String? = null
    var videoId: String? = null
    var pollingIntervalMs: Long = 5000L
    var chatFormat: String = "§e%author%: §f%message%"
    var showChatInGame: Boolean = true
    
    val eventMap = mutableMapOf<EventType, String>()
    
    private val displayQueue = PriorityQueue<QueuedMessage>()

    override fun onInitializeClient() {
        // Load config
        loadConfig()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            val root = ClientCommandManager.literal("streammc")

            // /streammc -> Print help
            root.executes { context ->
                context.source.sendFeedback(Component.literal("§e[StreamMC] Usage: /streammc <start|stop|config|pollingrate|event>"))
                1
            }

            // /streammc config -> Open GUI
            root.then(ClientCommandManager.literal("config").executes { context ->
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(StreamConfigScreen())
                }
                1
            })

            // /streammc start -> Start polling with cached config
            root.then(ClientCommandManager.literal("start").executes { context ->
                if (apiKey.isNullOrBlank() || videoId.isNullOrBlank()) {
                    context.source.sendFeedback(Component.literal("§c[StreamMC] No configuration found. Please run /streammc config to set API Key and Video ID."))
                } else {
                    startPolling(apiKey!!, videoId!!)
                }
                1
            })

            // /streammc stop -> Stop polling
            root.then(ClientCommandManager.literal("stop").executes { context ->
                stopPolling()
                1
            })

            // /streammc pollingrate <seconds>
            root.then(ClientCommandManager.literal("pollingrate")
                .then(ClientCommandManager.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes { context ->
                        if (poller != null) {
                            context.source.sendFeedback(Component.literal("§c[StreamMC] Cannot change polling rate while polling is active. Stop polling first."))
                            return@executes 0
                        }
                        
                        val seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds")
                        pollingIntervalMs = seconds * 1000L
                        saveConfig()
                        
                        if (seconds < 5) {
                            context.source.sendFeedback(Component.literal("§e[StreamMC] Warning: Polling interval < 5s may exceed YouTube API quota."))
                        }
                        
                        context.source.sendFeedback(Component.literal("§a[StreamMC] Polling interval set to ${seconds}s."))
                        1
                    }
                )
            )

            // /streammc event ...
            val eventNode = ClientCommandManager.literal("event")
            
            // register <type> <function>
            eventNode.then(ClientCommandManager.literal("register")
                .then(ClientCommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests { _, builder -> 
                        EventType.values().forEach { builder.suggest(it.name.lowercase()) }
                        builder.buildFuture()
                    }
                    .then(ClientCommandManager.argument("function", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes { context ->
                            val typeStr = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "type").uppercase()
                            val funcName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "function")
                            
                            try {
                                val type = EventType.valueOf(typeStr)
                                eventMap[type] = funcName
                                saveConfig()
                                context.source.sendFeedback(Component.literal("§a[StreamMC] Registered event '$type' to function '$funcName'"))
                            } catch (e: IllegalArgumentException) {
                                context.source.sendFeedback(Component.literal("§c[StreamMC] Invalid event type. Valid types: ${EventType.values().joinToString(", ")}"))
                            }
                            1
                        }
                    )
                )
            )
            
            // list
            eventNode.then(ClientCommandManager.literal("list").executes { context ->
                if (eventMap.isEmpty()) {
                     context.source.sendFeedback(Component.literal("§e[StreamMC] No events registered."))
                } else {
                    context.source.sendFeedback(Component.literal("§a[StreamMC] Registered Events:"))
                    eventMap.forEach { (type, func) ->
                        context.source.sendFeedback(Component.literal(" - $type -> $func"))
                    }
                }
                1
            })
            
            // clear <type>
            eventNode.then(ClientCommandManager.literal("clear")
                 .then(ClientCommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests { _, builder -> 
                        EventType.values().forEach { builder.suggest(it.name.lowercase()) }
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val typeStr = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "type").uppercase()
                         try {
                                val type = EventType.valueOf(typeStr)
                                if (eventMap.remove(type) != null) {
                                    saveConfig()
                                    context.source.sendFeedback(Component.literal("§a[StreamMC] Cleared event '$type'"))
                                } else {
                                    context.source.sendFeedback(Component.literal("§e[StreamMC] No registration found for '$type'"))
                                }
                            } catch (e: IllegalArgumentException) {
                                context.source.sendFeedback(Component.literal("§c[StreamMC] Invalid event type."))
                            }
                        1
                    }
                 )
            )

            root.then(eventNode)
            dispatcher.register(root)
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            val poller = poller ?: return@register
            
            // 1. Process new batches
            while (poller.messageQueue.isNotEmpty()) {
                val batch = poller.messageQueue.poll() ?: break
                if (batch.items.isEmpty()) continue

                val maxTime = batch.items.maxOfOrNull { 
                    try { Instant.parse(it.snippet.publishedAt).toEpochMilli() } catch (e: Exception) { 0L } 
                } ?: 0L
                
                if (maxTime == 0L) continue 

                val baseTime = batch.receivedAt + batch.pollingInterval
                
                for (msg in batch.items) {
                    val publishedMsg = try { Instant.parse(msg.snippet.publishedAt).toEpochMilli() } catch (e: Exception) { maxTime }
                    val delta = publishedMsg - maxTime
                    val displayAt = baseTime + delta
                    
                    displayQueue.add(QueuedMessage(msg, displayAt))
                }
            }

            // 2. Display messages & Trigger Events
            val now = System.currentTimeMillis()
            while (displayQueue.isNotEmpty() && displayQueue.peek().displayTime <= now) {
                val queued = displayQueue.poll()
                val msg = queued.message
                val rawAuthor = msg.authorDetails.displayName
                val author = if (rawAuthor.startsWith("@")) rawAuthor.substring(1) else rawAuthor
                
                // Super Chat?
                val superChat = msg.snippet.superChatDetails
                val text = msg.snippet.displayMessage ?: msg.snippet.textMessageDetails?.messageText ?: ""
                
                // Display in Chat
                if (showChatInGame) {
                    val formatted = chatFormat
                        .replace("%author%", author)
                        .replace("%message%", text)

                    client.gui.chat.addMessage(Component.literal(formatted))
                }
                
                // Trigger Event based on message type
                when (msg.snippet.type) {
                    "superChatEvent" -> {
                        val superChat = msg.snippet.superChatDetails
                        if (superChat != null) {
                            triggerEvent(EventType.SUPERCHAT, mapOf(
                                "author" to author,
                                "message" to (superChat.userComment ?: ""),
                                "amount" to superChat.amountMicros,
                                "currency" to superChat.currency,
                                "displayAmount" to superChat.amountDisplayString,
                                "tier" to superChat.tier
                            ))
                        }
                    }
                    "superStickerEvent" -> {
                        val superSticker = msg.snippet.superStickerDetails
                        if (superSticker != null) {
                            triggerEvent(EventType.SUPERSTICKER, mapOf(
                                "author" to author,
                                "amount" to superSticker.amountMicros,
                                "currency" to superSticker.currency,
                                "displayAmount" to superSticker.amountDisplayString,
                                "tier" to superSticker.tier,
                                "stickerId" to (superSticker.superStickerMetadata?.stickerId ?: ""),
                                "altText" to (superSticker.superStickerMetadata?.altText ?: "")
                            ))
                        }
                    }
                    "newSponsorEvent" -> {
                        val newSponsor = msg.snippet.newSponsorDetails
                        if (newSponsor != null) {
                            triggerEvent(EventType.NEW_SPONSOR, mapOf(
                                "author" to author,
                                "isUpgrade" to (newSponsor.isUpgrade ?: false),
                                "memberLevel" to (newSponsor.memberLevelName ?: "")
                            ))
                        }
                    }
                    "memberMilestoneChatEvent" -> {
                        val milestone = msg.snippet.memberMilestoneChatDetails
                        if (milestone != null) {
                            triggerEvent(EventType.MEMBER_MILESTONE, mapOf(
                                "author" to author,
                                "message" to (milestone.userComment ?: ""),
                                "memberMonth" to milestone.memberMonth,
                                "memberLevel" to (milestone.memberLevelName ?: "")
                            ))
                        }
                    }
                    "textMessageEvent" -> {
                        // Normal Chat
                        triggerEvent(EventType.CHAT, mapOf(
                            "author" to author,
                            "message" to text
                        ))
                    }
                }
            }
        }
    }
    
    // Helper to trigger data pack function with macro arguments
    private fun triggerEvent(type: EventType, params: Map<String, Any>) {
        val funcName = eventMap[type] ?: return
        
        // Build JSON-like string for NBT
        val nbtBuilder = StringBuilder("{")
        var first = true
        for ((key, value) in params) {
            if (!first) nbtBuilder.append(",")
            first = false
            
            nbtBuilder.append(key).append(":")
            when (value) {
                is String -> nbtBuilder.append("\"").append(escapeString(value)).append("\"")
                else -> nbtBuilder.append(value)
            }
        }
        nbtBuilder.append("}")
        
        val command = "function $funcName $nbtBuilder"
        
        // Send command
        Minecraft.getInstance().player?.connection?.sendCommand(command)
    }
    
    private fun escapeString(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    fun startPolling(key: String, id: String) {
        stopPolling()
        apiKey = key
        videoId = id
        
        poller = ChatPoller(key, id, pollingIntervalMs)
        poller?.start()
        
        saveConfig()
        
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§aStarted polling YouTube Chat with interval ${pollingIntervalMs/1000}s!"))
    }

    fun stopPolling() {
        poller?.stop()
        poller = null
        displayQueue.clear()
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§cStopped polling YouTube Chat."))
    }
    
    private fun loadConfig() {
        val config = ConfigManager.loadConfig()
        videoId = config.videoId
        pollingIntervalMs = config.pollingIntervalMs
        chatFormat = config.chatFormat
        showChatInGame = config.showChatInGame
        eventMap.clear()
        eventMap.putAll(ConfigManager.stringMapToEventMap(config.eventMappings))
    }
    
    fun saveConfig() {
        val config = StreamMCConfig(
            videoId = videoId,
            pollingIntervalMs = pollingIntervalMs,
            chatFormat = chatFormat,
            showChatInGame = showChatInGame,
            eventMappings = ConfigManager.eventMapToStringMap(eventMap)
        )
        ConfigManager.saveConfig(config)
    }
}
