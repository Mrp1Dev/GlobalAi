package com.farmtourism.assistant.backend.pipeline

import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.ConversationTurn
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier
import java.util.Collections

/**
 * Result of processing a tourist message through Tier 3 Direct Fallback.
 */
data class TouristTurnResult(
    val touristOriginalText: String,
    val touristLanguage: String,
    val farmerTranslatedText: String,
    val farmerLanguage: String,
    val latencyMs: Long,
    val conversationTurn: ConversationTurn
)

/**
 * Result of processing a farmer's response through Tier 3 Direct Fallback.
 */
data class FarmerTurnResult(
    val farmerOriginalText: String,
    val farmerLanguage: String,
    val touristTranslatedText: String,
    val touristLanguage: String,
    val latencyMs: Long,
    val conversationTurn: ConversationTurn
)

/**
 * Tier 3: Direct Two-Way Translation Fallback Pipeline.
 *
 * Implements the baseline / worst-case conversational pipeline:
 * 1. Tourist sends natural language query in foreign tongue (Spanish, French, German, etc.).
 * 2. Language is automatically identified on-device in <5ms.
 * 3. Message is translated directly into host farmer's language (e.g. Noor's Hindi) on-device.
 * 4. Host farmer inputs response freely in her native tongue.
 * 5. Host farmer's response is translated directly back into tourist's native language on-device.
 */
class Tier3FallbackPipeline(
    private val translationEngine: ITranslationEngine,
    private val languageIdentifier: ILanguageIdentifier,
    val farmerProfile: FarmerProfile = FarmerProfile(),
    private val defaultTouristLanguage: String = "en"
) {

    private val conversationHistory = Collections.synchronizedList(mutableListOf<ConversationTurn>())

    /**
     * Step 1: Ingest and translate tourist message to Noor's native language.
     *
     * @param rawText Natural language message typed/spoken by the tourist.
     * @param forcedTouristLanguage Optional language code if tourist explicitly selected their language.
     */
    suspend fun processTouristMessage(
        rawText: String,
        forcedTouristLanguage: String? = null
    ): Result<TouristTurnResult> {
        return runCatching {
            val startTime = System.currentTimeMillis()

            // 1. Identify tourist language if not explicitly provided
            val touristLang = if (!forcedTouristLanguage.isNullOrBlank()) {
                forcedTouristLanguage
            } else {
                val detectionResult = languageIdentifier.identifyLanguage(rawText).getOrNull()
                if (detectionResult != null && detectionResult.isReliable) {
                    detectionResult.languageCode
                } else {
                    defaultTouristLanguage
                }
            }

            // 2. Translate tourist query into Noor's configured language (e.g. Hindi "hi")
            val translation = translationEngine.translate(
                text = rawText,
                sourceLanguage = touristLang,
                targetLanguage = farmerProfile.language
            ).getOrThrow()

            val totalLatency = System.currentTimeMillis() - startTime

            val turn = ConversationTurn(
                sender = MessageSender.TOURIST,
                originalText = rawText,
                originalLanguage = touristLang,
                translatedText = translation.translatedText,
                translatedLanguage = farmerProfile.language,
                tier = PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK,
                latencyMs = totalLatency
            )
            conversationHistory.add(turn)

            TouristTurnResult(
                touristOriginalText = rawText,
                touristLanguage = touristLang,
                farmerTranslatedText = translation.translatedText,
                farmerLanguage = farmerProfile.language,
                latencyMs = totalLatency,
                conversationTurn = turn
            )
        }
    }

    /**
     * Step 2: Translate Noor's native language response back to tourist's language.
     *
     * @param farmerReplyText Free-form response entered by Noor in her native language (e.g. Hindi).
     * @param targetTouristLanguage BCP-47 tag for the tourist's language.
     */
    suspend fun processFarmerReply(
        farmerReplyText: String,
        targetTouristLanguage: String
    ): Result<FarmerTurnResult> {
        return runCatching {
            val startTime = System.currentTimeMillis()

            // Translate Noor's reply back to tourist's language
            val translation = translationEngine.translate(
                text = farmerReplyText,
                sourceLanguage = farmerProfile.language,
                targetLanguage = targetTouristLanguage
            ).getOrThrow()

            val totalLatency = System.currentTimeMillis() - startTime

            val turn = ConversationTurn(
                sender = MessageSender.FARMER,
                originalText = farmerReplyText,
                originalLanguage = farmerProfile.language,
                translatedText = translation.translatedText,
                translatedLanguage = targetTouristLanguage,
                tier = PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK,
                latencyMs = totalLatency
            )
            conversationHistory.add(turn)

            FarmerTurnResult(
                farmerOriginalText = farmerReplyText,
                farmerLanguage = farmerProfile.language,
                touristTranslatedText = translation.translatedText,
                touristLanguage = targetTouristLanguage,
                latencyMs = totalLatency,
                conversationTurn = turn
            )
        }
    }

    fun getHistory(): List<ConversationTurn> = conversationHistory.toList()

    fun clearHistory() {
        conversationHistory.clear()
    }
}
