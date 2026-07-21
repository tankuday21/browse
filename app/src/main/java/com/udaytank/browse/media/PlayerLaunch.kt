package com.udaytank.browse.media

/** One playable item for the Andromeda Player (v6.0). [filePath] doubles as the media id. */
data class PlayerItem(val filePath: String, val title: String, val mimeType: String?)

/**
 * Everything the player screen needs to start: the ordered queue, which item to open on
 * ([startIndex]), and where to resume the start item ([startPositionMs], 0 = from the top).
 */
data class PlayerQueue(
    val items: List<PlayerItem>,
    val startIndex: Int,
    val startPositionMs: Long,
)
