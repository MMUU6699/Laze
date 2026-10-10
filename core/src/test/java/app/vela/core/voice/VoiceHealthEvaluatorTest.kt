package app.vela.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceHealthEvaluatorTest {

    @Test
    fun testNoEngineInstalled() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.ERROR,
            hasInstalledEngines = false,
            langAvailCode = VoiceHealthEvaluator.LANG_NOT_SUPPORTED,
            offlineArabicVoiceCount = 0,
        )
        assertEquals(VoiceHealthLevel.NO_ENGINE, result.level)
        assertFalse(result.isReady)
        assertFalse(result.isArabicSupported)
        assertFalse(result.hasOfflineVoice)
    }

    @Test
    fun testInitFailed() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.ERROR,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_NOT_SUPPORTED,
            offlineArabicVoiceCount = 0,
            enginePackage = "com.google.android.tts",
        )
        assertEquals(VoiceHealthLevel.INIT_FAILED, result.level)
        assertFalse(result.isReady)
        assertFalse(result.isArabicSupported)
    }

    @Test
    fun testLanguageNotSupported() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_NOT_SUPPORTED,
            offlineArabicVoiceCount = 0,
            enginePackage = "com.android.tts.mock",
        )
        assertEquals(VoiceHealthLevel.NOT_SUPPORTED, result.level)
        assertFalse(result.isReady)
        assertFalse(result.isArabicSupported)
    }

    @Test
    fun testLanguageDataMissing() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_MISSING_DATA,
            offlineArabicVoiceCount = 0,
            enginePackage = "com.google.android.tts",
        )
        assertEquals(VoiceHealthLevel.DATA_MISSING, result.level)
        assertFalse(result.isReady)
        assertFalse(result.isArabicSupported)
    }

    @Test
    fun testOfflineMissingWhenOnlyNetworkVoiceAvailable() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_AVAILABLE,
            offlineArabicVoiceCount = 0,
            totalArabicVoiceCount = 2,
            enginePackage = "com.google.android.tts",
        )
        assertEquals(VoiceHealthLevel.OFFLINE_MISSING, result.level)
        assertFalse(result.isReady)
        assertTrue(result.isArabicSupported)
        assertFalse(result.hasOfflineVoice)
        assertEquals(2, result.totalVoicesCount)
    }

    @Test
    fun testReadyWhenOfflineVoicesInstalled() {
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_AVAILABLE,
            offlineArabicVoiceCount = 2,
            totalArabicVoiceCount = 2,
            enginePackage = "com.google.android.tts",
            engineLabel = "Speech Services by Google",
        )
        assertEquals(VoiceHealthLevel.READY, result.level)
        assertTrue(result.isReady)
        assertTrue(result.isArabicSupported)
        assertTrue(result.hasOfflineVoice)
        assertEquals(2, result.offlineVoicesCount)
    }

    @Test
    fun testSamsungEngineProfileWithOfflineArabicVoice() {
        // Emulates a Samsung device where Samsung TTS engine has Arabic pre-installed
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_COUNTRY_AVAILABLE,
            offlineArabicVoiceCount = 1,
            totalArabicVoiceCount = 1,
            enginePackage = "com.samsung.SMT",
            engineLabel = "Samsung text-to-speech engine",
        )
        assertEquals(VoiceHealthLevel.READY, result.level)
        assertTrue(result.isReady)
        assertEquals("com.samsung.SMT", result.enginePackage)
    }

    @Test
    fun testPixelGoogleTtsProfileWithoutDownloadedData() {
        // Emulates a Google Pixel clean install before Google Speech Services finishes downloading the Arabic pack
        val result = VoiceHealthEvaluator.evaluate(
            initStatus = VoiceHealthEvaluator.SUCCESS,
            hasInstalledEngines = true,
            langAvailCode = VoiceHealthEvaluator.LANG_MISSING_DATA,
            offlineArabicVoiceCount = 0,
            totalArabicVoiceCount = 0,
            enginePackage = "com.google.android.tts",
            engineLabel = "Speech Services by Google",
        )
        assertEquals(VoiceHealthLevel.DATA_MISSING, result.level)
        assertFalse(result.isReady)
        assertEquals("com.google.android.tts", result.enginePackage)
    }
}
