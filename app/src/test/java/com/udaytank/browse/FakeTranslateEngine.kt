package com.udaytank.browse

import com.udaytank.browse.translate.TranslateEngine

/**
 * Scriptable fake ML Kit engine for VM flow tests: set the detected language, whether the model
 * download succeeds, and how a string is "translated" (default: prefix with the target code).
 */
class FakeTranslateEngine(
    var detected: String? = "es",
    var modelResult: Result<Unit> = Result.success(Unit),
    var translate: (String, String) -> String = { target, text -> "[$target]$text" },
) : TranslateEngine {
    var ensureCalls = 0
        private set

    /** The requireWifi flag from the most recent ensureModel call (v6.4). */
    var lastRequireWifi: Boolean? = null
        private set

    override suspend fun detect(sample: String): String? = detected

    override suspend fun ensureModel(source: String, target: String, requireWifi: Boolean): Result<Unit> {
        ensureCalls++
        lastRequireWifi = requireWifi
        return modelResult
    }

    override suspend fun translateAll(source: String, target: String, texts: List<String>): List<String> =
        texts.map { translate(target, it) }
}
