package app.vela.core.voice

/**
 * Health assessment of the system text-to-speech engine for navigation guidance.
 */
enum class VoiceHealthLevel {
    READY,              // Arabic voice is installed and available offline
    OFFLINE_MISSING,    // Arabic voice is available, but requires an active internet connection
    DATA_MISSING,       // TTS engine supports Arabic, but the language voice data is not downloaded
    NOT_SUPPORTED,      // The active TTS engine does not support Arabic at all
    NO_ENGINE,          // No TTS engine is installed on the system
    INIT_FAILED         // TTS engine failed to initialize
}

/**
 * Detailed diagnostic state for Arabic (or active language) TTS.
 */
data class VoiceHealthStatus(
    val level: VoiceHealthLevel,
    val enginePackage: String? = null,
    val engineLabel: String? = null,
    val isArabicSupported: Boolean = false,
    val hasOfflineVoice: Boolean = false,
    val langAvailabilityCode: Int = 0,
    val offlineVoicesCount: Int = 0,
    val totalVoicesCount: Int = 0,
    val detailMessage: String = "",
) {
    val isReady: Boolean get() = level == VoiceHealthLevel.READY
    val canSpeak: Boolean get() = level == VoiceHealthLevel.READY || level == VoiceHealthLevel.OFFLINE_MISSING || isArabicSupported
}

/**
 * Pure evaluator for TTS diagnostics, decoupled from Android framework services for unit testing.
 */
object VoiceHealthEvaluator {
    // TextToSpeech constants for test purity:
    const val SUCCESS = 0
    const val ERROR = -1
    const val LANG_AVAILABLE = 0
    const val LANG_COUNTRY_AVAILABLE = 1
    const val LANG_COUNTRY_VAR_AVAILABLE = 2
    const val LANG_MISSING_DATA = -1
    const val LANG_NOT_SUPPORTED = -2

    fun evaluate(
        initStatus: Int,
        hasInstalledEngines: Boolean,
        langAvailCode: Int,
        offlineArabicVoiceCount: Int,
        totalArabicVoiceCount: Int = offlineArabicVoiceCount,
        enginePackage: String? = null,
        engineLabel: String? = null,
    ): VoiceHealthStatus {
        if (!hasInstalledEngines) {
            return VoiceHealthStatus(
                level = VoiceHealthLevel.NO_ENGINE,
                enginePackage = enginePackage,
                engineLabel = engineLabel,
                isArabicSupported = false,
                hasOfflineVoice = false,
                langAvailabilityCode = langAvailCode,
                detailMessage = "No TextToSpeech engine found on device",
            )
        }

        if (initStatus != SUCCESS) {
            return VoiceHealthStatus(
                level = VoiceHealthLevel.INIT_FAILED,
                enginePackage = enginePackage,
                engineLabel = engineLabel,
                isArabicSupported = false,
                hasOfflineVoice = false,
                langAvailabilityCode = langAvailCode,
                detailMessage = "TTS engine initialization failed (status: $initStatus)",
            )
        }

        val isSupported = langAvailCode >= LANG_AVAILABLE
        if (!isSupported) {
            val level = if (langAvailCode == LANG_MISSING_DATA) {
                VoiceHealthLevel.DATA_MISSING
            } else {
                VoiceHealthLevel.NOT_SUPPORTED
            }
            return VoiceHealthStatus(
                level = level,
                enginePackage = enginePackage,
                engineLabel = engineLabel,
                isArabicSupported = false,
                hasOfflineVoice = false,
                langAvailabilityCode = langAvailCode,
                detailMessage = if (level == VoiceHealthLevel.DATA_MISSING) {
                    "Arabic voice data is missing from the TTS engine"
                } else {
                    "Arabic language is not supported by the active TTS engine"
                },
            )
        }

        if (offlineArabicVoiceCount <= 0 && totalArabicVoiceCount > 0) {
            return VoiceHealthStatus(
                level = VoiceHealthLevel.OFFLINE_MISSING,
                enginePackage = enginePackage,
                engineLabel = engineLabel,
                isArabicSupported = true,
                hasOfflineVoice = false,
                langAvailabilityCode = langAvailCode,
                offlineVoicesCount = 0,
                totalVoicesCount = totalArabicVoiceCount,
                detailMessage = "Arabic voice requires an active internet connection",
            )
        } else if (offlineArabicVoiceCount <= 0 && totalArabicVoiceCount == 0) {
            // Language code reported available, but no voice instances returned
            return VoiceHealthStatus(
                level = VoiceHealthLevel.DATA_MISSING,
                enginePackage = enginePackage,
                engineLabel = engineLabel,
                isArabicSupported = true,
                hasOfflineVoice = false,
                langAvailabilityCode = langAvailCode,
                offlineVoicesCount = 0,
                totalVoicesCount = 0,
                detailMessage = "No installed Arabic voices found in the engine",
            )
        }

        return VoiceHealthStatus(
            level = VoiceHealthLevel.READY,
            enginePackage = enginePackage,
            engineLabel = engineLabel,
            isArabicSupported = true,
            hasOfflineVoice = true,
            langAvailabilityCode = langAvailCode,
            offlineVoicesCount = offlineArabicVoiceCount,
            totalVoicesCount = totalArabicVoiceCount,
            detailMessage = "Arabic offline voice is ready",
        )
    }
}
