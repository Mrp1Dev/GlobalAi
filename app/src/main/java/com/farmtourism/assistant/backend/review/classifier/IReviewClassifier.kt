package com.farmtourism.assistant.backend.review.classifier

import com.farmtourism.assistant.backend.review.model.ReviewClassification

/**
 * Interface for on-device multilingual review aspect and sentiment classification.
 */
interface IReviewClassifier {

    /**
     * Analyzes visitor review text (and optional translated Hindi text) to identify
     * expressed aspects, sentiment polarities, urgency severity, and confidence scores.
     */
    suspend fun classifyReview(
        text: String,
        translatedHindiText: String? = null
    ): Result<ReviewClassification>

    /**
     * Releases active inference sessions or native resources.
     */
    fun close() {}
}
