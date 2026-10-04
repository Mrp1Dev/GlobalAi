package com.farmtourism.assistant.backend.pipeline

import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.ConversationTurn
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.IntentTemplate
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier

data class Tier1FastPathResult(
    val touristOriginalText: String,
    val touristLanguage: String,
    val intent: String,
    val slotKey: String,
    val slotValue: String,
    val englishReply: String,
    val responseInTouristLanguage: String,
    val latencyMs: Long,
    val conversationTurn: ConversationTurn
)

/**
 * Tier 1: Automated Fast-Path Pipeline.
 * Triggered when intent classification confidence >= tau and target slot value exists in the local database.
 * Completely automated: populates base template and translates directly to tourist with 0 effort from Noor.
 */
class Tier1FastPathPipeline(
    private val translationEngine: ITranslationEngine,
    private val farmerProfile: FarmerProfile = FarmerProfile()
) {

    suspend fun execute(
        touristMessage: String,
        touristLanguage: String,
        intent: String,
        slotValue: String,
        template: IntentTemplate
    ): Result<Tier1FastPathResult> = runCatching {
        val startTime = System.currentTimeMillis()

        // 1. If database slot was saved in Hindi (Devanagari), translate to English first for template filling
        val isDevanagari = slotValue.any { it in '\u0900'..'\u097F' }
        val englishSlotValue = if (isDevanagari) {
            val trans = translationEngine.translate(
                text = slotValue,
                sourceLanguage = "hi",
                targetLanguage = "en"
            ).getOrThrow().translatedText
            if (trans.isNotBlank()) trans else slotValue
        } else {
            slotValue
        }

        // 2. Slot fill database value into English template
        val englishReply = template.fillTemplate(englishSlotValue)

        // 3. On-device translation to tourist's native language
        val translationResult = if (touristLanguage == "en") {
            englishReply
        } else if (touristLanguage == "hi" && isDevanagari) {
            template.fillTemplate(slotValue, languageCode = "hi")
        } else {
            translationEngine.translate(
                text = englishReply,
                sourceLanguage = "en",
                targetLanguage = touristLanguage
            ).getOrThrow().translatedText
        }

        val totalLatency = System.currentTimeMillis() - startTime

        val turn = ConversationTurn(
            sender = MessageSender.TOURIST,
            originalText = touristMessage,
            originalLanguage = touristLanguage,
            translatedText = translationResult,
            translatedLanguage = touristLanguage,
            tier = PipelineTier.TIER_1_FAST_DB,
            latencyMs = totalLatency
        )

        Tier1FastPathResult(
            touristOriginalText = touristMessage,
            touristLanguage = touristLanguage,
            intent = intent,
            slotKey = template.slotKey,
            slotValue = slotValue,
            englishReply = englishReply,
            responseInTouristLanguage = translationResult,
            latencyMs = totalLatency,
            conversationTurn = turn
        )
    }
}
