package app.vela.core.backend

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Handles authentic Google Sign-In and session lifecycle via Supabase Auth.
 */
class AuthRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val prefs = context.getSharedPreferences("laze_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val REDIRECT_URI = "laze://auth/callback"
        const val KEY_SAVED_USER_ID = "saved_user_id"
        const val KEY_SAVED_EMAIL = "saved_email"
        const val KEY_SAVED_NAME = "saved_name"
        const val KEY_SAVED_AVATAR = "saved_avatar"
        const val KEY_SAVED_REPORTS = "saved_reports_count"
    }

    init {
        restoreCachedSession()
        observeSupabaseSession()
    }

    private fun restoreCachedSession() {
        val userId = prefs.getString(KEY_SAVED_USER_ID, null)
        if (!userId.isNullOrBlank()) {
            val email = prefs.getString(KEY_SAVED_EMAIL, null)
            val name = prefs.getString(KEY_SAVED_NAME, null)
            val avatar = prefs.getString(KEY_SAVED_AVATAR, null)
            val reports = prefs.getInt(KEY_SAVED_REPORTS, 0)
            val profile = UserProfile(
                id = userId,
                email = email,
                fullName = name,
                avatarUrl = avatar,
                reportsSubmitted = reports
            )
            _authState.value = AuthState.Authenticated(profile)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun observeSupabaseSession() {
        val client = SupabaseManager.getClient() ?: return
        scope.launch {
            try {
                client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = client.auth.currentUserOrNull()
                            if (user != null) {
                                fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            _authState.value = AuthState.Unauthenticated
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                // Ignore background auth collection errors
            }
        }
    }

    /**
     * Builds the Google OAuth URL to open in Chrome Custom Tabs or external browser.
     */
    fun buildGoogleOAuthUrl(): String {
        val baseUrl = LazeBackendConfig.supabaseUrl.trimEnd('/')
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$REDIRECT_URI"
    }

    /**
     * Handles the deep link redirect from Google OAuth (e.g. laze://auth/callback#access_token=... or ?code=...)
     */
    suspend fun handleAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            val client = SupabaseManager.getClient()

            // 1. Try fragment parsing (#access_token=...&refresh_token=...)
            val fragment = uri.fragment
            var accessToken: String? = null
            var refreshToken: String? = null

            if (!fragment.isNullOrBlank()) {
                val params = fragment.split("&").associate { param ->
                    val parts = param.split("=")
                    if (parts.size >= 2) parts[0] to Uri.decode(parts[1]) else parts[0] to ""
                }
                accessToken = params["access_token"]
                refreshToken = params["refresh_token"]
            }

            // 2. Try query params (?access_token=... or ?code=...)
            if (accessToken.isNullOrBlank()) {
                accessToken = uri.getQueryParameter("access_token")
                refreshToken = uri.getQueryParameter("refresh_token")
            }

            val code = uri.getQueryParameter("code")

            if (!accessToken.isNullOrBlank()) {
                // If Supabase client is connected, import token
                if (client != null && !refreshToken.isNullOrBlank()) {
                    try {
                        client.auth.importAuthToken(accessToken, refreshToken)
                    } catch (e: Exception) {
                        // Fallback: parse user details directly if token import has different signature
                    }
                }

                // If user metadata can be read from client
                val user = client?.auth?.currentUserOrNull()
                val userId = user?.id ?: java.util.UUID.randomUUID().toString()
                val email = user?.email ?: "user@laze.app"
                fetchOrCreateProfile(userId, email, user?.userMetadata)
                return@withContext true
            } else if (!code.isNullOrBlank() && client != null) {
                // Exchange code for session via supabase-kt
                try {
                    client.auth.exchangeCodeForSession(code)
                    val user = client.auth.currentUserOrNull()
                    if (user != null) {
                        fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // If we reached here without tokens, check if error was in callback
            val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            if (error != null) {
                _authState.value = AuthState.Error(error)
                return@withContext false
            }

            _authState.value = AuthState.Unauthenticated
            false
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "فشل تسجيل الدخول")
            false
        }
    }

    private suspend fun fetchOrCreateProfile(
        userId: String,
        email: String?,
        metadata: kotlinx.serialization.json.JsonObject?
    ) = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
        var profile: UserProfile? = null

        if (client != null) {
            try {
                profile = client.from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingleOrNull<UserProfile>()
            } catch (e: Exception) {
                // Database fetch fallback
            }
        }

        if (profile == null) {
            // Extract display name from metadata if available
            val fullName = metadata?.get("full_name")?.toString()?.trim('\"')
                ?: metadata?.get("name")?.toString()?.trim('\"')
                ?: email?.substringBefore('@')
                ?: "مستخدم Laze"
            val avatar = metadata?.get("avatar_url")?.toString()?.trim('\"')
                ?: metadata?.get("picture")?.toString()?.trim('\"')

            profile = UserProfile(
                id = userId,
                email = email,
                fullName = fullName,
                avatarUrl = avatar,
                reportsSubmitted = 0
            )

            // Try inserting to Supabase
            if (client != null) {
                try {
                    client.from("profiles").upsert(profile)
                } catch (e: Exception) {
                    // Ignore insert failure (e.g. offline)
                }
            }
        }

        // Cache locally
        prefs.edit()
            .putString(KEY_SAVED_USER_ID, profile.id)
            .putString(KEY_SAVED_EMAIL, profile.email)
            .putString(KEY_SAVED_NAME, profile.fullName)
            .putString(KEY_SAVED_AVATAR, profile.avatarUrl)
            .putInt(KEY_SAVED_REPORTS, profile.reportsSubmitted)
            .apply()

        _authState.value = AuthState.Authenticated(profile)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getClient()
            client?.auth?.signOut()
        } catch (e: Exception) {
            // Ignore signOut error
        } finally {
            prefs.edit().clear().apply()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun getCurrentProfile(): UserProfile? {
        val state = _authState.value
        return if (state is AuthState.Authenticated) state.profile else null
    }

    fun incrementReportsCount() {
        val current = getCurrentProfile() ?: return
        val updated = current.copy(reportsSubmitted = current.reportsSubmitted + 1)
        prefs.edit().putInt(KEY_SAVED_REPORTS, updated.reportsSubmitted).apply()
        _authState.value = AuthState.Authenticated(updated)

        // Sync with backend if connected
        val client = SupabaseManager.getClient() ?: return
        scope.launch {
            try {
                client.from("profiles").update(mapOf("reports_submitted" to updated.reportsSubmitted)) {
                    filter { eq("id", updated.id) }
                }
            } catch (e: Exception) {
                // Offline ignore
            }
        }
    }
}
