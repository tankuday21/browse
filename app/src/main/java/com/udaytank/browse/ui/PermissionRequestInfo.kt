package com.udaytank.browse.ui

/**
 * A site's request for a device permission (camera, mic, location), surfaced
 * to the UI so the user can allow or deny. [grant]/[deny] resume the page.
 */
data class PermissionRequestInfo(
    val host: String,
    val label: String,
    val grant: () -> Unit,
    val deny: () -> Unit,
)
