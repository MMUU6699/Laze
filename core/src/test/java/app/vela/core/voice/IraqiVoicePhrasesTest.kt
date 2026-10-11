package app.vela.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class IraqiVoicePhrasesTest {

    @Test
    fun `disabled returns exact original text`() {
        val original = "بعد مئتي متر، انعطف إلى اليمين"
        assertEquals(original, IraqiVoicePhrases.adapt(original, enabled = false))
    }

    @Test
    fun `u-turn maps to استدير وارجع`() {
        assertEquals("استدير وارجع", IraqiVoicePhrases.adapt("استدر للخلف"))
        assertEquals("استدير وارجع", IraqiVoicePhrases.adapt("قم بالدوران للخلف"))
        assertEquals("استدير وارجع", IraqiVoicePhrases.adapt("انعطف للخلف"))
    }

    @Test
    fun `roundabout maps to الفلكة and exits`() {
        assertEquals("عند الفلكة، اسلك أول مخرج", IraqiVoicePhrases.adapt("عند الدوار، اسلك المخرج الأول"))
        assertEquals("بالفلكة، اسلك ثاني مخرج", IraqiVoicePhrases.adapt("في الدوار، اسلك المخرج الثاني"))
        assertEquals("ادخل الفلكة ثم اطلع من الفلكة", IraqiVoicePhrases.adapt("ادخل الدوار ثم اخرج من الدوار"))
    }

    @Test
    fun `turns map to لف يمنة and لف يسرة`() {
        assertEquals("بعد ميتين متر، لف يمنة باتجاه شارع الرشيد", IraqiVoicePhrases.adapt("بعد مئتي متر، انعطف إلى اليمين باتجاه شارع الرشيد"))
        assertEquals("لف يسرة ثم الزم اليمنة", IraqiVoicePhrases.adapt("انعطف يساراً ثم الزم اليمين"))
        assertEquals("وجهتك على اليسرة", IraqiVoicePhrases.adapt("وجهتك على اليسار"))
    }

    @Test
    fun `straight maps to ضل مكمل كبل`() {
        assertEquals("ضل مكمل كبل لمسافة 500 متر", IraqiVoicePhrases.adapt("سر إلى الأمام لمسافة 500 متر"))
        assertEquals("ضل مكمل كبل", IraqiVoicePhrases.adapt("استمر في السير إلى الأمام"))
        assertEquals("ضل مكمل على نفس الطريق", IraqiVoicePhrases.adapt("استمر على نفس الطريق"))
    }

    @Test
    fun `distances map to Iraqi numbers and units`() {
        assertEquals("بعد ميتين متر", IraqiVoicePhrases.adapt("بعد مئتي متر"))
        assertEquals("بعد مية متر", IraqiVoicePhrases.adapt("بعد مئة متر"))
        assertEquals("بعد نص كيلو", IraqiVoicePhrases.adapt("بعد 500 متر"))
        assertEquals("بعد كيلومتر", IraqiVoicePhrases.adapt("بعد 1 كيلومتر"))
        assertEquals("بعد كيلوين", IraqiVoicePhrases.adapt("بعد كيلومترين"))
    }

    @Test
    fun `arrival maps to وصلت بالسلامة`() {
        assertEquals("وصلت بالسلامة", IraqiVoicePhrases.adapt("لقد وصلت إلى وجهتك"))
        assertEquals("وصلت بالسلامة", IraqiVoicePhrases.adapt("وصلت إلى وجهتك"))
    }
}
