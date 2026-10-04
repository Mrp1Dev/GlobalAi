package com.farmtourism.assistant.backend

import com.farmtourism.assistant.backend.classifier.OnnxIntentClassifier
import com.farmtourism.assistant.backend.database.IFarmDatabase
import com.farmtourism.assistant.backend.database.LocalFarmDatabase
import com.farmtourism.assistant.backend.engine.IIntentClassifier
import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.mlkit.MlKitLanguageIdentifier
import com.farmtourism.assistant.backend.mlkit.MlKitTranslationEngine
import com.farmtourism.assistant.backend.model.ConversationTurn
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.UnifiedTouristResult
import com.farmtourism.assistant.backend.pipeline.FarmerTurnResult
import com.farmtourism.assistant.backend.pipeline.Tier1FastPathPipeline
import com.farmtourism.assistant.backend.pipeline.Tier2CompletedResult
import com.farmtourism.assistant.backend.pipeline.Tier2PromptRequest
import com.farmtourism.assistant.backend.pipeline.Tier2TemplatePipeline
import com.farmtourism.assistant.backend.pipeline.Tier3FallbackPipeline
import com.farmtourism.assistant.backend.pipeline.TouristTurnResult
import com.farmtourism.assistant.backend.review.ReviewAnalyzerEngine
import com.farmtourism.assistant.backend.review.classifier.IReviewClassifier
import com.farmtourism.assistant.backend.review.classifier.ReviewAspectClassifier
import com.farmtourism.assistant.backend.review.model.ReviewAnalysisResult
import java.util.Collections

/**
 * Main Android Backend Engine for the Edge-Native Farm Tourism Assistant.
 *
 * Implements the full 3-tier offline conversational architecture:
 * 1. Tier 1: Sub-100ms Automated Fast-Path via local database hit.
 * 2. Tier 2: Template-Guided Human-in-the-Loop when database slot is missing.
 * 3. Tier 3: Direct Two-Way On-Device Translation Fallback for open-ended queries.
 * Plus on-device multilingual review understanding and suggestion generation for Noor.
 */
