package app.vela.core.backend

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Repository for fetching and caching remote app configuration values from
 * the Supabase `app_config` table.
 *
 * Cloud-first with local cache: on first launch the app reads from Supabase,
 * then caches the values in SharedPreferences for offline resilience.
 * Subsequent launches serve the cache immediately while refreshing in the
 * background.
 *
 * **Security note:** Only rows with `is_public = true` are readable via the
 * anon key (enforced by the RLS policy on `app_config`).
 */
class RemoteConfigRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "laze_remote_config_cache"
        private const val CACHE_TS_SUFFIX = "_ts"
        /** Re-fetch from Supabase when the local cache is older than 6 hours. */
        private const val STALE_MS = 6 * 60 * 60 * 1000L
    }

    @Serializable
    private data class AppConfigRow(
        val key: String,
        val value: String,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mu = Mutex()

    /**
     * Returns the config [value] for [key], preferring the local cache when
     * fresh, otherwise fetching from Supabase.  Returns `null` if both the
     * network and the cache miss.
     */
    suspend fun getString(key: String, forceRefresh: Boolean = false): String? = mu.withLock {
        val cached = prefs.getString(key, null)
        val cachedTs = prefs.getLong("$key$CACHE_TS_SUFFIX", 0L)
        val isFresh = System.currentTimeMillis() - cachedTs < STALE_MS

        // Serve cached immediately when fresh and not forced
        if (cached != null && isFresh && !forceRefresh) return cached

        // Attempt cloud fetch
        val remote = fetchFromCloud(key)
        if (remote != null) {
            prefs.edit()
                .putString(key, remote)
                .putLong("$key$CACHE_TS_SUFFIX", System.currentTimeMillis())
                .apply()
            return remote
        }

        // Fallback to stale cache
        return cached
    }

    /**
     * Convenience: fetches the Mapbox access token.
     */
    suspend fun getMapboxToken(): String? = getString("mapbox_token")

    /**
     * Forces a background refresh of all known config keys.
     */
    suspend fun refreshAll() {
        for (key in listOf("mapbox_token")) {
            getString(key, forceRefresh = true)
        }
    }

    private suspend fun fetchFromCloud(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getClient() ?: return@withContext null
            val rows = client.postgrest
                .from("app_config")
                .select {
                    filter { eq("key", key) }
                }
                .decodeList<AppConfigRow>()
            rows.firstOrNull()?.value
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Synchronous read from the local SharedPreferences cache only.
     * Useful in non-suspending contexts (e.g. token provider lambdas).
     * Returns `null` if the key has never been fetched from the cloud.
     */
    fun getCachedString(key: String): String? = prefs.getString(key, null)
}
