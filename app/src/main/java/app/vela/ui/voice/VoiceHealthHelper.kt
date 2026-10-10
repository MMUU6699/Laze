package app.vela.ui.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import app.vela.core.voice.VoiceHealthEvaluator
import app.vela.core.voice.VoiceHealthLevel
import app.vela.core.voice.VoiceHealthStatus
import java.util.Locale

object VoiceHealthHelper {

    /**
     * Inspects the system TTS engine asynchronously to assess Arabic voice readiness.
     */
    fun checkVoiceHealth(
        context: Context,
        enginePackage: String? = null,
        onResult: (VoiceHealthStatus) -> Unit,
    ) {
        val pm = context.packageManager
        val installedEngines = runCatching {
            pm.queryIntentServices(Intent("android.intent.action.TTS_SERVICE"), 0)
        }.getOrNull().orEmpty()

        if (installedEngines.isEmpty()) {
            Handler(Looper.getMainLooper()).post {
                onResult(
                    VoiceHealthEvaluator.evaluate(
                        initStatus = VoiceHealthEvaluator.ERROR,
                        hasInstalledEngines = false,
                        langAvailCode = VoiceHealthEvaluator.LANG_NOT_SUPPORTED,
                        offlineArabicVoiceCount = 0,
                        enginePackage = enginePackage,
                    )
                )
            }
            return
        }

        var probeTts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val tts = probeTts
            val targetEngine = enginePackage ?: tts?.defaultEngine
            val engineLabel = installedEngines.firstOrNull { it.serviceInfo?.packageName == targetEngine }
                ?.loadLabel(pm)?.toString() ?: targetEngine

            if (status != TextToSpeech.SUCCESS || tts == null) {
                val eval = VoiceHealthEvaluator.evaluate(
                    initStatus = status,
                    hasInstalledEngines = true,
                    langAvailCode = VoiceHealthEvaluator.LANG_NOT_SUPPORTED,
                    offlineArabicVoiceCount = 0,
                    enginePackage = targetEngine,
                    engineLabel = engineLabel,
                )
                Handler(Looper.getMainLooper()).post {
                    onResult(eval)
                }
                runCatching { probeTts?.shutdown() }
                return@OnInitListener
            }

            val avail = runCatching { tts.isLanguageAvailable(Locale("ar")) }
                .getOrDefault(VoiceHealthEvaluator.LANG_NOT_SUPPORTED)

            val arabicVoices = runCatching { tts.voices.orEmpty() }
                .getOrDefault(emptySet())
                .filter { it.locale.language == "ar" }

            val offlineCount = arabicVoices.count { voice ->
                !voice.isNetworkConnectionRequired &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }

            val eval = VoiceHealthEvaluator.evaluate(
                initStatus = VoiceHealthEvaluator.SUCCESS,
                hasInstalledEngines = true,
                langAvailCode = avail,
                offlineArabicVoiceCount = offlineCount,
                totalArabicVoiceCount = arabicVoices.size,
                enginePackage = targetEngine,
                engineLabel = engineLabel,
            )

            Handler(Looper.getMainLooper()).post {
                onResult(eval)
            }

            runCatching { probeTts?.shutdown() }
        }

        probeTts = if (enginePackage != null) {
            TextToSpeech(context.applicationContext, listener, enginePackage)
        } else {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    /**
     * Prompts the system or active TTS engine to install voice data.
     */
    fun openTtsDataInstall(context: Context) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openTtsSettings(context)
        }
    }

    /**
     * Opens system Text-to-Speech settings.
     */
    fun openTtsSettings(context: Context) {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    /**
     * Directs the user to Google Text-to-Speech on Google Play or via web.
     */
    fun openGoogleTtsPlayStore(context: Context) {
        val playUri = Uri.parse("market://details?id=com.google.android.tts")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, playUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }
}
