package com.farmtourism.assistant.backend

import com.farmtourism.assistant.backend.database.LocalFarmDatabase
import com.farmtourism.assistant.backend.engine.IIntentClassifier
import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.IntentClassificationResult
import com.farmtourism.assistant.backend.model.LanguageDetectionResult
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier
import com.farmtourism.assistant.backend.model.TranslationResult
import com.farmtourism.assistant.backend.model.UnifiedTouristResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullPipelineTest {

    private lateinit var fakeTranslationEngine: FakeTranslationEngine
    private lateinit var fakeLanguageIdentifier: FakeLanguageIdentifier
    private lateinit var fakeIntentClassifier: FakeIntentClassifier
    private lateinit var database: LocalFarmDatabase
    private lateinit var backend: FarmAssistantBackend

    @Before
    fun setup() {
        fakeTranslationEngine = FakeTranslationEngine()
        fakeLanguageIdentifier = FakeLanguageIdentifier()
        fakeIntentClassifier = FakeIntentClassifier()
        database = LocalFarmDatabase() // In-memory mode for unit testing

        backend = FarmAssistantBackend(
            farmerProfile = FarmerProfile(name = "Noor", language = "hi"),
            translationEngine = fakeTranslationEngine,
            languageIdentifier = fakeLanguageIdentifier,
            intentClassifier = fakeIntentClassifier,
            database = database,
            defaultTouristLanguage = "en"
        )
    }

    @Test
    fun testTier1_DatabaseHit_DeliversInstantAutomatedReply() = runBlocking {
        // Arrange
        val spanishQuery = "¿Cuánto cuesta el tour por la granja?"
        fakeLanguageIdentifier.mockLanguage = "es"
        fakeIntentClassifier.registerMockClassification(
            text = spanishQuery,
            intent = "price_tour",
            confidence = 0.98f
        )
        // tour_price_inr is pre-seeded as "500" in LocalFarmDatabase
        fakeTranslationEngine.registerMockTranslation(
            text = "The price for a guided farm tour is ₹500 per person.",
            source = "en",
            target = "es",
            result = "El precio de una visita guiada por la granja es de 500 ₹ por persona."
        )

        // Act
        val result = backend.processTouristMessage(spanishQuery).getOrThrow()

        // Assert
        assertTrue("Result should be Tier1Hit", result is UnifiedTouristResult.Tier1Hit)
        val tier1 = (result as UnifiedTouristResult.Tier1Hit).result
        assertEquals("price_tour", tier1.intent)
        assertEquals("tour_price_inr", tier1.slotKey)
        assertEquals("500", tier1.slotValue)
        assertEquals("El precio de una visita guiada por la granja es de 500 ₹ por persona.", tier1.responseInTouristLanguage)
        assertEquals(PipelineTier.TIER_1_FAST_DB, tier1.conversationTurn.tier)
        assertEquals(MessageSender.TOURIST, tier1.conversationTurn.sender)

        val history = backend.getConversationHistory()
        assertEquals(1, history.size)
        assertEquals(PipelineTier.TIER_1_FAST_DB, history[0].tier)
    }

    @Test
    fun testTier2_DatabaseMiss_PromptsNoor_ThenCachesAndReplies() = runBlocking {
        // Arrange: pet_policy_rules is not seeded in database yet
        val frenchQuery = "Acceptez-vous les chiens à la ferme?"
        fakeLanguageIdentifier.mockLanguage = "fr"
        fakeIntentClassifier.registerMockClassification(
            text = frenchQuery,
            intent = "pet_policy",
            confidence = 0.95f
        )
        fakeTranslationEngine.registerMockTranslation(
            text = "Our pet policy: friendly dogs on leashes are warmly welcome.",
            source = "en",
            target = "fr",
            result = "Notre politique relative aux animaux : les chiens tenus en laisse sont les bienvenus."
        )

        // Act 1: Tourist asks question -> DB Miss triggers Tier 2 Prompt Request
        val firstResult = backend.processTouristMessage(frenchQuery).getOrThrow()
        assertTrue("Should trigger Tier 2 prompt request", firstResult is UnifiedTouristResult.Tier2PromptRequired)
        val promptReq = (firstResult as UnifiedTouristResult.Tier2PromptRequired).promptRequest

        // Assert Prompt for Noor is in Hindi
        assertEquals("pet_policy", promptReq.intent)
        assertEquals("pet_policy_rules", promptReq.slotKey)
        assertEquals("खेत में पालतू जानवरों (जैसे कुत्तों) के लिए क्या नियम हैं?", promptReq.promptForNoor)

        // Act 2: Noor enters the missing slot answer
        val completion = backend.completeTier2Prompt(
            promptRequest = promptReq,
            farmerValueInput = "friendly dogs on leashes are warmly welcome"
        ).getOrThrow()

        // Assert Tier 2 completed and translated to tourist's French
        assertEquals("friendly dogs on leashes are warmly welcome", completion.enteredValue)
        assertEquals("Notre politique relative aux animaux : les chiens tenus en laisse sont les bienvenus.", completion.responseInTouristLanguage)
        assertEquals(PipelineTier.TIER_2_TEMPLATE_PROMPT, completion.conversationTurn.tier)

        // Verify Database now contains this newly learned value!
        val cachedValue = backend.getDatabaseSlot("pet_policy_rules")
        assertEquals("friendly dogs on leashes are warmly welcome", cachedValue)

        // Act 3: Subsequent tourist inquiry on same intent NOW instantly hits Tier 1!
        val germanQuery = "Darf ich ein Haustier mitbringen?"
        fakeLanguageIdentifier.mockLanguage = "de"
        fakeIntentClassifier.registerMockClassification(
            text = germanQuery,
            intent = "pet_policy",
            confidence = 0.94f
        )
        fakeTranslationEngine.registerMockTranslation(
            text = "Our pet policy: friendly dogs on leashes are warmly welcome.",
            source = "en",
            target = "de",
            result = "Unsere Haustierregelung: Freundliche Hunde an der Leine sind herzlich willkommen."
        )

        val subsequentResult = backend.processTouristMessage(germanQuery).getOrThrow()
        assertTrue("Subsequent query must now hit Tier 1 directly!", subsequentResult is UnifiedTouristResult.Tier1Hit)
        val subsequentTier1 = (subsequentResult as UnifiedTouristResult.Tier1Hit).result
        assertEquals("Unsere Haustierregelung: Freundliche Hunde an der Leine sind herzlich willkommen.", subsequentTier1.responseInTouristLanguage)
    }

    @Test
    fun testTier3_OutOfScope_DirectFallbackToNoor() = runBlocking {
        // Arrange
        val weatherQuery = "Va-t-il pleuvoir demain après-midi?"
        fakeLanguageIdentifier.mockLanguage = "fr"
        fakeIntentClassifier.registerMockClassification(
            text = weatherQuery,
            intent = "out_of_scope",
            confidence = 0.90f
        )
        fakeTranslationEngine.registerMockTranslation(
            text = weatherQuery,
            source = "fr",
            target = "hi",
            result = "क्या कल दोपहर बारिश होगी?"
        )
        fakeTranslationEngine.registerMockTranslation(
            text = "नहीं, कल पूरे दिन धूप रहेगी।",
            source = "hi",
            target = "fr",
            result = "Non, il fera beau toute la journée demain."
        )

        // Act 1: Tourist message falls back to Tier 3 direct translation to Noor's Hindi
        val result = backend.processTouristMessage(weatherQuery).getOrThrow()
        assertTrue("Result should fall back to Tier 3", result is UnifiedTouristResult.Tier3Fallback)
        val fallback = (result as UnifiedTouristResult.Tier3Fallback).result
        assertEquals("क्या कल दोपहर बारिश होगी?", fallback.farmerTranslatedText)
        assertEquals("hi", fallback.farmerLanguage)

        // Act 2: Noor replies in Hindi, translated directly back to French
        val farmerReply = backend.handleFarmerReply(
            replyText = "नहीं, कल पूरे दिन धूप रहेगी।",
            targetTouristLanguage = fallback.touristLanguage
        ).getOrThrow()

        assertEquals("Non, il fera beau toute la journée demain.", farmerReply.touristTranslatedText)
        assertEquals("fr", farmerReply.touristLanguage)

        val history = backend.getConversationHistory()
        assertEquals(2, history.size)
        assertEquals(PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK, history[0].tier)
        assertEquals(PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK, history[1].tier)
    }

    @Test
    fun testPredeterminedTemplates_VariationsAndSlotFilling() = runBlocking {
        val template = database.getTemplate("price_tour")
        assertNotNull(template)
        assertEquals("tour_price_inr", template!!.slotKey)

        // Default fill
        val defaultFilled = template.fillTemplate("600")
        assertEquals("The price for a guided farm tour is ₹600 per person.", defaultFilled)

        // Noor prompt in Hindi
        val noorHiPrompt = template.getNoorPrompt("hi")
        assertEquals("इस फार्म टूर की प्रति व्यक्ति कीमत क्या है?", noorHiPrompt)

        // All replies should contain the slotKey
        val allReplies = template.getAllReplyTemplates()
        assertTrue(allReplies.isNotEmpty())
        for (reply in allReplies) {
            assertTrue(reply.contains("{tour_price_inr}"))
        }
    }

    // --- Mock Implementations for Tests ---

    class FakeIntentClassifier : IIntentClassifier {
        override val confidenceThreshold: Float = 0.70f
        private val mockMap = mutableMapOf<String, IntentClassificationResult>()

        fun registerMockClassification(text: String, intent: String, confidence: Float) {
            mockMap[text] = IntentClassificationResult(
                intent = intent,
                confidence = confidence,
                isConfident = confidence >= confidenceThreshold && intent != "out_of_scope",
                latencyMs = 25L
            )
        }

        override suspend fun classify(text: String): Result<IntentClassificationResult> {
            val res = mockMap[text] ?: IntentClassificationResult(
                intent = "out_of_scope",
                confidence = 0.50f,
                isConfident = false,
                latencyMs = 20L
            )
            return Result.success(res)
        }

        override fun close() {}
    }

    class FakeTranslationEngine : ITranslationEngine {
        private val mockTranslations = mutableMapOf<String, String>()
        private val downloadedModels = mutableSetOf("hi", "en", "es", "fr", "de")

        fun registerMockTranslation(text: String, source: String, target: String, result: String) {
            mockTranslations["${source}_${target}_$text"] = result
        }

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String
        ): Result<TranslationResult> {
            val key = "${sourceLanguage}_${targetLanguage}_$text"
            val translated = mockTranslations[key] ?: "[Translated $sourceLanguage->$targetLanguage: $text]"
            return Result.success(
                TranslationResult(
                    originalText = text,
                    translatedText = translated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    latencyMs = 12L
                )
            )
        }

        override suspend fun isModelDownloaded(languageCode: String): Boolean =
            downloadedModels.contains(languageCode)

        override suspend fun downloadModel(languageCode: String, requireWifi: Boolean): Result<Unit> {
            downloadedModels.add(languageCode)
            return Result.success(Unit)
        }

        override suspend fun getDownloadedModels(): List<String> = downloadedModels.toList()
        override suspend fun deleteModel(languageCode: String): Result<Unit> = Result.success(Unit)
        override fun close() {}
    }

    class FakeLanguageIdentifier : ILanguageIdentifier {
        var mockLanguage: String = "en"

        override suspend fun identifyLanguage(text: String): Result<LanguageDetectionResult> {
            return Result.success(LanguageDetectionResult(mockLanguage, 0.99f, true))
        }

        override suspend fun identifyPossibleLanguages(text: String): Result<List<LanguageDetectionResult>> {
            return Result.success(listOf(LanguageDetectionResult(mockLanguage, 0.99f, true)))
        }

        override fun close() {}
    }
}
