package com.farmtourism.assistant.backend.model

/**
 * Result of on-device intent classification from mmBERT.
 *
 * @property intent Identified intent code (e.g., "price_tour", "visitation_hours", "out_of_scope")
 * @property confidence Prediction confidence score (0.0 to 1.0)
 * @property isConfident True if confidence meets or exceeds the required threshold tau (default: 0.70)
 * @property latencyMs End-to-end inference latency in milliseconds
 */
data class IntentClassificationResult(
    val intent: String,
    val confidence: Float,
    val isConfident: Boolean,
    val latencyMs: Long = 0L
)
