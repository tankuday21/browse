package com.udaytank.browse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitTitle

/** Opaque gate shown over incognito tabs until biometric auth succeeds. */
@Composable
fun IncognitoLockScreen(onUnlock: () -> Unit) {
    // Prompt automatically on appearance; the button is the retry path.
    LaunchedEffect(Unit) { onUnlock() }

    val scheme = orbit()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surfaces.base)
            .padding(OrbitSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = scheme.accent.solid,
            modifier = Modifier.padding(bottom = OrbitSpacing.lg),
        )
        Text("Incognito locked", style = orbitTitle, color = scheme.text.primary)
        Text(
            "Your private tabs are hidden. Verify to continue.",
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(vertical = OrbitSpacing.md),
        )
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
