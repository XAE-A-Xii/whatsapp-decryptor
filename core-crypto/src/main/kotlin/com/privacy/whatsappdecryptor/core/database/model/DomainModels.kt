package com.privacy.whatsappdecryptor.core.database.model

import kotlinx.serialization.Serializable

@Serializable
enum class WhatsAppMessageType(val rawCode: Int?, val label: String) {
    TEXT(0, "Text"),
    IMAGE(1, "Photo"),
    AUDIO(2, "Voice Note / Audio"),
    VIDEO(3, "Video"),
    CONTACT(4, "Contact Card"),
    LOCATION(5, "Location"),
    SYSTEM(7, "System Notification"),
    DOCUMENT(9, "Document"),
    MISSED_CALL(10, "Missed Call"),
    STICKER(15, "Sticker"),
    GROUP_INVITE(20, "Group Invite"),
    POLL(64, "Poll"),
    REACTION(99, "Reaction"),
    COMMUNITY_ACTION(103, "Community Announcement"),
    UNKNOWN(null, "Attachment");

    companion object {
        fun fromCode(code: Int?): WhatsAppMessageType {
            if (code == null) return UNKNOWN
            return entries.firstOrNull { it.rawCode == code } ?: UNKNOWN
        }
    }
}

@Serializable
data class ContactIdentifier(
    val rawJid: String,
    val phoneNumber: String? = null,
    val pushName: String? = null,
    val resolvedDisplayName: String? = null
) {
    val displayLabel: String
        get() = when {
            resolvedDisplayName != null -> resolvedDisplayName
            pushName != null && phoneNumber != null -> "$pushName ($phoneNumber)"
            pushName != null -> pushName
            phoneNumber != null -> phoneNumber
            else -> rawJid
        }

    companion object {
        fun fromJid(rawJid: String, pushName: String? = null): ContactIdentifier {
            val user = rawJid.substringBefore("@")
            val isPhoneNumber = user.all { it.isDigit() } && user.length >= 7
            val formattedPhone = if (isPhoneNumber) "+$user" else null

            return ContactIdentifier(
                rawJid = rawJid,
                phoneNumber = formattedPhone,
                pushName = pushName
            )
        }
    }
}

@Serializable
data class ChatSummary(
    val id: Long,
    val rawJid: String,
    val title: String,
    val isGroup: Boolean,
    val messageCount: Long = 0,
    val lastMessageTimestampMs: Long? = null,
    val isArchived: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: Long,
    val messageKeyId: String,
    val timestampMs: Long,
    val sender: ContactIdentifier,
    val isFromMe: Boolean,
    val text: String?,
    val messageType: WhatsAppMessageType,
    val metadataSummary: String? = null
)
