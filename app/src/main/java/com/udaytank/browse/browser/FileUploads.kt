package com.udaytank.browse.browser

/**
 * Pure helpers for HTML `<input type="file">` uploads (v4.8). Free of android.* types so the
 * accept-type normalization and picker-result parsing are unit-testable on the JVM; the Activity
 * layer supplies the platform pieces (MimeTypeMap, Intent, Uri).
 */
object FileUploads {

    /**
     * Normalizes an HTML `accept` list into content-picker MIME types. Entries arrive as MIME
     * types ("application/pdf", or wildcard image types) or dot-extensions (".jpg"); pages sometimes hand the
     * whole comma-joined attribute as a single array element, so entries are re-split on commas.
     * Extensions map through [extensionToMime] (production: MimeTypeMap) and unknown ones are
     * dropped — an unmappable filter must not silently exclude the user's file.
     */
    fun normalizeAcceptTypes(acceptTypes: List<String>, extensionToMime: (String) -> String?): List<String> =
        acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                when {
                    entry.contains('/') -> entry
                    entry.startsWith(".") -> extensionToMime(entry.removePrefix("."))
                    else -> null
                }
            }
            .distinct()

    /**
     * Resolves a system-picker result into the URIs to hand back to the page, or null for
     * "no file chosen" (the WebView callback still needs that exactly-once null on cancel).
     * Multi-select results (ClipData) win over the single-URI field. Generic over the URI type
     * so JVM tests don't need android.net.Uri.
     */
    fun <T : Any> parseChooserResult(ok: Boolean, single: T?, clip: List<T>): List<T>? = when {
        !ok -> null
        clip.isNotEmpty() -> clip
        single != null -> listOf(single)
        else -> null
    }
}
