package app.vela.core.util

/**
 * Utility for Bidirectional (BiDi) text formatting and Unicode directional isolation.
 * Prevents number-hyphen-number street names (e.g. "214-151", "126-214", "M-1")
 * from being visually inverted in RTL contexts or mispronounced by TTS engines.
 */
object BidiHelper {

    /**
     * Wraps a street or road name with Unicode Directional Isolates (FSI / PDI)
     * so that numbers and punctuation within the name stay in their logical order
     * when embedded in Arabic RTL sentences.
     */
    fun wrapRoadName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val trimmed = name.trim()
        // If already isolated, return as is
        if (trimmed.startsWith("\u2068") && trimmed.endsWith("\u2069")) return trimmed
        if (trimmed.startsWith("\u2066") && trimmed.endsWith("\u2069")) return trimmed
        return "\u2068$trimmed\u2069"
    }

    /**
     * Prepares numbered streets or road codes for Arabic text-to-speech so that
     * hyphenated street identifiers (e.g. "214-151") are read sequentially
     * ("214 إلى 151") instead of being reversed or read as a negative number.
     */
    fun formatForSpeech(text: String): String {
        if (text.isBlank()) return text
        // Clean any raw isolate characters so TTS engines don't stutter
        val cleaned = text
            .replace("\u2066", "")
            .replace("\u2067", "")
            .replace("\u2068", "")
            .replace("\u2069", "")
            .replace("\u200E", "")
            .replace("\u200F", "")

        // Replace number-hyphen-number patterns with sequential verbal phrases
        return cleaned.replace(Regex("""(\d+)\s*[-/]\s*(\d+)""")) { mr ->
            "${mr.groupValues[1]} إلى ${mr.groupValues[2]}"
        }
    }

    /**
     * Scans any text (such as navigation instructions, road names, and headlines)
     * and wraps any numbers, hyphenated codes (e.g. "118-214", "M-1", "I-95"),
     * or mixed alphanumeric identifiers with Unicode First Strong Isolate (\u2068...\u2069)
     * so that the Unicode BiDi algorithm renders numbers and codes in their
     * correct natural order in RTL paragraphs.
     */
    fun isolateBidiRuns(text: String): String {
        if (text.isBlank()) return text
        // Isolate sequences of alphanumeric parts joined by hyphens, slashes, or dashes
        val regex = Regex("""(?<![\u2068\u2066])\b([A-Za-z0-9]+(?:[-/][A-Za-z0-9]+)+)\b(?![\u2069])""")
        return regex.replace(text) { mr ->
            "\u2068${mr.value}\u2069"
        }
    }
}
