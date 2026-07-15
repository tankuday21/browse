package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.browser.feed.FeedItem
import com.udaytank.browse.browser.feed.QuickDial
import com.udaytank.browse.browser.feed.Weather
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitTitle

/** Small uppercase section header for the home feed ("Weather", "News", "Sports"). */
@Composable
fun HomeSectionLabel(text: String, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Text(
        text.uppercase(),
        style = orbitCaption.copy(letterSpacing = 2.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        color = scheme.text.muted,
        modifier = modifier.padding(start = OrbitSpacing.xs, top = OrbitSpacing.lg, bottom = OrbitSpacing.sm),
    )
}

/**
 * A site favicon inside a circle of [size], loaded source-direct from `https://<host>/favicon.ico`
 * (host derived from [url] via [UrlHosts]). Never a third-party favicon proxy — privacy. While the
 * icon loads, when the site exposes none, or when [url] has no host, it degrades to a letter avatar
 * built from [label] so the tile is never blank.
 */
@Composable
fun FaviconOrLetter(url: String, label: String, size: Dp, modifier: Modifier = Modifier) {
    val scheme = orbit()
    val host = UrlHosts.of(url)
    val letterAvatar: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(scheme.surfaces.elevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = orbitTitle, color = scheme.accent.solid)
        }
    }
    if (host.isNullOrBlank()) {
        Box(modifier = modifier) { letterAvatar() }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("https://$host/favicon.ico")
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { letterAvatar() },
        error = { letterAvatar() },
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}

/** Horizontally-scrolling row of most-visited quick dials. */
@Composable
fun QuickDialsRow(dials: List<QuickDial>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
    ) {
        dials.forEach { dial ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpen(dial.url) },
            ) {
                FaviconOrLetter(url = dial.url, label = dial.label, size = 48.dp)
                Text(
                    dial.label,
                    style = orbitCaption,
                    color = scheme.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = OrbitSpacing.xs).size(width = 56.dp, height = 16.dp),
                )
            }
        }
    }
}

/** Current-conditions + short forecast card (Open-Meteo data). */
@Composable
fun WeatherCard(weather: Weather, place: String, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Surface(
        shape = RoundedCornerShape(OrbitRadii.card),
        color = scheme.surfaces.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.text.muted.copy(alpha = 0.16f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(OrbitSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${weather.tempC}°", style = orbitDisplayNumber(), color = scheme.text.primary)
                    Text(
                        listOfNotNull(place.ifBlank { null }, weather.description).joinToString(" · "),
                        style = orbitCaption,
                        color = scheme.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = scheme.accent.gradient.getOrElse(2) { scheme.accent.solid },
                    modifier = Modifier.size(34.dp),
                )
            }
            if (weather.daily.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OrbitSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    weather.daily.take(4).forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.dayLabel, style = orbitCaption, color = scheme.text.muted)
                            Text("${day.highC}°", style = orbitBody, color = scheme.text.secondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One Chrome-style feed card: a thumbnail image (source-direct from [FeedItem.thumbnailUrl],
 * cropped and rounded) with a gradient-initial tile fallback, a title, a 1–2 line snippet from
 * [FeedItem.description] (omitted when blank), and the source · relative-time line. Tapping opens
 * the article.
 */
@Composable
fun FeedItemCard(item: FeedItem, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = orbit()
    val host = UrlHosts.of(item.link)?.removePrefix("www.")
    Surface(
        shape = RoundedCornerShape(OrbitRadii.card),
        color = scheme.surfaces.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.text.muted.copy(alpha = 0.16f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(item.link) },
    ) {
        Row(
            modifier = Modifier.padding(OrbitSpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
        ) {
            FeedThumbnail(url = item.thumbnailUrl, fallbackLabel = host ?: item.title)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = orbitBody.copy(fontWeight = FontWeight.Medium),
                    color = scheme.text.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = orbitCaption,
                        color = scheme.text.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    listOfNotNull(host, relativeTime(item.publishedAt)).joinToString(" · "),
                    style = orbitCaption,
                    color = scheme.text.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The feed card's leading visual: an ~80dp thumbnail cropped and rounded to [OrbitRadii.chip],
 * loaded source-direct from [url]. Falls back to a gradient tile with [fallbackLabel]'s initial
 * while loading, when [url] is null/blank, or when the image fails to load.
 */
@Composable
private fun FeedThumbnail(url: String?, fallbackLabel: String) {
    val scheme = orbit()
    val shape = RoundedCornerShape(OrbitRadii.chip)
    val tile: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(shape)
                .background(Brush.linearGradient(scheme.accent.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(fallbackLabel.take(1).uppercase(), style = orbitTitle, color = Color.White)
        }
    }
    if (url.isNullOrBlank()) {
        tile()
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { tile() },
        error = { tile() },
        modifier = Modifier
            .size(80.dp)
            .clip(shape),
    )
}

/** A big tabular number style for the weather temperature. */
@Composable
private fun orbitDisplayNumber() = orbitTitle.copy(
    fontSize = 30.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
)

/** Compact relative time ("3h ago"); null when the source gave no usable date. */
private fun relativeTime(publishedAt: Long): String? {
    if (publishedAt <= 0L) return null
    val diff = System.currentTimeMillis() - publishedAt
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> "${diff / 86_400_000L}d ago"
    }
}
