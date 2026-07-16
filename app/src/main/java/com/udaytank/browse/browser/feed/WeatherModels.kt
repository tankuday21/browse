package com.udaytank.browse.browser.feed

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One day's high in a short forecast strip. dayLabel is a 3-letter weekday (e.g. "Mon"). */
data class DailyForecast(val dayLabel: String, val highC: Int)

/** Current conditions plus a short daily forecast, mapped from an Open-Meteo response. */
data class Weather(
    val tempC: Int,
    val code: Int,
    val description: String,
    val daily: List<DailyForecast>,
)

/**
 * Pure JSON (de)serialization of a resolved [Weather] for the offline cache (v4.7). Lets the home
 * widget show the last-known conditions immediately on launch and keep showing them when a refresh
 * fails (no network) instead of blanking out. Kept separate from [WeatherJson] (which maps the
 * Open-Meteo API shape) and free of any Android/network dependency so it's trivially testable.
 */
object WeatherCodec {
    fun encode(w: Weather): String = JSONObject().apply {
        put("t", w.tempC)
        put("c", w.code)
        put("d", w.description)
        put(
            "days",
            JSONArray().apply {
                w.daily.forEach { put(JSONObject().put("l", it.dayLabel).put("h", it.highC)) }
            },
        )
    }.toString()

    fun decode(json: String): Weather? = runCatching {
        val o = JSONObject(json)
        val daysArr = o.optJSONArray("days") ?: JSONArray()
        val days = (0 until daysArr.length()).map {
            val d = daysArr.getJSONObject(it)
            DailyForecast(d.getString("l"), d.getInt("h"))
        }
        Weather(
            tempC = o.getInt("t"),
            code = o.getInt("c"),
            description = o.getString("d"),
            daily = days,
        )
    }.getOrNull()
}

/** Pure JSON mapping for Open-Meteo forecast responses (no network). */
object WeatherJson {

    /**
     * Map an Open-Meteo forecast response to [Weather]; null on malformed input.
     * Expects `current` (or `current_weather`) with temperature + weathercode, and optional
     * `daily` with `time` + `temperature_2m_max`. Round temps to Int.
     */
    fun parse(json: String): Weather? = runCatching {
        val root = JSONObject(json)
        val current = root.optJSONObject("current_weather")
            ?: root.optJSONObject("current")
            ?: return null

        if (!current.has("temperature") || !current.has("weathercode")) return null
        val tempC = current.getDouble("temperature").roundToInt()
        val code = current.getInt("weathercode")

        Weather(
            tempC = tempC,
            code = code,
            description = describe(code),
            daily = parseDaily(root.optJSONObject("daily")),
        )
    }.getOrNull()

    private fun parseDaily(daily: JSONObject?): List<DailyForecast> {
        daily ?: return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val highs = daily.optJSONArray("temperature_2m_max") ?: return emptyList()

        val count = minOf(times.length(), highs.length(), 4)
        val out = ArrayList<DailyForecast>(count)
        for (i in 0 until count) {
            val label = dayLabel(times.optString(i)) ?: continue
            val high = highs.optDouble(i, Double.NaN)
            if (high.isNaN()) continue
            out.add(DailyForecast(label, high.roundToInt()))
        }
        return out
    }

    private fun dayLabel(isoDate: String?): String? {
        if (isoDate.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(isoDate).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        }.getOrNull()
    }

    /** Human description for an Open-Meteo WMO weather code (0 Clear, 1-3 clouds, 45/48 fog, 51-67 rain, 71-77 snow, 80-82 showers, 95-99 thunderstorm). */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }
}
