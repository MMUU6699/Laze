package app.vela.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VoiceFallbackSelectionTest {

    private val arLocale = Locale("ar")
    private val enLocale = Locale("en")

    @Test
    fun prefersSystemDefaultArabicVoiceFirst() {
        val defaultAr = VoiceCandidate(
            name = "ar-xa-x-system-def",
            locale = arLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 300,
        )
        val otherOffline = VoiceCandidate(
            name = "ar-xa-x-other",
            locale = arLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 400,
        )

        val selected = VoiceSelector.selectCandidate(
            defaultVoice = defaultAr,
            availableVoices = listOf(otherOffline),
            targetLocale = arLocale,
        )
        assertEquals("Should select system default Arabic voice", defaultAr, selected)
    }

    @Test
    fun fallsBackToOfflineArabicVoiceWhenDefaultIsNotArabic() {
        val defaultEn = VoiceCandidate(
            name = "en-us-x-sfg",
            locale = enLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 300,
        )
        val offlineAr = VoiceCandidate(
            name = "ar-xa-x-offline",
            locale = arLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 400,
        )
        val networkAr = VoiceCandidate(
            name = "ar-xa-x-network",
            locale = arLocale,
            isNetworkConnectionRequired = true,
            isInstalled = false,
            quality = 500,
        )

        val selected = VoiceSelector.selectCandidate(
            defaultVoice = defaultEn,
            availableVoices = listOf(offlineAr, networkAr),
            targetLocale = arLocale,
        )
        assertEquals("Should select offline Arabic voice over network", offlineAr, selected)
    }

    @Test
    fun selectsNetworkArabicVoiceWithoutPromptingDownloadWhenOfflineMissing() {
        val defaultEn = VoiceCandidate(
            name = "en-us-x-sfg",
            locale = enLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 300,
        )
        val networkArLow = VoiceCandidate(
            name = "ar-xa-x-net-1",
            locale = arLocale,
            isNetworkConnectionRequired = true,
            isInstalled = false,
            quality = 300,
        )
        val networkArHigh = VoiceCandidate(
            name = "ar-xa-x-net-2",
            locale = arLocale,
            isNetworkConnectionRequired = true,
            isInstalled = false,
            quality = 500,
        )

        val selected = VoiceSelector.selectCandidate(
            defaultVoice = defaultEn,
            availableVoices = listOf(networkArLow, networkArHigh),
            targetLocale = arLocale,
        )
        assertNotNull("Should select network voice when no offline voice exists", selected)
        assertEquals("Should choose highest quality network voice", networkArHigh, selected)
    }

    @Test
    fun returnsNullWhenNoMatchingVoiceObjectsExistToRelyOnEngineSetLanguage() {
        val defaultEn = VoiceCandidate(
            name = "en-us-x-sfg",
            locale = enLocale,
            isNetworkConnectionRequired = false,
            isInstalled = true,
            quality = 300,
        )

        val selected = VoiceSelector.selectCandidate(
            defaultVoice = defaultEn,
            availableVoices = emptyList(),
            targetLocale = arLocale,
        )
        assertNull("Should return null so engine setLanguage(Locale('ar')) executes as final fallback", selected)
    }
}
