package com.farmtourism.assistant.backend

import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.LanguageDetectionResult
import com.farmtourism.assistant.backend.model.TranslationResult
import com.farmtourism.assistant.backend.review.ReviewAnalyzerEngine
import com.farmtourism.assistant.backend.review.classifier.ReviewAspectClassifier
import com.farmtourism.assistant.backend.review.demo.DemoReviewScenarios
import com.farmtourism.assistant.backend.review.model.ReviewAnalysisStatus
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewAnalyzerTest {

    private lateinit var fakeLanguageIdentifier: FakeTestLanguageIdentifier
    private lateinit var fakeTranslationEngine: FakeTestTranslationEngine
    private lateinit var reviewClassifier: ReviewAspectClassifier
    private lateinit var analyzerEngine: ReviewAnalyzerEngine

    class FakeTestLanguageIdentifier : ILanguageIdentifier {
        var forcedLanguage: String? = null

        override suspend fun identifyLanguage(text: String): Result<LanguageDetectionResult> {
            val lang = forcedLanguage ?: detectSimple(text)
            val isReliable = lang != "und"
            return Result.success(LanguageDetectionResult(lang, if (isReliable) 0.98f else 0.0f, isReliable))
        }

        override suspend fun identifyPossibleLanguages(text: String): Result<List<LanguageDetectionResult>> {
            val r = identifyLanguage(text).getOrThrow()
            return Result.success(listOf(r))
        }

        override fun close() {}

        private fun detectSimple(text: String): String {
            val lower = text.lowercase()
            return when {
                lower.contains("die tour") || lower.contains("über die") || lower.contains("sauber") -> "de"
                lower.contains("encantó") || lower.contains("finca") || lower.contains("demasiado cara") -> "es"
                lower.contains("la ferme") || lower.contains("trouver") || lower.contains("panneau") -> "fr"
                lower.contains("मेजबान") || lower.contains("कॉफी") || lower.contains("स्वाद") -> "hi"
                lower.contains("wait") || lower.contains("minutes") || lower.contains("disorganized") -> "en"
                else -> "en"
            }
        }
    }

    class FakeTestTranslationEngine : ITranslationEngine {
        val mockTranslations = mutableMapOf<String, String>()

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String
        ): Result<TranslationResult> {
            val key = "${sourceLanguage}_$targetLanguage"
            val translated = mockTranslations[key] ?: "[Hindi translated from $sourceLanguage: $text]"
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

        override suspend fun isModelDownloaded(languageCode: String): Boolean = true
        override suspend fun downloadModel(languageCode: String, requireWifi: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun getDownloadedModels(): List<String> = listOf("hi", "en", "de", "es", "fr")
        override suspend fun deleteModel(languageCode: String): Result<Unit> = Result.success(Unit)
        override fun close() {}
    }

    @Before
    fun setUp() {
        fakeLanguageIdentifier = FakeTestLanguageIdentifier()
        fakeTranslationEngine = FakeTestTranslationEngine()
        reviewClassifier = ReviewAspectClassifier()
        analyzerEngine = ReviewAnalyzerEngine(
            languageIdentifier = fakeLanguageIdentifier,
            translationEngine = fakeTranslationEngine,
            reviewClassifier = reviewClassifier,
            defaultFarmerLanguage = "hi"
        )
    }

    @Test
    fun testDemo1_GermanPunctualCleanReview() = runBlocking {
        val scenario = DemoReviewScenarios.findById("demo-1-german-clean")!!
        val outcome = analyzerEngine.analyzeReview(scenario.originalReview)

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals(ReviewAnalysisStatus.SUCCESS, result.status)
        assertEquals("de", result.sourceLanguageCode)
        assertEquals("German", result.sourceLanguageName)

        val aspects = result.classification.aspects.map { it.aspect }
        assertTrue("Aspects should contain cleanliness", aspects.contains("cleanliness"))
        assertTrue("Aspects should contain experience", aspects.contains("experience"))
        assertEquals(Sentiment.POSITIVE, result.classification.overallSentiment)
        assertEquals(Severity.LOW, result.classification.reviewSeverity)

        // Recommendations should be low priority maintenance
        assertTrue(result.recommendations.isNotEmpty())
        val cleanRec = result.recommendations.find { it.aspect == "cleanliness" }
        assertNotNull(cleanRec)
        assertEquals(Severity.LOW, cleanRec!!.priority)
    }

    @Test
    fun testDemo2_EnglishCriticalWaitingDelay() = runBlocking {
        val scenario = DemoReviewScenarios.findById("demo-2-english-delay")!!
        val outcome = analyzerEngine.analyzeReview(scenario.originalReview)

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals(ReviewAnalysisStatus.SUCCESS, result.status)
        assertEquals("en", result.sourceLanguageCode)

        val waitingAspect = result.classification.aspects.find { it.aspect == "waiting_time" }
        assertNotNull("Should detect waiting_time aspect", waitingAspect)
        assertEquals(Sentiment.NEGATIVE, waitingAspect!!.sentiment)

        // Critical cue '45 minutes' and 'disorganized' must trigger HIGH severity
        assertEquals(Severity.HIGH, result.classification.reviewSeverity)
        assertEquals(Severity.HIGH, waitingAspect.severity)
        assertEquals(Sentiment.NEGATIVE, result.classification.overallSentiment)

        // First recommendation must be the high priority waiting issue
        assertTrue(result.recommendations.isNotEmpty())
        val topRec = result.recommendations.first()
        assertEquals("waiting_time", topRec.aspect)
        assertEquals(Severity.HIGH, topRec.priority)
        assertTrue(topRec.action.contains("improving arrival and start-time", ignoreCase = true))
    }

    @Test
    fun testDemo3_SpanishMixedPricing() = runBlocking {
        val scenario = DemoReviewScenarios.findById("demo-3-spanish-mixed")!!
        val outcome = analyzerEngine.analyzeReview(scenario.originalReview)

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals("es", result.sourceLanguageCode)

        val aspects = result.classification.aspects.map { it.aspect }
        assertTrue("Should detect pricing", aspects.contains("pricing"))
        assertTrue("Should detect food", aspects.contains("food"))

        val pricingAspect = result.classification.aspects.find { it.aspect == "pricing" }
        assertEquals(Sentiment.NEGATIVE, pricingAspect!!.sentiment)

        val foodAspect = result.classification.aspects.find { it.aspect == "food" }
        assertEquals(Sentiment.POSITIVE, foodAspect!!.sentiment)

        // Negative pricing complaint should dominate/influence or sort highest
        val topRec = result.recommendations.first()
        assertEquals("pricing", topRec.aspect)
    }

    @Test
    fun testDemo4_FrenchDirections() = runBlocking {
        val scenario = DemoReviewScenarios.findById("demo-4-french-directions")!!
        val outcome = analyzerEngine.analyzeReview(scenario.originalReview)

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals("fr", result.sourceLanguageCode)

        val dirAspect = result.classification.aspects.find { it.aspect == "directions" }
        assertNotNull("Should identify directions issue", dirAspect)
        assertEquals(Sentiment.NEGATIVE, dirAspect!!.sentiment)

        val dirRec = result.recommendations.find { it.aspect == "directions" }
        assertNotNull(dirRec)
        assertTrue(dirRec!!.action.contains("clearer directions", ignoreCase = true))
    }

    @Test
    fun testDemo5_HindiHospitality() = runBlocking {
        val scenario = DemoReviewScenarios.findById("demo-5-hindi-hospitality")!!
        val outcome = analyzerEngine.analyzeReview(scenario.originalReview)

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals("hi", result.sourceLanguageCode)

        val aspects = result.classification.aspects.map { it.aspect }
        assertTrue("Should detect hospitality", aspects.contains("hospitality"))
        assertTrue("Should detect food", aspects.contains("food"))
        assertEquals(Sentiment.POSITIVE, result.classification.overallSentiment)
        assertEquals(Severity.LOW, result.classification.reviewSeverity)
    }

    @Test
    fun testFailSafe_UndeterminedLanguage() = runBlocking {
        fakeLanguageIdentifier.forcedLanguage = "und"
        val outcome = analyzerEngine.analyzeReview("asdkjhasd ????? 12345")

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals(ReviewAnalysisStatus.UNDETERMINED_LANGUAGE, result.status)
        assertEquals("Language unclear — please ask a person.", result.message)
    }

    @Test
    fun testEmptyInput_HandledSafely() = runBlocking {
        val outcome = analyzerEngine.analyzeReview("   ")

        assertTrue(outcome.isSuccess)
        val result = outcome.getOrThrow()
        assertEquals(ReviewAnalysisStatus.EMPTY, result.status)
        assertTrue(result.recommendations.isEmpty())
    }
}
