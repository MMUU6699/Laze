package app.vela.core.backend

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

/**
 * Singleton manager providing a lazily-configured SupabaseClient instance.
 */
object SupabaseManager {
    @Volatile
    private var client: SupabaseClient? = null

    @Synchronized
    fun getClient(): SupabaseClient? {
        if (!LazeBackendConfig.isConfigured) return null
        if (client == null) {
            try {
                client = createSupabaseClient(
                    supabaseUrl = LazeBackendConfig.supabaseUrl,
                    supabaseKey = LazeBackendConfig.supabaseAnonKey
                ) {
                    install(Auth) {
                        scheme = "laze"
                        host = "auth"
                    }
                    install(Postgrest)
                    install(Realtime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return client
    }

    @Synchronized
    fun resetClient() {
        client = null
    }

    val isAvailable: Boolean
        get() = LazeBackendConfig.isConfigured
}
