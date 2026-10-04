package com.farmtourism.assistant.backend

import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.LanguageDetectionResult
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier
import com.farmtourism.assistant.backend.model.TranslationResult
import com.farmtourism.assistant.backend.pipeline.Tier3FallbackPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Tier3FallbackPipelineTest {

    private lateinit var fakeTranslationEngine: FakeTranslationEngine
    private lateinit var fakeLanguageIdentifier: FakeLanguageIdentifier
    private lateinit var pipeline: Tier3FallbackPipeline

    @Before
    fun setup() {
        fakeTranslationEngine = FakeTranslationEngine()
        fakeLanguageIdentifier = FakeLanguageIdentifier()
        pipeline = Tier3FallbackPipeline(
            translationEngine = fakeTranslationEngine,
            languageIdentifier = fakeLanguageIdentifier,
            farmerProfile = FarmerProfile(name = "Noor", language = "hi")
        )
    }

    @Test
    fun testTouristMessage_AutoDetectsLanguageAndTranslatesToHindi() = runBlocking {
        fakeLanguageIdentifier.mockDetectedLanguage = "es"
        fakeTranslationEngine.registerMockTranslation(
            text = "Hola, ¿cuánto cuesta el tour?",
            source = "es",
            target = "hi",
            result = "नमस्ते, टूर की कीमत क्या है?"
        )

        val result = pipeline.processTouristMessage("Hola, ¿cuánto cuesta el tour?").getOrThrow()

        assertEquals("Hola, ¿cuánto cuesta el tour?", result.touristOriginalText)
        assertEquals("es", result.touristLanguage)
        assertEquals("नमस्ते, टूर की कीमत क्या है?", result.farmerTranslatedText)
        assertEquals("hi", result.farmerLanguage)
        assertEquals(PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK, result.conversationTurn.tier)
        assertEquals(MessageSender.TOURIST, result.conversationTurn.sender)
    }

    @Test
    fun testTouristMessage_WithForcedLanguage() = runBlocking {
        fakeTranslationEngine.registerMockTranslation(
            text = "Bonjour, avez-vous des pommes fraîches?",
            source = "fr",
            target = "hi",
            result = "नमस्ते, क्या आपके पास ताजे सेब हैं?"
        )

        val result = pipeline.processTouristMessage(
            rawText = "Bonjour, avez-vous des pommes fraîches?",
            forcedTouristLanguage = "fr"
        ).getOrThrow()

        assertEquals("fr", result.touristLanguage)
        assertEquals("नमस्ते, क्या आपके पास ताजे सेब हैं?", result.farmerTranslatedText)
    }

    @Test
    fun testFarmerReply_TranslatesHindiBackToTouristLanguage() = runBlocking {
        fakeTranslationEngine.registerMockTranslation(
            text = "हाँ, ताजे सेब उपलब्ध हैं और कीमत ₹100 प्रति किलो है।",
            source = "hi",
            target = "fr",
            result = "Oui, des pommes fraîches sont disponibles à 100 Rs le kilo."
        )

        val result = pipeline.processFarmerReply(
            farmerReplyText = "हाँ, ताजे सेब उपलब्ध हैं और कीमत ₹100 प्रति किलो है।",
            targetTouristLanguage = "fr"
        ).getOrThrow()

        assertEquals("हाँ, ताजे सेब उपलब्ध हैं और कीमत ₹100 प्रति किलो है।", result.farmerOriginalText)
        assertEquals("hi", result.farmerLanguage)
        assertEquals("Oui, des pommes fraîches sont disponibles à 100 Rs le kilo.", result.touristTranslatedText)
        assertEquals("fr", result.touristLanguage)
        assertEquals(MessageSender.FARMER, result.conversationTurn.sender)
    }

    @Test
    fun testFullTwoWayExchangeMaintainsHistory() = runBlocking {
        fakeLanguageIdentifier.mockDetectedLanguage = "de"
        fakeTranslationEngine.registerMockTranslation(
            text = "Wann kann ich kommen?",
            source = "de",
            target = "hi",
            result = "मैं कब आ सकता हूँ?"
        )
        fakeTranslationEngine.registerMockTranslation(
            text = "आप सुबह 10 बजे आ सकते हैं।",
            source = "hi",
            target = "de",
            result = "Sie können um 10 Uhr morgens kommen."
        )

        val touristResult = pipeline.processTouristMessage("Wann kann ich kommen?").getOrThrow()
        val farmerResult = pipeline.processFarmerReply(
            "आप सुबह 10 बजे आ सकते हैं।",
            touristResult.touristLanguage
        ).getOrThrow()

        val history = pipeline.getHistory()
        assertEquals(2, history.size)
        assertEquals(MessageSender.TOURIST, history[0].sender)
        assertEquals(MessageSender.FARMER, history[1].sender)
        assertEquals("Sie können um 10 Uhr morgens kommen.", farmerResult.touristTranslatedText)
    }

    // --- Fake Test Implementations ---

    class FakeTranslationEngine : ITranslationEngine {
        private val mockTranslations = mutableMapOf<String, String>()
        private val downloadedModels = mutableSetOf("hi", "en")

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
                    latencyMs = 15L
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

        override suspend fun deleteModel(languageCode: String): Result<Unit> {
            downloadedModels.remove(languageCode)
            return Result.success(Unit)
        }

        override fun close() {}
    }

    class FakeLanguageIdentifier : ILanguageIdentifier {
        var mockDetectedLanguage: String = "es"

        override suspend fun identifyLanguage(text: String): Result<LanguageDetectionResult> {
            return Result.success(
                LanguageDetectionResult(
                    languageCode = mockDetectedLanguage,
                    confidence = 0.98f,
                    isReliable = true
                )
            )
        }

        override suspend fun identifyPossibleLanguages(text: String): Result<List<LanguageDetectionResult>> {
            return Result.success(
                listOf(
                    LanguageDetectionResult(mockDetectedLanguage, 0.98f, true)
                )
            )
        }

        override fun close() {}
    }
}
