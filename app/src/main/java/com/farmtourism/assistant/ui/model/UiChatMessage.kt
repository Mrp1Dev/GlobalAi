package com.farmtourism.assistant.ui.model

import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier
import java.util.UUID

/**
 * Clean dual-perspective chat message model:
 * Contains the presentation text for both the foreign tourist and host farmer Noor.
 */
data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender, // TOURIST or FARMER
    val touristText: String,   // What the tourist sees (in tourist's language)
    val noorText: String,      // What Noor sees (in Hindi / local tongue)
    val touristLanguage: String = "en",
    val timestamp: Long = System.currentTimeMillis(),
    val tier: PipelineTier = PipelineTier.TIER_1_FAST_DB,
    val isAutoReply: Boolean = false,
    val latencyMs: Long = 0L
)
