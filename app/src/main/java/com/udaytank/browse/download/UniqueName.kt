package com.udaytank.browse.download

/** Pure helper for resolving filesystem-safe, collision-free download filenames. */
object UniqueName {

    private const val MAX_ATTEMPTS = 999

    /**
     * Returns [fileName] unchanged if [taken] reports it's free. Otherwise tries
     * "name (1).ext", "name (2).ext", ... up to [MAX_ATTEMPTS], returning the first
     * candidate [taken] reports free. If all of those are taken too, falls back to
     * appending the current time in millis so the result is still unique.
     *
     * Extension handling: the extension is everything after the LAST '.' in [fileName]
     * (matching how most file managers, e.g. Windows Explorer, treat "duplicate" names).
     * So a multi-dot name like "archive.tar.gz" becomes "archive.tar (1).gz", not
     * "archive (1).tar.gz". A name with no dot (or a leading-dot dotfile with nothing
     * before it, e.g. ".gitignore") is treated as having no extension.
     */
    fun resolve(fileName: String, taken: (String) -> Boolean): String {
        if (!taken(fileName)) return fileName

        val dotIndex = fileName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < fileName.length - 1
        val base = if (hasExtension) fileName.substring(0, dotIndex) else fileName
        val extension = if (hasExtension) fileName.substring(dotIndex) else ""

        for (n in 1..MAX_ATTEMPTS) {
            val candidate = "$base ($n)$extension"
            if (!taken(candidate)) return candidate
        }

        // Exhausted 1..999 (pathological, but must never collide): fall back to a
        // timestamp, which is unique for all practical purposes.
        return "$base (${System.currentTimeMillis()})$extension"
    }
}
