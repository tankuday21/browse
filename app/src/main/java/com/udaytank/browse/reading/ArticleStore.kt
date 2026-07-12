package com.udaytank.browse.reading

import java.io.File

/**
 * Stores extracted article HTML for offline reading, one file per reading-list
 * row, under [baseDir] (production: `filesDir/reading_list`). Files hold the
 * *unthemed* extracted content; reader theming is applied at render time so
 * font/theme/width controls also work on saved articles.
 */
class ArticleStore(private val baseDir: File) {

    /** Writes the article for row [id], creating directories as needed; returns the absolute path. */
    fun save(id: Long, contentHtml: String): String {
        baseDir.mkdirs()
        val file = File(baseDir, "$id.html")
        file.writeText(contentHtml)
        return file.absolutePath
    }

    /** The saved HTML, or null if the file is missing or unreadable. */
    fun load(path: String): String? = runCatching {
        val file = File(path)
        if (file.isFile) file.readText() else null
    }.getOrNull()

    /** Best-effort removal; a no-op on null/missing paths, never throws. */
    fun delete(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }
}
