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
     * Extensions map through [extensionToMime] (production: MimeTypeMap). If ANY entry fails to
     * map, the whole filter is abandoned (empty result → the caller opens an unfiltered picker):
     * a partial filter would grey out exactly the file type the page asked for (e.g.
     * accept=".dwg,image/png" narrowing to PNG-only when ".dwg" is unmappable).
     */
    fun normalizeAcceptTypes(acceptTypes: List<String>, extensionToMime: (String) -> String?): List<String> {
        val entries = acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val mapped = entries.mapNotNull { entry ->
            when {
                entry.contains('/') -> entry
                entry.startsWith(".") -> extensionToMime(entry.removePrefix("."))
                else -> null
            }
        }
        if (mapped.size < entries.size) return emptyList()
        return mapped.distinct()
    }

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
