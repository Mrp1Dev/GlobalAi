package com.farmtourism.assistant.backend.engine

import com.farmtourism.assistant.backend.model.LanguageDetectionResult

/**
 * Interface contract for on-device language identification.
 */
interface ILanguageIdentifier {

    /**
     * Identifies the primary language tag (BCP-47) for the given input text.
     */
    suspend fun identifyLanguage(text: String): Result<LanguageDetectionResult>

    /**
     * Returns candidate languages with their respective confidence scores.
     */
    suspend fun identifyPossibleLanguages(text: String): Result<List<LanguageDetectionResult>>

    /**
     * Releases active language identifier resources.
     */
    fun close()
}
