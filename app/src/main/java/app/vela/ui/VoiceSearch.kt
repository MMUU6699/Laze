package app.vela.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.runtime.mutableStateOf

/**
 * Voice search for the search bar using standard Android speech recognition (RecognizerIntent).
 */
object VoiceSearch {
    val enabled = mutableStateOf(true)

    enum class Mode { SYSTEM, NONE }

    fun init(context: Context) {
        enabled.value = prefs(context).getBoolean(KEY, true)
        provider.value = prefs(context).getString(PROVIDER_KEY, null)
    }

    fun set(context: Context, value: Boolean) {
        enabled.value = value
        prefs(context).edit().putBoolean(KEY, value).apply()
    }

    data class Provider(val label: String, val component: android.content.ComponentName)

    val provider = mutableStateOf<String?>(null)

    fun providers(context: Context): List<Provider> = runCatching {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).map {
            val label = it.loadLabel(pm).toString().ifBlank {
                runCatching { it.activityInfo.applicationInfo.loadLabel(pm).toString() }.getOrDefault("")
            }.ifBlank { it.activityInfo.packageName }
            Provider(
                label,
                android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name),
            )
        }
    }.getOrDefault(emptyList())

    fun launchComponent(context: Context): android.content.ComponentName? {
        val all = providers(context)
        if (all.isEmpty()) return null
        val saved = provider.value?.let(android.content.ComponentName::unflattenFromString)
        all.firstOrNull { it.component == saved }?.let { return it.component }
        if (androidDefault(context) != null) return null
        return all.first().component
    }

    private fun androidDefault(context: Context): android.content.ComponentName? = runCatching {
        val ri = context.packageManager.resolveActivity(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        ) ?: return null
        val ai = ri.activityInfo ?: return null
        if (ai.packageName == "android") null
        else android.content.ComponentName(ai.packageName, ai.name)
    }.getOrNull()

    fun chosenProvider(context: Context): Provider? {
        val all = providers(context)
        if (all.isEmpty()) return null
        val saved = provider.value?.let(android.content.ComponentName::unflattenFromString)
        all.firstOrNull { it.component == saved }?.let { return it }
        androidDefault(context)?.let { d -> all.firstOrNull { it.component == d }?.let { return it } }
        return all.first()
    }

    fun setProvider(context: Context, component: android.content.ComponentName) {
        val flat = component.flattenToString()
        provider.value = flat
        prefs(context).edit().putString(PROVIDER_KEY, flat).apply()
    }

    fun clearProvider(context: Context) {
        provider.value = null
        prefs(context).edit().remove(PROVIDER_KEY).apply()
    }

    fun hasProvider(context: Context): Boolean = runCatching {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }.getOrDefault(false)

    fun resolvedMode(context: Context): Mode {
        if (!enabled.value) return Mode.NONE
        return if (hasProvider(context)) Mode.SYSTEM else Mode.NONE
    }

    private fun prefs(c: Context) = c.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
    private const val KEY = "voice_search_button"
    private const val PROVIDER_KEY = "voice_search_provider"
}
