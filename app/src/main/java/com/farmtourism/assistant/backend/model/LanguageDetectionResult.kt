package com.farmtourism.assistant.backend.model

/**
 * Result of Google ML Kit on-device language identification.
 *
 * @param languageCode Detected BCP-47 language tag (e.g., "es", "fr", "de"), or "und" if undetermined.
 * @param confidence Confidence score between 0.0 and 1.0.
 * @param isReliable True if confidence is above the identification threshold and not undetermined.
 */
data class LanguageDetectionResult(
    val languageCode: String,
    val confidence: Float,
    val isReliable: Boolean
) {
    val isUndetermined: Boolean
        get() = languageCode.equals("und", ignoreCase = true) || languageCode.isBlank()
}
