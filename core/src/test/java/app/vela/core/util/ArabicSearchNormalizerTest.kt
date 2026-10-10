package app.vela.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSearchNormalizerTest {

    @Test
    fun `strips diacritics and tatweel`() {
        val raw = "بَغْــــدَادُ"
        assertEquals("بغداد", ArabicSearchNormalizer.normalize(raw))
    }

    @Test
    fun `normalizes alef variants and alef maqsura`() {
        val input = "أحمد وإبراهيم في مستشفى"
        val expected = "احمد وابراهيم في مستشفي"
        assertEquals(expected, ArabicSearchNormalizer.normalize(input))
    }

    @Test
    fun `converts eastern arabic numerals to western digits`() {
        val input = "شارع ١٤ رمضان"
        val expected = "شارع 14 رمضان"
        assertEquals(expected, ArabicSearchNormalizer.normalize(input))
    }

    @Test
    fun `fuzzy matches with definite article variations`() {
        assertTrue(ArabicSearchNormalizer.matches("الكرادة", "كرادة داخل"))
        assertTrue(ArabicSearchNormalizer.matches("كرادة", "شارع الكرادة خارج"))
        assertTrue(ArabicSearchNormalizer.matches("منصور", "حي المنصور"))
    }

    @Test
    fun `fuzzy matches with spelling typos within tolerance`() {
        // Single typo in Arabic word
        assertTrue(ArabicSearchNormalizer.matches("الجادرية", "الجادريه"))
        assertTrue(ArabicSearchNormalizer.matches("السليمانية", "السليمانيه"))
        assertTrue(ArabicSearchNormalizer.matches("شارع الرشيد", "شارع رشيد"))
    }

    @Test
    fun `returns false for completely different queries`() {
        assertFalse(ArabicSearchNormalizer.matches("البصرة", "أربيل"))
        assertFalse(ArabicSearchNormalizer.matches("النجف", "كربلاء"))
    }
}
