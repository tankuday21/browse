package com.udaytank.browse.browser

import java.util.Locale

/**
 * Formats the lifetime blocked-request count for the home page's privacy stats block (C3).
 * Pure so it's unit-testable; the caller hides the block entirely when the count is zero.
 */
object PrivacyStatsFormat {

    /** Estimated payload of an average blocked ad/tracker request. */
    private const val BYTES_PER_BLOCK = 50L * 1024 // 50 KB

    private const val MB = 1024.0 * 1024
    private const val GB = MB * 1024

    /**
     * blocked -> ("1,234 ads & trackers blocked", "~60.3 MB saved (estimated)").
     * MB with one decimal below a gigabyte, GB with two above; US-style thousands separators.
     */
    fun format(blocked: Long): Pair<String, String> {
        val count = String.format(Locale.US, "%,d", blocked)
        val bytes = blocked * BYTES_PER_BLOCK
        val size = if (bytes >= GB) {
            String.format(Locale.US, "%,.2f GB", bytes / GB)
        } else {
            String.format(Locale.US, "%.1f MB", bytes / MB)
        }
        return "$count ads & trackers blocked" to "~$size saved (estimated)"
    }
}
