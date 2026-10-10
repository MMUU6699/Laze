package app.vela.core.util

/**
 * Normalizes Arabic text for high-tolerance search, voice input, and fuzzy matching.
 * Handles common Arabic orthographic variations, diacritics, prefixes (like "ال"),
 * and typo tolerance.
 */
object ArabicSearchNormalizer {

    private val DIACRITICS = Regex("[\u064B-\u0652\u0670\u0640]") // Fatha, Damma, Kasra, Tanwin, Shadda, Sukun, Dagger Alef, Tatweel
    private val ALEF_VARIANTS = Regex("[أإآٱ]")
    private val EASTERN_NUMERALS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    /**
     * Normalizes an Arabic string:
     * - Strips diacritics (tashkeel) and tatweel (kashida)
     * - Normalizes alef forms (أ, إ, آ, ٱ -> ا)
     * - Normalizes alef maqsura (ى -> ي)
     * - Normalizes taa marbouta (ة -> ه)
     * - Normalizes eastern Arabic numerals (٠..٩ -> 0..9)
     * - Collapses whitespace and trims
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val withoutDiacritics = DIACRITICS.replace(input, "")
        val unifiedAlef = ALEF_VARIANTS.replace(withoutDiacritics, "ا")

        val sb = StringBuilder(unifiedAlef.length)
        for (c in unifiedAlef) {
            when (c) {
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                in '٠'..'٩' -> sb.append(EASTERN_NUMERALS[c] ?: c)
                else -> sb.append(c)
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Strips leading definite article "ال" from Arabic words,
     * e.g., "الكرادة" -> "كرادة", "المنصور" -> "منصور".
     */
    fun stripDefiniteArticle(word: String): String {
        val norm = normalize(word)
        return if (norm.startsWith("ال") && norm.length > 3) {
            norm.substring(2)
        } else {
            norm
        }
    }

    /**
     * Checks if [query] matches [candidate] under Arabic normalization and fuzzy tolerance.
     * Matches if:
     * 1. Normalized candidate contains normalized query
     * 2. Candidate without "ال" contains query without "ال"
     * 3. Word-level fuzzy match with Levenshtein edit distance <= 1 (or 2 for words >= 6 chars)
     */
    fun matches(query: String, candidate: String): Boolean {
        val normQ = normalize(query).lowercase()
        val normC = normalize(candidate).lowercase()
        if (normQ.isEmpty() || normC.isEmpty()) return false

        // Exact substring match after normalization
        if (normC.contains(normQ)) return true

        // Definite article ("ال") agnostic match
        val strippedQ = stripDefiniteArticle(normQ)
        val strippedC = stripDefiniteArticle(normC)
        if (strippedC.contains(strippedQ) || normC.contains(strippedQ)) return true

        // Word-by-word token matching
        val qTokens = normQ.split(" ").filter { it.isNotBlank() }
        val cTokens = normC.split(" ").filter { it.isNotBlank() }

        if (qTokens.isEmpty()) return false

        // All query tokens must match at least one candidate token (either prefix or fuzzy)
        return qTokens.all { qTok ->
            val strippedQTok = stripDefiniteArticle(qTok)
            cTokens.any { cTok ->
                val strippedCTok = stripDefiniteArticle(cTok)
                cTok.startsWith(qTok) ||
                    strippedCTok.startsWith(strippedQTok) ||
                    isFuzzyMatch(strippedQTok, strippedCTok)
            }
        }
    }

    private fun isFuzzyMatch(s1: String, s2: String): Boolean {
        if (s1 == s2) return true
        val maxDist = if (s1.length >= 6 && s2.length >= 6) 2 else 1
        if (kotlin.math.abs(s1.length - s2.length) > maxDist) return false
        return levenshteinDistance(s1, s2) <= maxDist
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
