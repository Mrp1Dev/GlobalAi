package com.farmtourism.assistant.backend.model

/**
 * Result of a translation operation.
 *
 * @param originalText The input text prior to translation.
 * @param translatedText The resulting translated text.
 * @param sourceLanguage The BCP-47 language tag of the source (e.g., "es", "hi").
 * @param targetLanguage The BCP-47 language tag of the target (e.g., "hi", "es").
 * @param latencyMs Execution latency in milliseconds.
 * @param isDownloadedOnDemand Whether the model had to be fetched from network before translation.
 * @param engineName The underlying engine identifier (e.g., "Google ML Kit On-Device").
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val latencyMs: Long,
    val isDownloadedOnDemand: Boolean = false,
    val engineName: String = "Google ML Kit On-Device"
)
