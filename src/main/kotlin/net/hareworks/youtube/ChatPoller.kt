package net.hareworks.youtube

import kotlinx.coroutines.*
import net.hareworks.youtube.model.LiveChatMessage
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class ChatPoller(private val apiKey: String, private val videoId: String) {
    private val client = YouTubeClient()
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logger = LoggerFactory.getLogger("stream-mc-poller")
    private var isRunning = false
    
    // Simple queue for now to expose messages
    val messageQueue = ConcurrentLinkedQueue<LiveChatMessage>()

    fun start() {
        if (isRunning) return
        isRunning = true
        
        job = scope.launch {
            logger.info("Starting ChatPoller for video: $videoId")
            val liveChatId = client.getLiveChatId(videoId, apiKey)
            
            if (liveChatId == null) {
                logger.error("Could not find liveChatId. Stopping poller.")
                isRunning = false
                return@launch
            }

            logger.info("Found liveChatId: $liveChatId")
            
            var nextPageToken: String? = null
            var pollingInterval = 5000L // Default 5s

            while (isActive && isRunning) {
                try {
                    val response = client.getChatMessages(liveChatId, apiKey, nextPageToken)
                    
                    if (response != null) {
                        // Update paging and interval
                        nextPageToken = response.nextPageToken
                        pollingInterval = response.pollingIntervalMillis ?: pollingInterval
                        
                        // Process messages
                         if (response.items.isNotEmpty()) {
                             logger.info("Fetched ${response.items.size} messages")
                             messageQueue.addAll(response.items)
                         }

                    } else {
                        logger.warn("Failed to fetch messages. Retrying in default interval.")
                    }

                    delay(pollingInterval)

                } catch (e: Exception) {
                    logger.error("Error in polling loop", e)
                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        logger.info("Stopped ChatPoller")
    }
}
