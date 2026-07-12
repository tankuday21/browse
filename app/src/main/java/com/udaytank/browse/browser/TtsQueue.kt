package com.udaytank.browse.browser

/**
 * Podcast-mode play order over the reading list: a fixed snapshot of items
 * (unread, oldest first) with a cursor. [current] is null once the queue is
 * exhausted; [next] advances the cursor and returns the new [current].
 */
class TtsQueue(items: List<Item>) {

    data class Item(val id: Long, val title: String)

    private val items = items.toList()
    private var index = 0

    val current: Item?
        get() = items.getOrNull(index)

    val hasNext: Boolean
        get() = index + 1 < items.size

    fun next(): Item? {
        if (index < items.size) index++
        return current
    }
}
