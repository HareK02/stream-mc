package net.hareworks.youtube.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoListResponse(
    val items: List<VideoItem>
)

@Serializable
data class VideoItem(
    val liveStreamingDetails: LiveStreamingDetails? = null
)

@Serializable
data class LiveStreamingDetails(
    val activeLiveChatId: String? = null
)

@Serializable
data class LiveChatMessageListResponse(
    val items: List<LiveChatMessage>,
    val pollingIntervalMillis: Long? = null,
    val nextPageToken: String? = null
)

@Serializable
data class LiveChatMessage(
    val id: String,
    val snippet: Snippet,
    val authorDetails: AuthorDetails
)

@Serializable
data class Snippet(
    val type: String,
    val displayMessage: String? = null,
    val textMessageDetails: TextMessageDetails? = null,
    val superChatDetails: SuperChatDetails? = null
)

@Serializable
data class TextMessageDetails(
    val messageText: String
)

@Serializable
data class SuperChatDetails(
    val amountMicros: Long,
    val currency: String,
    val amountDisplayString: String,
    val userComment: String? = null,
    val tier: Int
)

@Serializable
data class AuthorDetails(
    val channelId: String,
    val displayName: String,
    val isChatOwner: Boolean = false,
    val isChatSponsor: Boolean = false,
    val isChatModerator: Boolean = false
)
