package com.farmtourism.assistant.backend.model

import java.util.UUID

enum class MessageSender {
    TOURIST,
    FARMER
}

enum class PipelineTier {
    TIER_1_FAST_DB,
    TIER_2_TEMPLATE_PROMPT,
    TIER_3_DIRECT_TRANSLATION_FALLBACK
}

/**
 * Message exchange record in the farm assistant conversation history.
 */
data class ConversationTurn(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val originalText: String,
    val originalLanguage: String,
    val translatedText: String,
    val translatedLanguage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tier: PipelineTier = PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK,
    val latencyMs: Long = 0L
)
