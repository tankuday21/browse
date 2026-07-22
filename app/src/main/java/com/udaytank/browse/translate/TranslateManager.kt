package com.udaytank.browse.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation via ML Kit (v6.1). The interface keeps [BrowserViewModel] testable with a
 * fake; only [TranslateManager] touches ML Kit. All text stays on the device — the sole network
 * touch is ML Kit's one-time per-language model download.
 */
interface TranslateEngine {
    /** BCP-47 language code of [sample], or null when undetermined ("und"). */
    suspend fun detect(sample: String): String?

    /** Ensures the source↔target model is present (downloading if needed). */
    suspend fun ensureModel(source: String, target: String, requireWifi: Boolean): Result<Unit>

    /** Translates each string; a per-item failure falls back to the original (never drops a node). */
    suspend fun translateAll(source: String, target: String, texts: List<String>): List<String>
}

class TranslateManager : TranslateEngine {

    override suspend fun detect(sample: String): String? {
        if (sample.isBlank()) return null
        val client = LanguageIdentification.getClient()
        return try {
            val code = client.identifyLanguage(sample).await()
            code.takeUnless { it == "und" }
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    override suspend fun ensureModel(source: String, target: String, requireWifi: Boolean): Result<Unit> {
        val translator = translatorFor(source, target)
            ?: return Result.failure(IllegalArgumentException("Unsupported language pair"))
        return try {
            val conditions = DownloadConditions.Builder().apply { if (requireWifi) requireWifi() }.build()
            translator.downloadModelIfNeeded(conditions).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            translator.close()
        }
    }

    override suspend fun translateAll(source: String, target: String, texts: List<String>): List<String> {
        val translator = translatorFor(source, target) ?: return texts
        return try {
            texts.map { original ->
                if (original.isBlank()) original
                else runCatching { translator.translate(original).await() }.getOrDefault(original)
            }
        } finally {
            translator.close()
        }
    }

    private fun translatorFor(source: String, target: String): Translator? {
        val s = TranslateLanguage.fromLanguageTag(source) ?: return null
        val t = TranslateLanguage.fromLanguageTag(target) ?: return null
        return Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build()
        )
    }

    /** Bridges a GMS [Task] to a coroutine without pulling in kotlinx-coroutines-play-services. */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
