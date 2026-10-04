package com.farmtourism.assistant.backend.engine

import com.farmtourism.assistant.backend.model.IntentClassificationResult

/**
 * Interface for on-device multilingual intent classification.
 * Implementations evaluate tourist queries and assign categorical farm intents with confidence scores.
 */
interface IIntentClassifier {

    /**
     * Confidence threshold (tau) required to route into Tier 1 / Tier 2.
     * Inquiries scoring below this threshold fall back to Tier 3 direct translation.
     */
    val confidenceThreshold: Float

    /**
     * Classifies the intent of an incoming natural language text query.
     */
    suspend fun classify(text: String): Result<IntentClassificationResult>

    /**
     * Releases active native resources or inference sessions.
     */
    fun close()
}
