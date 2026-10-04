package com.farmtourism.assistant.backend.engine

import com.farmtourism.assistant.backend.model.TranslationResult

/**
 * Interface contract for translation engines (Google ML Kit on Android, or mock/online in tests).
 */
interface ITranslationEngine {

    /**
     * Translates input text from source language to target language.
     * Automatically ensures required language models are downloaded.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<TranslationResult>

    /**
     * Checks whether the language model pack for the specified language is downloaded on-device.
     */
    suspend fun isModelDownloaded(languageCode: String): Boolean

    /**
     * Downloads the on-device language model pack.
     *
     * @param languageCode BCP-47 language tag (e.g., "es", "hi", "fr")
     * @param requireWifi If true, download only occurs over Wi-Fi
     */
    suspend fun downloadModel(languageCode: String, requireWifi: Boolean = false): Result<Unit>

    /**
     * Lists all BCP-47 language codes currently downloaded on-device.
     */
    suspend fun getDownloadedModels(): List<String>

    /**
     * Deletes a downloaded language model pack to free up device storage.
     */
    suspend fun deleteModel(languageCode: String): Result<Unit>

    /**
     * Releases active translators and memory resources.
     */
    fun close()
}
