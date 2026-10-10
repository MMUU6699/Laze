package app.vela.core.backend

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration holder for Laze backend services (Supabase URL and API Key).
 * Supports runtime configuration via SharedPreferences as well as defaults.
 */
object LazeBackendConfig {
    private const val PREFS_NAME = "laze_backend_prefs"
    private const val KEY_SUPABASE_URL = "supabase_url"
    private const val KEY_SUPABASE_KEY = "supabase_key"

    val DEFAULT_SUPABASE_URL: String = app.vela.core.BuildConfig.SUPABASE_URL
    val DEFAULT_SUPABASE_KEY: String = app.vela.core.BuildConfig.SUPABASE_ANON_KEY

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedKey: String? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedUrl = prefs.getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
        cachedKey = prefs.getString(KEY_SUPABASE_KEY, DEFAULT_SUPABASE_KEY) ?: DEFAULT_SUPABASE_KEY
    }

    var supabaseUrl: String
        get() = cachedUrl ?: DEFAULT_SUPABASE_URL
        set(value) {
            cachedUrl = value.trim()
        }

    var supabaseAnonKey: String
        get() = cachedKey ?: DEFAULT_SUPABASE_KEY
        set(value) {
            cachedKey = value.trim()
        }

    fun save(context: Context, url: String, key: String) {
        cachedUrl = url.trim()
        cachedKey = key.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUPABASE_URL, cachedUrl)
            .putString(KEY_SUPABASE_KEY, cachedKey)
            .apply()
    }

    val isConfigured: Boolean
        get() {
            val url = supabaseUrl
            val key = supabaseAnonKey
            return url.isNotBlank() &&
                url.startsWith("https://") &&
                !url.contains("your-project.supabase.co") &&
                key.isNotBlank() &&
                key != "your-anon-key"
        }
}
