package com.farmtourism.assistant.backend.mlkit

import com.farmtourism.assistant.backend.engine.ITranslationEngine
import com.farmtourism.assistant.backend.model.TranslationResult
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Google ML Kit On-Device Translation Engine.
 *
 * Provides offline-first, sub-100ms latency translation by:
 * 1. Maintaining an active pool of cached Translator instances per (source, target) pair.
 * 2. Pre-checking and downloading on-device translation model packs (~30MB/pack).
 * 3. Executing translation completely on the client Android device with zero server token cost.
 */
class MlKitTranslationEngine : ITranslationEngine {

    private val modelManager = RemoteModelManager.getInstance()
    private val translatorPool = ConcurrentHashMap<String, Translator>()

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()

            if (text.isBlank()) {
                return@runCatching TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    latencyMs = 0L,
                    isDownloadedOnDemand = false
                )
            }

            // Normalise language tags
            val sourceTag = normalizeLanguageTag(sourceLanguage)
            val targetTag = normalizeLanguageTag(targetLanguage)

            // If source and target are the same language, no translation needed
            if (sourceTag.equals(targetTag, ignoreCase = true)) {
                return@runCatching TranslationResult(
                    originalText = text,
                    translatedText = text,
                    sourceLanguage = sourceTag,
                    targetLanguage = targetTag,
                    latencyMs = System.currentTimeMillis() - startTime,
                    isDownloadedOnDemand = false
                )
            }

            val sourceMlKitLang = TranslateLanguage.fromLanguageTag(sourceTag)
                ?: throw IllegalArgumentException("Unsupported source language: $sourceLanguage")
            val targetMlKitLang = TranslateLanguage.fromLanguageTag(targetTag)
                ?: throw IllegalArgumentException("Unsupported target language: $targetLanguage")

            val translator = getOrCreateTranslator(sourceMlKitLang, targetMlKitLang)

            // Check if models need downloading
            val conditions = DownloadConditions.Builder().build()
            val wasSourceDownloaded = isModelDownloaded(sourceTag)
            val wasTargetDownloaded = isModelDownloaded(targetTag)
            val needsDownload = !wasSourceDownloaded || !wasTargetDownloaded

            // Ensures models are ready
            translator.downloadModelIfNeeded(conditions).await()

            // Perform translation on device
            val translated = translator.translate(text).await()
            val duration = System.currentTimeMillis() - startTime

            TranslationResult(
                originalText = text,
                translatedText = translated,
                sourceLanguage = sourceTag,
                targetLanguage = targetTag,
                latencyMs = duration,
                isDownloadedOnDemand = needsDownload,
                engineName = "Google ML Kit On-Device"
            )
        }
    }

    override suspend fun isModelDownloaded(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        val tag = normalizeLanguageTag(languageCode)
        val mlKitLang = TranslateLanguage.fromLanguageTag(tag) ?: return@withContext false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        runCatching {
            modelManager.isModelDownloaded(model).await()
        }.getOrDefault(false)
    }

    override suspend fun downloadModel(
        languageCode: String,
        requireWifi: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tag = normalizeLanguageTag(languageCode)
            val mlKitLang = TranslateLanguage.fromLanguageTag(tag)
                ?: throw IllegalArgumentException("Unsupported language for download: $languageCode")
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            modelManager.download(model, conditionsBuilder.build()).await()
            Unit
        }
    }

    override suspend fun getDownloadedModels(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            models.map { it.language }
        }.getOrDefault(emptyList())
    }

    override suspend fun deleteModel(languageCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tag = normalizeLanguageTag(languageCode)
            val mlKitLang = TranslateLanguage.fromLanguageTag(tag)
                ?: throw IllegalArgumentException("Unsupported language for deletion: $languageCode")
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            modelManager.deleteDownloadedModel(model).await()

            // Invalidate any translators in the pool that rely on this language
            val keysToRemove = translatorPool.keys.filter { key ->
                key.startsWith("${tag}_") || key.endsWith("_$tag")
            }
            keysToRemove.forEach { key ->
                translatorPool.remove(key)?.close()
            }
            Unit
        }
    }

    override fun close() {
        translatorPool.values.forEach { translator ->
            runCatching { translator.close() }
        }
        translatorPool.clear()
    }

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "${sourceLang}_$targetLang"
        return translatorPool.computeIfAbsent(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    private fun normalizeLanguageTag(tag: String): String {
        return tag.trim().lowercase().split("-", "_")[0]
    }
}
