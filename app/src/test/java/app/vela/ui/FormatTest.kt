package app.vela.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FormatTest {

    private var originalLang: String = ""
    private var originalImperial: Boolean = false

    @Before
    fun setUp() {
        originalLang = AppLocale.language.value
        originalImperial = Units.imperial.value
        Units.imperial.value = false
    }

    @After
    fun tearDown() {
        AppLocale.language.value = originalLang
        Units.imperial.value = originalImperial
    }

    @Test
    fun testFormatDistanceArabic() {
        AppLocale.language.value = "ar"
        assertEquals("500 م", formatDistance(500.0))
        assertEquals("50 م", formatDistance(50.0))
        assertTrue(formatDistance(1500.0).contains("كم"))
        assertTrue(formatDistance(1500.0).contains("1.5") || formatDistance(1500.0).contains("١٫٥") || formatDistance(1500.0).contains("1,5"))
    }

    @Test
    fun testFormatDistanceEnglish() {
        AppLocale.language.value = "en"
        assertEquals("500 m", formatDistance(500.0))
        assertEquals("1.5 km", formatDistance(1500.0))
    }

    @Test
    fun testFormatSpeedArabic() {
        AppLocale.language.value = "ar"
        val (speed, unit) = formatSpeed(25f) // 25 * 3.6 = 90 km/h
        assertEquals(90, speed)
        assertEquals("كم/س", unit)

        val (limit, limitUnit) = formatSpeedLimit(100.0)
        assertEquals(100, limit)
        assertEquals("كم/س", limitUnit)
    }

    @Test
    fun testFormatSpeedEnglish() {
        AppLocale.language.value = "en"
        val (speed, unit) = formatSpeed(25f)
        assertEquals(90, speed)
        assertEquals("km/h", unit)

        val (limit, limitUnit) = formatSpeedLimit(100.0)
        assertEquals(100, limit)
        assertEquals("km/h", limitUnit)
    }

    @Test
    fun testFormatAreaArabic() {
        AppLocale.language.value = "ar"
        assertEquals("5000 م²", formatArea(5000.0))
        assertTrue(formatArea(50_000.0).contains("هكتار"))
        assertTrue(formatArea(2_000_000.0).contains("كم²"))
    }
}
