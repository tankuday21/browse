package com.udaytank.browse.ui

import androidx.compose.runtime.Composable
import com.udaytank.browse.ui.components.LockGate

/** Opaque gate shown over incognito tabs until biometric auth succeeds (layout: [LockGate]). */
@Composable
fun IncognitoLockScreen(onUnlock: () -> Unit) {
    LockGate(
        title = "Incognito locked",
        subtitle = "Your private tabs are hidden. Verify to continue.",
        onUnlock = onUnlock,
    )
}
