package net.hareworks.youtube

import kotlinx.serialization.json.Json
import net.hareworks.youtube.model.LiveChatMessageListResponse
import net.hareworks.youtube.model.VideoListResponse
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class YouTubeClient {
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger("stream-mc-client")

    fun getLiveChatId(videoId: String, apiKey: String): String? {
        val url = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id=$videoId&key=$apiKey"
        
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                logger.error("Failed to get video details. Status: ${response.statusCode()}, Body: ${response.body()}")
                return null
            }

            val videoListResponse = json.decodeFromString<VideoListResponse>(response.body())
            val liveChatId = videoListResponse.items.firstOrNull()?.liveStreamingDetails?.activeLiveChatId
            
            if (liveChatId == null) {
                logger.warn("No active live chat found for video ID: $videoId")
            }
            
            return liveChatId

        } catch (e: Exception) {
            logger.error("Error fetching live chat ID", e)
            return null
        }
    }

    fun getChatMessages(liveChatId: String, apiKey: String, pageToken: String? = null): LiveChatMessageListResponse? {
        val baseUrl = "https://www.googleapis.com/youtube/v3/liveChat/messages?liveChatId=$liveChatId&part=snippet,authorDetails&key=$apiKey"
        val url = if (pageToken != null) "$baseUrl&pageToken=$pageToken" else baseUrl

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                logger.error("Failed to get chat messages. Status: ${response.statusCode()}, Body: ${response.body()}")
                return null
            }

            return json.decodeFromString<LiveChatMessageListResponse>(response.body())

        } catch (e: Exception) {
            logger.error("Error fetching chat messages", e)
            return null
        }
    }
}
