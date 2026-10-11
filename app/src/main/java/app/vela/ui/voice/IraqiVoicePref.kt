package app.vela.ui.voice

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Preference for enabling simplified Iraqi spoken phrases for navigation instructions.
 * Enabled by default for Iraqi users.
 */
object IraqiVoicePref {
    private const val PREFS_NAME = "vela_settings"
    private const val KEY_IRAQI_DIALECT = "voice_iraqi_dialect"

    val enabled = mutableStateOf(true)

    fun isEnabled(context: Context): Boolean {
        val pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IRAQI_DIALECT, true)
        enabled.value = pref
        return pref
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IRAQI_DIALECT, value)
            .apply()
        enabled.value = value
    }
}
