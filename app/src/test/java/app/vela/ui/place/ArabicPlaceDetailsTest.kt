package app.vela.ui.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArabicPlaceDetailsTest {

    @Test
    fun testArabicCategoryTranslation() {
        assertNull(ArabicCategoryNames.translate(null, isArabic = true))
        assertEquals("", ArabicCategoryNames.translate("", isArabic = true) ?: "")

        // Non-Arabic locale keeps original
        assertEquals("Restaurant", ArabicCategoryNames.translate("Restaurant", isArabic = false))
        assertEquals("Coffee shop", ArabicCategoryNames.translate("Coffee shop", isArabic = false))

        // Arabic translations
        assertEquals("مطعم", ArabicCategoryNames.translate("Restaurant", isArabic = true))
        assertEquals("مطاعم", ArabicCategoryNames.translate("Restaurants", isArabic = true))
        assertEquals("مقهى", ArabicCategoryNames.translate("Cafe", isArabic = true))
        assertEquals("مقهى", ArabicCategoryNames.translate("Coffee shop", isArabic = true))
        assertEquals("وجبات سريعة", ArabicCategoryNames.translate("Fast food restaurant", isArabic = true))
        assertEquals("صيدلية", ArabicCategoryNames.translate("Pharmacy", isArabic = true))
        assertEquals("مستشفى", ArabicCategoryNames.translate("Hospital", isArabic = true))
        assertEquals("سوبرماركت", ArabicCategoryNames.translate("Supermarket", isArabic = true))
        assertEquals("محطة وقود (بنزين)", ArabicCategoryNames.translate("Gas station", isArabic = true))
        assertEquals("محطة وقود (بنزين)", ArabicCategoryNames.translate("Petrol station", isArabic = true))
        assertEquals("جامع ومسجد", ArabicCategoryNames.translate("Mosque", isArabic = true))
        assertEquals("مدرسة", ArabicCategoryNames.translate("School", isArabic = true))
        assertEquals("جامعة", ArabicCategoryNames.translate("University", isArabic = true))
        assertEquals("فندق", ArabicCategoryNames.translate("Hotel", isArabic = true))
        assertEquals("مصرف وبنك", ArabicCategoryNames.translate("Bank", isArabic = true))
        assertEquals("كراج حافلات", ArabicCategoryNames.translate("Bus station", isArabic = true))
        assertEquals("موقف تكسي", ArabicCategoryNames.translate("Taxi stand", isArabic = true))
    }

    @Test
    fun testArabicScheduleFormatting() {
        assertEquals("", ArabicScheduleHelper.formatDetail(null, isArabic = true))
        assertEquals("", ArabicScheduleHelper.formatDetail("", isArabic = true))

        // Non-Arabic locale
        assertEquals("Closes 10 PM", ArabicScheduleHelper.formatDetail("Closes 10 PM", isArabic = false))

        // Full phrases
        assertEquals("مفتوح 24 ساعة", ArabicScheduleHelper.formatDetail("Open 24 hours", isArabic = true))
        assertEquals("مغلق", ArabicScheduleHelper.formatDetail("Closed", isArabic = true))

        // Closing time
        assertEquals("يغلق عند 10:00 م", ArabicScheduleHelper.formatDetail("Closes 10:00 PM", isArabic = true))
        assertEquals("يغلق عند 1:00 ص", ArabicScheduleHelper.formatDetail("Closes 1:00 AM", isArabic = true))

        // Opening time
        assertEquals("يفتح عند 8:00 ص", ArabicScheduleHelper.formatDetail("Opens 8:00 AM", isArabic = true))
        assertEquals("يفتح غداً عند 8:00 ص", ArabicScheduleHelper.formatDetail("Opens 8:00 AM tomorrow", isArabic = true))

        // Day names
        assertEquals("يفتح عند 9:00 ص الإثنين", ArabicScheduleHelper.formatDetail("Opens 9:00 AM Mon", isArabic = true))
    }
}
