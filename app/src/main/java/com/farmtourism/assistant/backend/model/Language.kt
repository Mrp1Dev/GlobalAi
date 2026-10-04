package com.farmtourism.assistant.backend.model

/**
 * Supported language representations and BCP-47 mappings for Google ML Kit On-Device Translation.
 */
data class SupportedLanguage(
    val code: String,          // BCP-47 tag (e.g., "es", "hi", "en", "fr", "de")
    val displayName: String,   // User-friendly name
    val nativeName: String,    // Native tongue name
    val isRtl: Boolean = false // Right-to-left flag
)

object Languages {
    val HINDI = SupportedLanguage("hi", "Hindi", "हिन्दी")
    val ENGLISH = SupportedLanguage("en", "English", "English")
    val SPANISH = SupportedLanguage("es", "Spanish", "Español")
    val FRENCH = SupportedLanguage("fr", "French", "Français")
    val GERMAN = SupportedLanguage("de", "German", "Deutsch")
    val ITALIAN = SupportedLanguage("it", "Italian", "Italiano")
    val PORTUGUESE = SupportedLanguage("pt", "Portuguese", "Português")
    val DUTCH = SupportedLanguage("nl", "Dutch", "Nederlands")
    val RUSSIAN = SupportedLanguage("ru", "Russian", "Русский")
    val JAPANESE = SupportedLanguage("ja", "Japanese", "日本語")
    val CHINESE = SupportedLanguage("zh", "Chinese", "中文")

    val ALL: List<SupportedLanguage> = listOf(
        HINDI, ENGLISH, SPANISH, FRENCH, GERMAN,
        ITALIAN, PORTUGUESE, DUTCH, RUSSIAN, JAPANESE, CHINESE
    )

    private val codeMap = ALL.associateBy { it.code.lowercase() }

    /**
     * Resolves language by BCP-47 code. Defaults to fallback if not explicitly registered.
     */
    fun fromCode(code: String): SupportedLanguage? = codeMap[code.lowercase()]

    fun getDisplayName(code: String): String =
        fromCode(code)?.displayName ?: code.uppercase()
}
