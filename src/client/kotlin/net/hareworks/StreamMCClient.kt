package net.hareworks

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.hareworks.screen.StreamConfigScreen
import net.hareworks.youtube.ChatPoller
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object StreamMCClient : ClientModInitializer {
    var poller: ChatPoller? = null
    var apiKey: String? = null
    var videoId: String? = null

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
	        dispatcher.register(ClientCommandManager.literal("streammc")
		        .executes { context ->
                    // Make sure to run on client thread
			        Minecraft.getInstance().execute {
				        Minecraft.getInstance().setScreen(StreamConfigScreen())
			        }
			        1
		        })
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            val poller = poller ?: return@register
            
            // Check if queue has items to avoid locking if not needed (though ConcurrentLinkedQueue is safe)
            if (poller.messageQueue.isEmpty()) return@register

            while (poller.messageQueue.isNotEmpty()) {
                val msg = poller.messageQueue.poll() ?: break
                val author = msg.authorDetails.displayName
                val text = msg.snippet.displayMessage ?: msg.snippet.textMessageDetails?.messageText ?: ""
                
                // client is Minecraft (Fabric mapping) or Minecraft (Mojang mapping passed as generic?)
                // Fabric's ClientTickEvents passes MinecraftClient (Yarn) or Minecraft (Mojang)
                // In Mojmap environment it should be Minecraft.
                
                client.gui.chat.addMessage(Component.literal("§6[YT] §e$author: §f$text"))
            }
        }
    }

    fun startPolling(key: String, id: String) {
        stopPolling()
        apiKey = key
        videoId = id
        
        poller = ChatPoller(key, id)
        poller?.start()
        
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§aStarted polling YouTube Chat!"))
    }

    fun stopPolling() {
        poller?.stop()
        poller = null
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("§cStopped polling YouTube Chat."))
    }
}