class FarmAssistantBackend(
    val farmerProfile: FarmerProfile = FarmerProfile(),
    private val translationEngine: ITranslationEngine = MlKitTranslationEngine(),
    private val languageIdentifier: ILanguageIdentifier = MlKitLanguageIdentifier(),
    private val intentClassifier: IIntentClassifier = OnnxIntentClassifier(),
    private val database: IFarmDatabase = LocalFarmDatabase(),
    private val reviewClassifier: IReviewClassifier = ReviewAspectClassifier(),
    private val defaultTouristLanguage: String = "en"
) {

    val tier1Pipeline = Tier1FastPathPipeline(
        translationEngine = translationEngine,
        farmerProfile = farmerProfile
    )

    val tier2Pipeline = Tier2TemplatePipeline(
        translationEngine = translationEngine,
        database = database,
        farmerProfile = farmerProfile
    )

    val tier3Pipeline = Tier3FallbackPipeline(
        translationEngine = translationEngine,
        languageIdentifier = languageIdentifier,
        farmerProfile = farmerProfile,
        defaultTouristLanguage = defaultTouristLanguage
    )

    val reviewAnalyzerEngine = ReviewAnalyzerEngine(
        languageIdentifier = languageIdentifier,
        translationEngine = translationEngine,
        reviewClassifier = reviewClassifier,
        defaultFarmerLanguage = farmerProfile.language
    )

    private val conversationHistory = Collections.synchronizedList(mutableListOf<ConversationTurn>())

    /**
     * Initializes the backend by ensuring the host farmer's native tongue model
     * (e.g., Hindi) is downloaded and ready for on-device inference.
     */
    suspend fun initialize(): Result<Unit> = runCatching {
        val farmerLang = farmerProfile.language
        if (!translationEngine.isModelDownloaded(farmerLang)) {
            translationEngine.downloadModel(farmerLang).getOrThrow()
        }
        Unit
    }

    /**
     * Unified entry point for incoming natural language tourist queries.
     * Evaluates intents and database state to route dynamically across Tier 1, 2, or 3.
     *
     * @param message Natural language inquiry typed or spoken by the tourist.
     * @param forcedTouristLanguage Optional language code if tourist explicitly picked a language.
     * @return [UnifiedTouristResult] indicating Tier 1 hit, Tier 2 prompt needed, or Tier 3 fallback.
     */
    suspend fun processTouristMessage(
        message: String,
        forcedTouristLanguage: String? = null
    ): Result<UnifiedTouristResult> = runCatching {
        val cleanMessage = message.trim()

        // 1. On-device Language Identification (<5ms)
        val touristLang = if (!forcedTouristLanguage.isNullOrBlank()) {
            forcedTouristLanguage
        } else {
            val detectionResult = languageIdentifier.identifyLanguage(cleanMessage).getOrNull()
            if (detectionResult != null && detectionResult.isReliable) {
                detectionResult.languageCode
            } else {
                defaultTouristLanguage
            }
        }

        // 2. Multilingual Intent Classification via on-device mmBERT
        val classification = intentClassifier.classify(cleanMessage).getOrThrow()

        // 3. Routing Logic: Check confidence and intent validity
        if (classification.isConfident && classification.intent != "out_of_scope") {
            val template = database.getTemplate(classification.intent)

            if (template != null) {
                // Check if target slot value is already cached in local database
                val existingSlotValue = database.getSlot(template.slotKey)

                if (!existingSlotValue.isNullOrBlank()) {
                    // TIER 1 HIT: Instant automated reply directly from database
                    val fastPathResult = tier1Pipeline.execute(
                        touristMessage = cleanMessage,
                        touristLanguage = touristLang,
                        intent = classification.intent,
                        slotValue = existingSlotValue,
                        template = template
                    ).getOrThrow()

                    conversationHistory.add(fastPathResult.conversationTurn)
                    return@runCatching UnifiedTouristResult.Tier1Hit(fastPathResult)
                } else {
                    // TIER 2 HIT: Missing slot in database -> prompt Noor in her native tongue
                    val promptRequest = tier2Pipeline.createPromptRequest(
                        touristMessage = cleanMessage,
                        touristLanguage = touristLang,
                        intent = classification.intent,
                        template = template
                    )

                    return@runCatching UnifiedTouristResult.Tier2PromptRequired(promptRequest)
                }
            }
        }

        // 4. TIER 3 FALLBACK: Unclassified or out-of-scope -> translate directly to Noor
        val fallbackResult = tier3Pipeline.processTouristMessage(
            rawText = cleanMessage,
            forcedTouristLanguage = touristLang
        ).getOrThrow()

        conversationHistory.add(fallbackResult.conversationTurn)
        UnifiedTouristResult.Tier3Fallback(fallbackResult)
    }

    /**
     * Completes a Tier 2 interaction after Noor enters the requested atomic slot value.
     * Caches value into database for future Tier 1 hits, fills template, and translates to tourist.
     */
    suspend fun completeTier2Prompt(
        promptRequest: Tier2PromptRequest,
        farmerValueInput: String
    ): Result<Tier2CompletedResult> = runCatching {
        val result = tier2Pipeline.completeWithFarmerInput(
            promptRequest = promptRequest,
            farmerValueInput = farmerValueInput
        ).getOrThrow()

        conversationHistory.add(result.conversationTurn)
        result
    }

    /**
     * Processes a free-form response from host farmer (Noor) back to tourist under Tier 3.
     */
    suspend fun handleFarmerReply(
        replyText: String,
        targetTouristLanguage: String
    ): Result<FarmerTurnResult> = runCatching {
        val result = tier3Pipeline.processFarmerReply(replyText, targetTouristLanguage).getOrThrow()
        conversationHistory.add(result.conversationTurn)
        result
    }

    /**
     * Database Access: Get slot value
     */
    suspend fun getDatabaseSlot(slotKey: String): String? = database.getSlot(slotKey)

    /**
     * Database Access: Set or update slot value
     */
    suspend fun setDatabaseSlot(slotKey: String, value: String): Unit = database.setSlot(slotKey, value)

    /**
     * Database Access: Retrieve all slots
     */
    suspend fun getAllDatabaseSlots(): Map<String, String> = database.getAllSlots()

    /**
     * Database Access: Direct database instance
     */
    fun getDatabase(): IFarmDatabase = database

    /**
     * Intent Classifier instance
     */
    fun getClassifier(): IIntentClassifier = intentClassifier

    /**
     * Pre-downloads a language model pack (e.g., Spanish, French, German) to guarantee
     * instant sub-100ms offline translation when tourists arrive.
     */
    suspend fun predownloadLanguage(languageCode: String): Result<Unit> {
        return translationEngine.downloadModel(languageCode)
    }

    /**
     * Returns list of currently downloaded on-device model language codes.
     */
    suspend fun getDownloadedLanguages(): List<String> {
        return translationEngine.getDownloadedModels()
    }

    /**
     * Returns full unified conversation session history across all tiers.
     */
    fun getConversationHistory(): List<ConversationTurn> {
        return conversationHistory.toList()
    }

    /**
     * Clears current conversation history.
     */
    fun clearHistory() {
        conversationHistory.clear()
        tier3Pipeline.clearHistory()
    }

    /**
     * Executes the end-to-end review understanding and suggestion pipeline on a visitor review.
     * Detects language, translates to Noor's tongue, extracts aspects and severity, and produces
     * actionable deterministic recommendations.
     */
    suspend fun analyzeReview(
        reviewText: String,
        targetLanguage: String? = null
    ): Result<ReviewAnalysisResult> {
        return reviewAnalyzerEngine.analyzeReview(reviewText, targetLanguage)
    }

    /**
     * Releases active native resources, translators, ONNX sessions, and ML Kit clients.
     */
    fun shutdown() {
        translationEngine.close()
        languageIdentifier.close()
        intentClassifier.close()
        reviewAnalyzerEngine.close()
    }
}
