package com.udaytank.browse.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * Page thumbnails for the tab switcher. Memory LRU + disk cache;
 * incognito tabs (negative ids) are memory-only — nothing written to disk.
 */
class ThumbnailStore(context: Context) {

    private val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    private val memory = LruCache<Long, Bitmap>(8)

    fun capture(tabId: Long, view: View) {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return
        runCatching {
            val full = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            view.draw(Canvas(full))
            val targetW = 540
            val targetH = (height * (targetW.toFloat() / width)).toInt()
            val scaled = Bitmap.createScaledBitmap(full, targetW, targetH, true)
            full.recycle()
            memory.put(tabId, scaled)
            if (tabId >= 0) {
                FileOutputStream(fileFor(tabId)).use {
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
            }
        }
    }

    fun load(tabId: Long): Bitmap? {
        memory.get(tabId)?.let { return it }
        if (tabId < 0) return null
        return runCatching {
            fileFor(tabId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }.getOrNull()?.also { memory.put(tabId, it) }
    }

    fun remove(tabId: Long) {
        memory.remove(tabId)
        if (tabId >= 0) fileFor(tabId).delete()
    }

    private fun fileFor(tabId: Long) = File(dir, "$tabId.jpg")
}
