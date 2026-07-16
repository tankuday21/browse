package com.udaytank.browse.browser.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCodecTest {

    @Test
    fun `round-trips a weather with a daily forecast`() {
        val original = Weather(
            tempC = 22,
            code = 3,
            description = "Overcast",
            daily = listOf(DailyForecast("Mon", 28), DailyForecast("Tue", 30)),
        )
        val decoded = WeatherCodec.decode(WeatherCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips with an empty forecast`() {
        val original = Weather(tempC = -5, code = 71, description = "Snow", daily = emptyList())
        assertEquals(original, WeatherCodec.decode(WeatherCodec.encode(original)))
    }

    @Test
    fun `decode returns null on garbage`() {
        assertNull(WeatherCodec.decode("not json"))
        assertNull(WeatherCodec.decode("{}"))
    }
}

class WeatherJsonTest {

    private val json = """
        {
          "current_weather": { "temperature": 21.6, "weathercode": 3 },
          "daily": {
            "time": ["2026-07-14", "2026-07-15", "2026-07-16", "2026-07-17", "2026-07-18"],
            "temperature_2m_max": [28.4, 30.1, 27.9, 25.2, 24.0]
          }
        }
    """.trimIndent()

    @Test
    fun parsesCurrentAndDaily() {
        val w = WeatherJson.parse(json)!!
        assertEquals(22, w.tempC)
        assertEquals(3, w.code)
        assertTrue(w.description.isNotBlank())
        assertEquals("Overcast", w.description)
        // capped at 4
        assertEquals(4, w.daily.size)
        assertEquals("Tue", w.daily[0].dayLabel) // 2026-07-14 is a Tuesday
        assertEquals(28, w.daily[0].highC)
    }

    @Test
    fun acceptsCurrentAlias() {
        val alt = """{ "current": { "temperature": 10.2, "weathercode": 0 } }"""
        val w = WeatherJson.parse(alt)!!
        assertEquals(10, w.tempC)
        assertEquals(0, w.code)
        assertEquals("Clear", w.description)
        assertTrue(w.daily.isEmpty())
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(WeatherJson.parse("not json"))
        assertNull(WeatherJson.parse("{}"))
        assertNull(WeatherJson.parse("""{ "current_weather": { "temperature": 5.0 } }"""))
    }

    @Test
    fun describesKnownCodes() {
        assertEquals("Clear", WeatherJson.describe(0))
        assertEquals("Fog", WeatherJson.describe(45))
        assertEquals("Rain", WeatherJson.describe(63))
        assertEquals("Snow", WeatherJson.describe(73))
        assertEquals("Thunderstorm", WeatherJson.describe(95))
    }
}
