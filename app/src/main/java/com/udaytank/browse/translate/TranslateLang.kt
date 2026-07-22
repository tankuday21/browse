package com.udaytank.browse.translate

/**
 * Pure language metadata for on-device translation (v6.1). ML Kit identifies and translates by
 * lowercase ISO-639-1 codes; this maps the supported set to human names and resolves the default
 * target from the device language. No Android dependencies so it unit-tests on the JVM.
 */
object TranslateLang {

    /** The ML Kit-supported languages we expose, code → display name (a curated common subset). */
    val SUPPORTED: Map<String, String> = linkedMapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "ru" to "Russian",
        "hi" to "Hindi", "bn" to "Bengali", "gu" to "Gujarati", "mr" to "Marathi",
        "ta" to "Tamil", "te" to "Telugu", "kn" to "Kannada", "ur" to "Urdu",
        "ar" to "Arabic", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
        "tr" to "Turkish", "vi" to "Vietnamese", "th" to "Thai", "id" to "Indonesian",
        "pl" to "Polish", "uk" to "Ukrainian", "fa" to "Persian", "he" to "Hebrew",
        "sv" to "Swedish", "cs" to "Czech", "el" to "Greek", "ro" to "Romanian",
    )

    const val DEFAULT_TARGET = "en"

    /** Normalizes a locale/BCP-47 tag to a bare lowercase language code ("en-US" → "en"). */
    fun normalize(tag: String?): String =
        tag.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')

    fun isSupported(code: String?): Boolean = SUPPORTED.containsKey(normalize(code))

    fun displayName(code: String?): String {
        val norm = normalize(code)
        return SUPPORTED[norm] ?: norm.ifBlank { "Unknown" }
    }

    /** The device language if we can translate into it, else English. */
    fun defaultTarget(deviceTag: String?): String {
        val norm = normalize(deviceTag)
        return if (SUPPORTED.containsKey(norm)) norm else DEFAULT_TARGET
    }

    /**
     * Whether a page in [source] is worth translating to [target]: only when both are known,
     * supported, and different. An undetected/"und" source or a page already in the target is a
     * no-op (the caller tells the user rather than round-tripping identical text).
     */
    fun needsTranslation(source: String?, target: String?): Boolean {
        val s = normalize(source)
        val t = normalize(target)
        return s.isNotBlank() && s != "und" && isSupported(s) && isSupported(t) && s != t
    }
}
