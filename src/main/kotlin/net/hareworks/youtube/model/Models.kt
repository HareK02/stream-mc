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
    val publishedAt: String,
    val displayMessage: String? = null,
    val textMessageDetails: TextMessageDetails? = null,
    val superChatDetails: SuperChatDetails? = null,
    val superStickerDetails: SuperStickerDetails? = null,
    val newSponsorDetails: NewSponsorDetails? = null,
    val memberMilestoneChatDetails: MemberMilestoneChatDetails? = null,
    val membershipGiftingDetails: MembershipGiftingDetails? = null,
    val giftMembershipReceivedDetails: GiftMembershipReceivedDetails? = null
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
data class SuperStickerDetails(
    val amountMicros: Long,
    val currency: String,
    val amountDisplayString: String,
    val tier: Int,
    val superStickerMetadata: SuperStickerMetadata? = null
)

@Serializable
data class SuperStickerMetadata(
    val stickerId: String? = null,
    val altText: String? = null
)

@Serializable
data class NewSponsorDetails(
    val isUpgrade: Boolean? = null,
    val memberLevelName: String? = null
)

@Serializable
data class MemberMilestoneChatDetails(
    val memberMonth: Int,
    val memberLevelName: String? = null,
    val userComment: String? = null
)

@Serializable
data class AuthorDetails(
    val channelId: String,
    val displayName: String,
    val isChatOwner: Boolean = false,
    val isChatSponsor: Boolean = false,
    val isChatModerator: Boolean = false
)

@Serializable
data class MembershipGiftingDetails(
    val giftMembershipsCount: Int,
    val memberLevelName: String? = null
)

@Serializable
data class GiftMembershipReceivedDetails(
    val memberLevelName: String? = null,
    val gifterChannelId: String? = null,
    val gifterMemberLevelName: String? = null
)
