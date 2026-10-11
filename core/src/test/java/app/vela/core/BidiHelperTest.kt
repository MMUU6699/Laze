package app.vela.core

import app.vela.core.i18n.ArNavStrings
import app.vela.core.util.BidiHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiHelperTest {

    @Test
    fun wrapsHyphenatedRoadNameWithDirectionalIsolate() {
        val isolated = BidiHelper.wrapRoadName("214-151")
        assertEquals("\u2068214-151\u2069", isolated)
    }

    @Test
    fun doesNotDoubleWrapAlreadyIsolatedName() {
        val first = BidiHelper.wrapRoadName("214-151")
        val second = BidiHelper.wrapRoadName(first)
        assertEquals("\u2068214-151\u2069", second)
    }

    @Test
    fun handlesNullAndBlankGracefully() {
        assertEquals("", BidiHelper.wrapRoadName(null))
        assertEquals("", BidiHelper.wrapRoadName("   "))
    }

    @Test
    fun formatsHyphenatedNumbersForArabicSpeechSequentially() {
        val spoken = BidiHelper.formatForSpeech("انعطف يسارًا نحو \u2068214-151\u2069")
        assertEquals("انعطف يسارًا نحو 214 إلى 151", spoken)
    }

    @Test
    fun arNavStringsPhraseIsolatesRoadNames() {
        val phrase = ArNavStrings.phrase(
            type = "turn",
            mod = "left",
            road = "214-151",
            dest = null,
            exitNo = null,
            rbExit = null,
        )
        // Ensure "214-151" is wrapped with directional isolates inside the Arabic phrase
        assertTrue("Phrase should contain isolated road name", phrase.contains("\u2068214-151\u2069"))
    }

    @Test
    fun arNavStringsExpandForSpeechProducesVerbalPhrase() {
        val expanded = ArNavStrings.expandForSpeech("انعطف يسارًا نحو 214-151")
        assertEquals("انعطف يسارًا نحو 214 إلى 151", expanded)
    }
}
