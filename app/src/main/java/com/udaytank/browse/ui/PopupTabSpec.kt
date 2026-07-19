package com.udaytank.browse.ui

/**
 * What a popup tab is born as (v5.6): its PRE-ALLOCATED id plus the parent-derived isolation
 * (incognito parent → incognito popup; normal parent → the parent Orbit's profile). Returned
 * synchronously from Listener.onCreatePopup so the WebView can be created under its real tab
 * id inside the engine's onCreateWindow callback.
 */
data class PopupTabSpec(
    val tabId: Long,
    val incognito: Boolean,
    val profileKey: String?,
)
