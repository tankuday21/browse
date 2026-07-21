package com.udaytank.browse.browser

/** Pure logic for sharing multiple downloads at once (v6.0). */
object ShareBundle {

    /**
     * The MIME type to tag an `ACTION_SEND_MULTIPLE` intent with, given each selected file's
     * declared mime. Receivers filter by this, so we pick the most specific type that still
     * covers every file:
     *  - all identical      : that exact type (an image png stays an image png)
     *  - same top-level     : the top-level wildcard (png plus jpeg becomes the image wildcard)
     *  - mixed top-levels   : the full wildcard
     *  - empty, or ANY entry null, blank, or malformed (not exactly one slash) : the full
     *    wildcard (fail wide, never under-declare in a way that hides files from a willing target).
     */
    fun commonMimeType(mimes: List<String?>): String {
        if (mimes.isEmpty()) return ANY
        val cleaned = mimes.map { it?.trim().orEmpty() }
        if (cleaned.any { it.count { c -> c == '/' } != 1 }) return ANY
        if (cleaned.distinct().size == 1) return cleaned.first()
        val topLevels = cleaned.map { it.substringBefore('/') }.distinct()
        return if (topLevels.size == 1) "${topLevels.first()}/*" else ANY
    }

    private const val ANY = "*/*"
}
