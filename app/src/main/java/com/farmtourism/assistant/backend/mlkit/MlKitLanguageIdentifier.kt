package com.farmtourism.assistant.backend.mlkit

import com.farmtourism.assistant.backend.engine.ILanguageIdentifier
import com.farmtourism.assistant.backend.model.LanguageDetectionResult
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device language identification implementation utilizing Google ML Kit Language ID SDK.
 *
 * Runs locally on the tourist's device in <5ms to detect tourist's language (Spanish, French, German, etc.)
 * with zero manual configuration.
 *
 * @param confidenceThreshold Minimum confidence threshold (0.0 to 1.0) to consider a language identification reliable.
 */
class MlKitLanguageIdentifier(
    private val confidenceThreshold: Float = 0.5f
) : ILanguageIdentifier {

    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(confidenceThreshold)
            .build()
    )

    override suspend fun identifyLanguage(text: String): Result<LanguageDetectionResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) {
                return@runCatching LanguageDetectionResult(
                    languageCode = "und",
                    confidence = 0.0f,
                    isReliable = false
                )
            }

            val langCode = identifier.identifyLanguage(text).await()
            val isReliable = !langCode.equals("und", ignoreCase = true)

            LanguageDetectionResult(
                languageCode = langCode,
                confidence = if (isReliable) 0.95f else 0.0f,
                isReliable = isReliable
            )
        }
    }

    override suspend fun identifyPossibleLanguages(text: String): Result<List<LanguageDetectionResult>> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) {
                return@runCatching emptyList()
            }

            val candidates = identifier.identifyPossibleLanguages(text).await()
            candidates.map { candidate ->
                LanguageDetectionResult(
                    languageCode = candidate.languageTag,
                    confidence = candidate.confidence,
                    isReliable = candidate.confidence >= confidenceThreshold && !candidate.languageTag.equals("und", ignoreCase = true)
                )
            }
        }
    }

    override fun close() {
        runCatching { identifier.close() }
    }
}
