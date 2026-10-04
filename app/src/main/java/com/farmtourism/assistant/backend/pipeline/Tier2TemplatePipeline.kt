package com.farmtourism.assistant.backend.pipeline

import com.farmtourism.assistant.backend.database.IFarmDatabase
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.ConversationTurn
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.IntentTemplate
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier

data class Tier2PromptRequest(
    val touristOriginalText: String,
    val touristLanguage: String,
    val intent: String,
    val slotKey: String,
    val promptForNoor: String,
    val defaultReplyForNoor: String = "",
    val template: IntentTemplate
)

data class Tier2CompletedResult(
    val touristOriginalText: String,
    val touristLanguage: String,
    val intent: String,
    val slotKey: String,
    val enteredValue: String,
    val englishReply: String,
    val responseInTouristLanguage: String,
    val latencyMs: Long,
    val conversationTurn: ConversationTurn
)

/**
 * Tier 2: Template-Guided Human-in-the-Loop Pipeline.
 * Triggered when intent classification confidence >= tau, but the target field is missing from the local database.
 * Prompts Noor in her configured language (e.g. Hindi) with a single fill-in-the-blank question,
 * caches the entered atomic value to the local database, slot-fills the template, and translates to the tourist.
 */
class Tier2TemplatePipeline(
    private val translationEngine: ITranslationEngine,
    private val database: IFarmDatabase,
    private val farmerProfile: FarmerProfile = FarmerProfile()
) {

    /**
     * Generates the slotted question to prompt Noor in her native language.
     */
    fun createPromptRequest(
        touristMessage: String,
        touristLanguage: String,
        intent: String,
        template: IntentTemplate
    ): Tier2PromptRequest {
        val noorPrompt = template.getNoorPrompt(farmerProfile.language)
        val defaultReply = template.getSampleSlotValue(farmerProfile.language)
        return Tier2PromptRequest(
            touristOriginalText = touristMessage,
            touristLanguage = touristLanguage,
            intent = intent,
            slotKey = template.slotKey,
            promptForNoor = noorPrompt,
            defaultReplyForNoor = defaultReply,
            template = template
        )
    }

    /**
     * Completes Tier 2 once Noor inputs the missing atomic value.
     * Caches value into the database for future Tier 1 hits, populates template, and translates.
     */
    suspend fun completeWithFarmerInput(
        promptRequest: Tier2PromptRequest,
        farmerValueInput: String
    ): Result<Tier2CompletedResult> = runCatching {
        val startTime = System.currentTimeMillis()
        val cleanValue = farmerValueInput.trim()

        // 1. Cache atomic value into local database for future instant hits (persisting Noor's Hindi entry)
        database.setSlot(promptRequest.slotKey, cleanValue)

        // 2. If entered in Hindi (Devanagari), translate the database entry to English first for template filling
        val isDevanagari = cleanValue.any { it in '\u0900'..'\u097F' }
        val englishSlotValue = if (isDevanagari) {
            val trans = translationEngine.translate(
                text = cleanValue,
                sourceLanguage = "hi",
                targetLanguage = "en"
            ).getOrThrow().translatedText
            if (trans.isNotBlank()) trans else cleanValue
        } else {
            cleanValue
        }

        // 3. Populate English template
        val englishReply = promptRequest.template.fillTemplate(englishSlotValue)

        // 4. Translate to tourist's language on-device
        val translationResult = if (promptRequest.touristLanguage == "en") {
            englishReply
        } else if (promptRequest.touristLanguage == "hi" && isDevanagari) {
            promptRequest.template.fillTemplate(cleanValue, languageCode = "hi")
        } else {
            translationEngine.translate(
                text = englishReply,
                sourceLanguage = "en",
                targetLanguage = promptRequest.touristLanguage
            ).getOrThrow().translatedText
        }

        val totalLatency = System.currentTimeMillis() - startTime

        val turn = ConversationTurn(
            sender = MessageSender.TOURIST,
            originalText = promptRequest.touristOriginalText,
            originalLanguage = promptRequest.touristLanguage,
            translatedText = translationResult,
            translatedLanguage = promptRequest.touristLanguage,
            tier = PipelineTier.TIER_2_TEMPLATE_PROMPT,
            latencyMs = totalLatency
        )

        Tier2CompletedResult(
            touristOriginalText = promptRequest.touristOriginalText,
            touristLanguage = promptRequest.touristLanguage,
            intent = promptRequest.intent,
            slotKey = promptRequest.slotKey,
            enteredValue = cleanValue,
            englishReply = englishReply,
            responseInTouristLanguage = translationResult,
            latencyMs = totalLatency,
            conversationTurn = turn
        )
    }
}
