package com.udaytank.browse.data

import com.udaytank.browse.browser.feed.Weather
import com.udaytank.browse.browser.feed.WeatherJson
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A resolved place for weather — from coarse location or a geocoded city name. */
data class WeatherPlace(val lat: Double, val lon: Double, val label: String)

/**
 * Weather via Open-Meteo — free, **no API key, no tracking** (fits Andromeda's privacy stance).
 * Two calls: [forecast] for conditions, [geocodeCity] to turn a typed city into coordinates.
 * Callers gate on non-incognito + feed/weather enabled.
 */
class WeatherRepository(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun forecast(lat: Double, lon: Double): Weather? = withContext(io) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current_weather=true&daily=temperature_2m_max&timezone=auto&forecast_days=4"
        get(url)?.let(WeatherJson::parse)
    }

    /** Resolve a city name to coordinates via Open-Meteo's free geocoding API. */
    suspend fun geocodeCity(name: String): WeatherPlace? = withContext(io) {
        if (name.isBlank()) return@withContext null
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json"
        val json = get(url) ?: return@withContext null
        runCatching {
            val results = JSONObject(json).optJSONArray("results") ?: return@runCatching null
            if (results.length() == 0) return@runCatching null
            val r = results.getJSONObject(0)
            val label = buildString {
                append(r.optString("name"))
                r.optString("country_code").takeIf { it.isNotBlank() }?.let { append(", $it") }
            }
            WeatherPlace(r.getDouble("latitude"), r.getDouble("longitude"), label)
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Andromeda/3.2")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 8000
    }
}
