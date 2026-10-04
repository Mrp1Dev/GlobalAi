package com.farmtourism.assistant.backend.review.model

import com.farmtourism.assistant.backend.model.LanguageDetectionResult

/**
 * Sentiment polarity of a visitor review or individual review aspect.
 */
enum class Sentiment(val displayName: String) {
    POSITIVE("Positive"),
    NEUTRAL("Neutral"),
    NEGATIVE("Negative");

    companion object {
        fun fromString(value: String): Sentiment = when (value.lowercase().trim()) {
            "positive", "pos" -> POSITIVE
            "negative", "neg" -> NEGATIVE
            else -> NEUTRAL
        }
    }
}

/**
 * Urgency/severity priority for flagged issues in a visitor review.
 */
enum class Severity(val displayName: String, val weight: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3);

    companion object {
        fun fromString(value: String): Severity = when (value.lowercase().trim()) {
            "high" -> HIGH
            "medium" -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * A specific identified domain aspect within a visitor's review (e.g., waiting_time, food, hospitality).
 */
data class ReviewAspect(
    val aspect: String,
    val sentiment: Sentiment,
    val severity: Severity = Severity.LOW,
    val confidence: Float = 0.0f
) {
    val aspectDisplayName: String
        get() = when (aspect.lowercase().trim()) {
            "waiting_time", "timing_waiting" -> "Waiting time"
            "directions", "directions_access" -> "Directions & Access"
            "hospitality", "guide_hospitality" -> "Hospitality & Host"
            "food", "food_drink" -> "Food & Refreshments"
            "cleanliness", "facilities_cleanliness" -> "Cleanliness & Facilities"
            "experience", "experience_activity" -> "Experience & Tour"
            "pricing", "price_value" -> "Pricing & Value"
            "communication" -> "Communication"
            "learning_authenticity" -> "Authentic Learning"
            "activities" -> "Activities"
            else -> aspect.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
}

/**
 * Multi-aspect classification output for a single review.
 */
data class ReviewClassification(
    val overallSentiment: Sentiment,
    val aspects: List<ReviewAspect> = emptyList(),
    val reviewSeverity: Severity = Severity.LOW,
    val formattedOverallSentiment: String = deriveFormattedSentiment(overallSentiment, aspects)
) {
    companion object {
        fun deriveFormattedSentiment(overall: Sentiment, aspects: List<ReviewAspect>): String {
            val posCount = aspects.count { it.sentiment == Sentiment.POSITIVE }
            val negCount = aspects.count { it.sentiment == Sentiment.NEGATIVE }

            return if (posCount > 0 && negCount > 0) {
                if (overall == Sentiment.NEGATIVE) "Mixed / Negative"
                else if (overall == Sentiment.POSITIVE) "Mixed / Positive"
                else "Mixed"
            } else when (overall) {
                Sentiment.POSITIVE -> "Positive"
                Sentiment.NEGATIVE -> "Negative"
                Sentiment.NEUTRAL -> "Neutral"
            }
        }
    }
}

/**
 * Deterministic recommendation generated for Noor based on classified aspects and sentiment.
 */
data class Recommendation(
    val aspect: String,
    val priority: Severity,
    val action: String,
    val actionVerb: String = deriveActionVerb(action),
    val localizedActionHindi: String = ""
) {
    companion object {
        fun deriveActionVerb(action: String): String {
            val clean = action.trim()
            val withoutConsider = if (clean.startsWith("Consider ", ignoreCase = true)) {
                clean.substring(9).trim()
            } else clean

            val gerundMap = mapOf(
                "improving" to "Improve",
                "providing" to "Provide",
                "reviewing" to "Review",
                "maintaining" to "Maintain",
                "keeping" to "Keep",
                "making" to "Make",
                "greeting" to "Greet",
                "continuing" to "Continue",
                "adjusting" to "Adjust",
                "explaining" to "Explain",
                "featuring" to "Feature"
            )

            val firstWord = withoutConsider.substringBefore(' ').lowercase()
            val replacement = gerundMap[firstWord]
            return if (replacement != null) {
                replacement + " " + withoutConsider.substringAfter(' ')
            } else {
                withoutConsider.replaceFirstChar { it.uppercase() }
            }
        }
    }
}

/**
 * Processing latency diagnostics across pipeline stages.
 */
data class ReviewTimings(
    val detectionMs: Long = 0L,
    val translationMs: Long = 0L,
    val classificationMs: Long = 0L,
    val recommendationMs: Long = 0L,
    val totalMs: Long = 0L
)

/**
 * High-level pipeline outcome status.
 */
enum class ReviewAnalysisStatus {
    SUCCESS,
    EMPTY,
    UNDETERMINED_LANGUAGE,
    UNSUPPORTED_LANGUAGE,
    TRANSLATION_ERROR,
    CLASSIFICATION_ERROR,
    ERROR
}

/**
 * Complete immutable analysis contract delivered to Noor's portal.
 */
data class ReviewAnalysisResult(
    val status: ReviewAnalysisStatus,
    val originalText: String,
    val sourceLanguageCode: String = "und",
    val sourceLanguageName: String = "Unknown",
    val translatedText: String = "",
    val targetLanguageCode: String = "hi",
    val classification: ReviewClassification = ReviewClassification(Sentiment.NEUTRAL),
    val recommendations: List<Recommendation> = emptyList(),
    val timings: ReviewTimings = ReviewTimings(),
    val message: String? = null,
    val errorMessage: String? = null,
    val detectionResult: LanguageDetectionResult? = null
) {
    val isSuccess: Boolean get() = status == ReviewAnalysisStatus.SUCCESS
}

/**
 * Interactive demo scenario for testing and hackathon judging presentations.
 */
data class DemoReviewScenario(
    val id: String,
    val title: String,
    val category: String,
    val visitorLanguageName: String,
    val visitorLanguageCode: String,
    val originalReview: String,
    val expectedAspects: List<String>,
    val expectedSentiment: Sentiment,
    val expectedSeverity: Severity,
    val businessContext: String
)
