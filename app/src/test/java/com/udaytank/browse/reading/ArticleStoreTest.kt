package com.udaytank.browse.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArticleStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(dir: File = File(tmp.root, "reading_list")) = ArticleStore(dir) to dir

    @Test
    fun `save then load round-trips unicode content`() {
        val (store, _) = store()
        val content = "<h1>Καλημέρα</h1><p>“Smart quotes” — em dash, emoji 🚀, ünïcodé</p>"
        val path = store.save(7L, content)
        assertEquals(content, store.load(path))
    }

    @Test
    fun `save returns an absolute path under baseDir and creates missing dirs`() {
        val nested = File(tmp.root, "nested/deeper/reading_list")
        assertFalse(nested.exists())
        val store = ArticleStore(nested)
        val path = store.save(1L, "<p>hi</p>")
        val file = File(path)
        assertTrue(file.isAbsolute)
        assertTrue(file.exists())
        assertEquals(nested.absolutePath, file.parentFile?.absolutePath)
        assertEquals("1.html", file.name)
    }

    @Test
    fun `save overwrites previous content for the same id`() {
        val (store, _) = store()
        val path1 = store.save(3L, "<p>first</p>")
        val path2 = store.save(3L, "<p>second</p>")
        assertEquals(path1, path2)
        assertEquals("<p>second</p>", store.load(path2))
    }

    @Test
    fun `load returns null for a missing file`() {
        val (store, dir) = store()
        assertNull(store.load(File(dir, "999.html").absolutePath))
    }

    @Test
    fun `load returns null for a directory path`() {
        val (store, dir) = store()
        store.save(1L, "x") // ensures dir exists
        assertNull(store.load(dir.absolutePath))
    }

    @Test
    fun `delete removes the file`() {
        val (store, _) = store()
        val path = store.save(5L, "<p>bye</p>")
        store.delete(path)
        assertFalse(File(path).exists())
        assertNull(store.load(path))
    }

    @Test
    fun `delete is safe on null and missing paths`() {
        val (store, dir) = store()
        store.delete(null) // must not throw
        store.delete(File(dir, "never-existed.html").absolutePath) // must not throw
    }
}
