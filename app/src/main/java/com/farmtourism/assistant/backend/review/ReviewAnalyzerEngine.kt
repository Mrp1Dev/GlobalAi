package com.farmtourism.assistant.backend.review

import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.Languages
import com.farmtourism.assistant.backend.review.classifier.IReviewClassifier
import com.farmtourism.assistant.backend.review.classifier.ReviewAspectClassifier
import com.farmtourism.assistant.backend.review.classifier.ReviewAspectTaxonomy
import com.farmtourism.assistant.backend.review.model.ReviewAnalysisResult
import com.farmtourism.assistant.backend.review.model.ReviewAnalysisStatus
import com.farmtourism.assistant.backend.review.model.ReviewClassification
import com.farmtourism.assistant.backend.review.model.ReviewTimings
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity
import com.farmtourism.assistant.backend.review.rules.RecommendationRuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Orchestrator for the Complete Multilingual Review Analysis and Suggestion Pipeline.
 *
 * Pipeline Flow:
 * 1. Visitor Review in any language (German, Spanish, French, English, Hindi, etc.)
 *    ↓
 * 2. On-device Google ML Kit Language Identification (<5ms)
 *    ↓
 * 3. On-device Google ML Kit Translation into Noor's language (Hindi / configured language)
 *    ↓
 * 4. Multi-Aspect, Sentiment Polarity, and Severity Classification
 *    ↓
 * 5. Deterministic Recommendation Rules with Priority Ordering
 *    ↓
 * 6. Guardrail: Noor remains the human in the loop
 */
class ReviewAnalyzerEngine(
    private val languageIdentifier: ILanguageIdentifier,
    private val translationEngine: ITranslationEngine,
    private val reviewClassifier: IReviewClassifier = ReviewAspectClassifier(),
    private val defaultFarmerLanguage: String = "hi"
) {

    suspend fun analyzeReview(
        reviewText: String,
        targetLanguage: String? = null
    ): Result<ReviewAnalysisResult> = withContext(Dispatchers.Default) {
        val totalStart = System.currentTimeMillis()
        val trimmed = reviewText.trim()
        val targetLang = targetLanguage ?: defaultFarmerLanguage

        // 1. Handle empty input
        if (trimmed.isBlank()) {
            return@withContext Result.success(
                ReviewAnalysisResult(
                    status = ReviewAnalysisStatus.EMPTY,
                    originalText = "",
                    targetLanguageCode = targetLang,
                    message = "No review text provided. Please enter a visitor review.",
                    timings = ReviewTimings(totalMs = max(1L, System.currentTimeMillis() - totalStart))
                )
            )
        }

        // 2. Step 1: Language Detection
        val tDetectStart = System.currentTimeMillis()
        val detectionResult = languageIdentifier.identifyLanguage(trimmed).getOrNull()
        val detectionMs = max(1L, System.currentTimeMillis() - tDetectStart)

        val detectedLang = detectionResult?.languageCode?.lowercase() ?: "und"
        val langDisplayName = Languages.getDisplayName(detectedLang)

        // Fail-safe: Handle undetermined language (Never silently fall back to English)
        if (detectedLang == "und" || !detectionResult!!.isReliable) {
            return@withContext Result.success(
                ReviewAnalysisResult(
                    status = ReviewAnalysisStatus.UNDETERMINED_LANGUAGE,
                    originalText = trimmed,
                    sourceLanguageCode = "und",
                    sourceLanguageName = "Undetermined",
                    targetLanguageCode = targetLang,
                    message = "Language unclear — please ask a person.",
                    detectionResult = detectionResult,
                    timings = ReviewTimings(
                        detectionMs = detectionMs,
                        totalMs = max(1L, System.currentTimeMillis() - totalStart)
                    )
                )
            )
        }

        // 3. Step 2: On-Device Translation into Noor's Language (Hindi)
        var translatedText = trimmed
        var translationMs = 0L

        if (!detectedLang.equals(targetLang, ignoreCase = true)) {
            val tTranslateStart = System.currentTimeMillis()
            val translationOutcome = translationEngine.translate(
                text = trimmed,
                sourceLanguage = detectedLang,
                targetLanguage = targetLang
            )

            if (translationOutcome.isFailure) {
                val err = translationOutcome.exceptionOrNull()?.localizedMessage ?: "Translation failed"
                return@withContext Result.success(
                    ReviewAnalysisResult(
                        status = ReviewAnalysisStatus.TRANSLATION_ERROR,
                        originalText = trimmed,
                        sourceLanguageCode = detectedLang,
                        sourceLanguageName = langDisplayName,
                        targetLanguageCode = targetLang,
                        errorMessage = "Translation error: $err",
                        detectionResult = detectionResult,
                        timings = ReviewTimings(
                            detectionMs = detectionMs,
                            translationMs = max(1L, System.currentTimeMillis() - tTranslateStart),
                            totalMs = max(1L, System.currentTimeMillis() - totalStart)
                        )
                    )
                )
            }

            translatedText = translationOutcome.getOrNull()?.translatedText ?: trimmed
            translationMs = max(1L, System.currentTimeMillis() - tTranslateStart)
        }

        // 4. Step 3: Aspect, Polarity, and Severity Classification
        val tClassifyStart = System.currentTimeMillis()
        val classificationOutcome = reviewClassifier.classifyReview(
            text = trimmed,
            translatedHindiText = translatedText
        )

        if (classificationOutcome.isFailure) {
            val err = classificationOutcome.exceptionOrNull()?.localizedMessage ?: "Classification failed"
            return@withContext Result.success(
                ReviewAnalysisResult(
                    status = ReviewAnalysisStatus.CLASSIFICATION_ERROR,
                    originalText = trimmed,
                    sourceLanguageCode = detectedLang,
                    sourceLanguageName = langDisplayName,
                    translatedText = translatedText,
                    targetLanguageCode = targetLang,
                    errorMessage = "Classification error: $err",
                    detectionResult = detectionResult,
                    timings = ReviewTimings(
                        detectionMs = detectionMs,
                        translationMs = translationMs,
                        classificationMs = max(1L, System.currentTimeMillis() - tClassifyStart),
                        totalMs = max(1L, System.currentTimeMillis() - totalStart)
                    )
                )
            )
        }

        val classification = classificationOutcome.getOrNull() ?: ReviewClassification(
            overallSentiment = Sentiment.NEUTRAL,
            aspects = emptyList(),
            reviewSeverity = Severity.LOW
        )
        val classificationMs = max(1L, System.currentTimeMillis() - tClassifyStart)

        // 5. Step 4: Deterministic Recommendation Generation
        val tRecStart = System.currentTimeMillis()
        val recommendations = RecommendationRuleEngine.generateRecommendations(classification)
        val recommendationMs = max(1L, System.currentTimeMillis() - tRecStart)

        val totalMs = max(1L, System.currentTimeMillis() - totalStart)

        Result.success(
            ReviewAnalysisResult(
                status = ReviewAnalysisStatus.SUCCESS,
                originalText = trimmed,
                sourceLanguageCode = detectedLang,
                sourceLanguageName = langDisplayName,
                translatedText = translatedText,
                targetLanguageCode = targetLang,
                classification = classification,
                recommendations = recommendations,
                detectionResult = detectionResult,
                timings = ReviewTimings(
                    detectionMs = detectionMs,
                    translationMs = translationMs,
                    classificationMs = classificationMs,
                    recommendationMs = recommendationMs,
                    totalMs = totalMs
                ),
                message = "Analysis complete."
            )
        )
    }

    fun close() {
        reviewClassifier.close()
    }
}
