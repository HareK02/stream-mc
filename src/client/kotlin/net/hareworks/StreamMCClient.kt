package net.hareworks

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
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

object StreamMCClient : ClientModInitializer {
    var poller: ChatPoller? = null
    var apiKey: String? = null
    var videoId: String? = null
    var pollingIntervalMs: Long = 5000L
    var chatFormat: String = "§e%author%: §f%message%"
    
    private val displayQueue = PriorityQueue<QueuedMessage>()

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            val root = ClientCommandManager.literal("streammc")

            // /streammc -> Print help
            root.executes { context ->
                context.source.sendFeedback(Component.literal("§e[StreamMC] Usage: /streammc <start|stop|config|pollingrate>"))
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
                    context.source.sendFeedback(Component.literal("§c[StreamMC] No configuration found. Please run /streammc to set API Key and Video ID."))
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
                        
                        if (seconds < 5) {
                            context.source.sendFeedback(Component.literal("§e[StreamMC] Warning: Polling interval < 5s may exceed YouTube API quota."))
                        }
                        
                        context.source.sendFeedback(Component.literal("§a[StreamMC] Polling interval set to ${seconds}s."))
                        1
                    }
                )
            )

            dispatcher.register(root)
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            val poller = poller ?: return@register
            
            // 1. Process new batches from Poller
            while (poller.messageQueue.isNotEmpty()) {
                val batch = poller.messageQueue.poll() ?: break
                if (batch.items.isEmpty()) continue
                
                // Calculate display times
                // We want to flow messages over the NEXT polling interval.
                // Strategy: 
                // Find max(publishedAt) in batch.
                // BaseTime = valid received time + pollingInterval (delay by 1 interval)
                // DisplayTime(msg) = BaseTime + (publishedAt(msg) - maxPublishedAt)
                // This ensures the LAST message in the batch is shown exactly at (Arrival + Interval).
                // And previous messages are shown temporally correctly relative to it.
                
                val maxTime = batch.items.maxOfOrNull { 
                    try { Instant.parse(it.snippet.publishedAt).toEpochMilli() } catch (e: Exception) { 0L } 
                } ?: 0L
                
                if (maxTime == 0L) continue // Should not happen if items not empty

                val baseTime = batch.receivedAt + batch.pollingInterval
                
                for (msg in batch.items) {
                    val publishedMsg = try { Instant.parse(msg.snippet.publishedAt).toEpochMilli() } catch (e: Exception) { maxTime }
                    val delta = publishedMsg - maxTime
                    val displayAt = baseTime + delta
                    
                    displayQueue.add(QueuedMessage(msg, displayAt))
                }
            }

            // 2. Display messages whose time has come
            val now = System.currentTimeMillis()
            while (displayQueue.isNotEmpty() && displayQueue.peek().displayTime <= now) {
                val queued = displayQueue.poll()
                val msg = queued.message
                val rawAuthor = msg.authorDetails.displayName
                val author = if (rawAuthor.startsWith("@")) rawAuthor.substring(1) else rawAuthor
                val text = msg.snippet.displayMessage ?: msg.snippet.textMessageDetails?.messageText ?: ""
                
                val formatted = chatFormat
                    .replace("%author%", author)
                    .replace("%message%", text)
                
               client.gui.chat.addMessage(Component.literal(formatted))
            }
        }
    }

    fun startPolling(key: String, id: String) {
        stopPolling()
        apiKey = key
        videoId = id
        
        poller = ChatPoller(key, id, pollingIntervalMs)
        poller?.start()
        
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§aStarted polling YouTube Chat with interval ${pollingIntervalMs/1000}s!"))
    }

    fun stopPolling() {
        poller?.stop()
        poller = null
        displayQueue.clear()
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§cStopped polling YouTube Chat."))
    }
}
