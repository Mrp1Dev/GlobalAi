package com.farmtourism.assistant.backend.model

/**
 * Metadata and prompt templates for a specific farm tourism intent.
 * Used by Tier 1 for response slot-filling and Tier 2 for asking Noor missing values.
 *
 * Supports both single default templates and rich arrays of predetermined replies
 * and Noor request prompts across multiple styles, tones, and languages.
 */
data class IntentTemplate(
    val intent: String,
    val slotKey: String,
    val description: String,
    val noorPromptTemplates: Map<String, String>,
    val defaultReplyTemplate: String,
    val sampleSlotValue: String = "",
    val replyTemplateVariations: List<String> = listOf(defaultReplyTemplate),
    val noorPromptVariations: Map<String, List<String>> = emptyMap()
) {
    /**
     * Retrieves the localized question to display to Noor in her configured language.
     * Optionally selects a specific variation index or defaults to the primary prompt.
     */
    fun getNoorPrompt(languageCode: String, variationIndex: Int? = null): String {
        val list = noorPromptVariations[languageCode] ?: noorPromptVariations["hi"]
        if (!list.isNullOrEmpty() && variationIndex != null && variationIndex in list.indices) {
            return list[variationIndex]
        }
        return noorPromptTemplates[languageCode]
            ?: noorPromptTemplates["hi"]
            ?: (list?.firstOrNull())
            ?: "Please enter $slotKey:"
    }

    /**
     * Returns all predetermined prompt questions for Noor in the requested language.
     */
    fun getAllNoorPrompts(languageCode: String): List<String> {
        val list = noorPromptVariations[languageCode] ?: noorPromptVariations["hi"]
        if (!list.isNullOrEmpty()) {
            return list
        }
        val defaultVal = noorPromptTemplates[languageCode] ?: noorPromptTemplates["hi"]
        return if (defaultVal != null) listOf(defaultVal) else emptyList()
    }

    /**
     * Injects the database/farmer value into a reply template.
     * Optionally selects a specific predetermined variation index.
     */
    fun fillTemplate(slotValue: String, variationIndex: Int? = null): String {
        val template = if (variationIndex != null && variationIndex in replyTemplateVariations.indices) {
            replyTemplateVariations[variationIndex]
        } else {
            defaultReplyTemplate
        }
        return template.replace("{$slotKey}", slotValue)
    }

    /**
     * Returns all predetermined reply template strings for this intent.
     */
    fun getAllReplyTemplates(): List<String> {
        return if (replyTemplateVariations.isNotEmpty()) replyTemplateVariations else listOf(defaultReplyTemplate)
    }
}
