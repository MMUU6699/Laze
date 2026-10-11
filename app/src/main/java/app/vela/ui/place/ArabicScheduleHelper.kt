package app.vela.ui.place

import app.vela.ui.AppLocale
import java.util.Locale

/**
 * Formats working hours and open/closed transitions into natural Arabic.
 * Converts phrases like "Closes 10 PM", "Opens 8 AM tomorrow", "Open 24 hours"
 * into natural Arabic phrasing ("يغلق عند 10:00 م", "يفتح غداً عند 8:00 ص", "مفتوح 24 ساعة").
 */
object ArabicScheduleHelper {

    private val DAYS_MAP = mapOf(
        "Mon" to "الإثنين",
        "Monday" to "الإثنين",
        "Tue" to "الثلاثاء",
        "Tuesday" to "الثلاثاء",
        "Wed" to "الأربعاء",
        "Wednesday" to "الأربعاء",
        "Thu" to "الخميس",
        "Thursday" to "الخميس",
        "Fri" to "الجمعة",
        "Friday" to "الجمعة",
        "Sat" to "السبت",
        "Saturday" to "السبت",
        "Sun" to "الأحد",
        "Sunday" to "الأحد",
    )

    fun formatDetail(detail: String?, isArabic: Boolean = AppLocale.effective().language == "ar"): String {
        if (detail.isNullOrBlank()) return ""
        if (!isArabic) return detail.trim()

        var s = detail.trim()

        // Handle full phrases
        if (s.equals("Open 24 hours", ignoreCase = true) || s.equals("24 hours", ignoreCase = true)) {
            return "مفتوح 24 ساعة"
        }
        if (s.equals("Closed", ignoreCase = true)) {
            return "مغلق"
        }

        val hasTomorrow = s.contains("tomorrow", ignoreCase = true)
        s = s.replace("tomorrow", "", ignoreCase = true).trim()

        // Replace days
        for ((enDay, arDay) in DAYS_MAP) {
            if (s.contains(enDay, ignoreCase = true)) {
                s = s.replace(Regex("(?i)\\b$enDay\\b"), arDay)
            }
        }

        // Replace meridians
        s = s.replace(Regex("(?i)\\s*AM\\b"), " ص")
        s = s.replace(Regex("(?i)\\s*PM\\b"), " م")
        s = s.replace(Regex("(?i)\\bmidnight\\b"), "منتصف الليل")
        s = s.replace(Regex("(?i)\\bnoon\\b"), "الظهر")

        // Handle action prefixes
        when {
            s.startsWith("Closes", ignoreCase = true) -> {
                val time = s.removePrefix("Closes").removePrefix("closes").trim()
                return "يغلق عند $time"
            }
            s.startsWith("Opens", ignoreCase = true) -> {
                val time = s.removePrefix("Opens").removePrefix("opens").trim()
                return if (hasTomorrow) "يفتح غداً عند $time" else "يفتح عند $time"
            }
            s.startsWith("Closing soon", ignoreCase = true) -> {
                return "يغلق قريباً"
            }
            s.startsWith("Opening soon", ignoreCase = true) -> {
                return "يفتح قريباً"
            }
            s.startsWith("Open", ignoreCase = true) -> {
                val rem = s.removePrefix("Open").removePrefix("open").trim().removePrefix("·").trim()
                return if (rem.isNotBlank()) formatDetail(rem, isArabic) else "مفتوح"
            }
            else -> {
                return if (hasTomorrow) "$s (غداً)" else s
            }
        }
    }
}
