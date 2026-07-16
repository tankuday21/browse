package com.udaytank.browse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitDisplay

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
        // Calm, private-feeling hero: a flat tonal circle behind the lock, no borders or shadows.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(scheme.accent.solid.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = scheme.accent.solid,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            "Incognito locked",
            style = orbitDisplay,
            color = scheme.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OrbitSpacing.xl),
        )
        Text(
            "Your private tabs are hidden. Verify to continue.",
            style = orbitBody,
            color = scheme.text.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OrbitSpacing.sm, bottom = OrbitSpacing.xl),
        )
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(OrbitRadii.pill),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock", style = orbitBody) }
    }
}
