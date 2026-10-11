package app.vela.core.voice

import java.util.Locale

/**
 * Representation of a TTS Voice candidate for pure testing and ranking.
 */
data class VoiceCandidate(
    val name: String,
    val locale: Locale,
    val isNetworkConnectionRequired: Boolean,
    val isInstalled: Boolean,
    val quality: Int,
)

/**
 * Voice selection hierarchy:
 * 1. System default voice for [lang] if available.
 * 2. Highest quality offline voice for [lang].
 * 3. Highest quality network voice for [lang] (works immediately without prompting downloads).
 * 4. Engine default / language fallback.
 */
object VoiceSelector {

    fun selectCandidate(
        defaultVoice: VoiceCandidate?,
        availableVoices: List<VoiceCandidate>,
        targetLocale: Locale,
    ): VoiceCandidate? {
        // 1. System default voice for target language
        if (defaultVoice != null && defaultVoice.locale.language == targetLocale.language) {
            return defaultVoice
        }

        val matching = availableVoices.filter { it.locale.language == targetLocale.language }

        // 2. Offline voice (locally installed)
        val offline = matching.filter { !it.isNetworkConnectionRequired && it.isInstalled }
        val bestOffline = offline.maxByOrNull { it.quality }
        if (bestOffline != null) {
            return bestOffline
        }

        // 3. Network voice (high quality, works without downloads)
        val bestNetwork = matching.maxByOrNull { it.quality }
        if (bestNetwork != null) {
            return bestNetwork
        }

        // 4. Fallback to engine's setLanguage
        return null
    }
}
