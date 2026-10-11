package app.vela.core.backend

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class SignUpOutcome {
    data class SuccessLoggedIn(val profile: UserProfile) : SignUpOutcome()
    data class NeedsEmailConfirmation(val email: String) : SignUpOutcome()
}

internal data class AuthCallbackTokens(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val code: String? = null,
    val error: String? = null,
    val errorDescription: String? = null
) {
    val displayError: String?
        get() = errorDescription ?: error
}

internal fun parseAuthCallbackUrl(url: String): AuthCallbackTokens {
    var accessToken: String? = null
    var refreshToken: String? = null
    var code: String? = null
    var error: String? = null
    var errorDescription: String? = null

    val fragmentIndex = url.indexOf('#')
    if (fragmentIndex >= 0 && fragmentIndex < url.length - 1) {
        val fragment = url.substring(fragmentIndex + 1)
        val params = fragment.split("&")
        for (p in params) {
            val parts = p.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                val value = try {
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
                } catch (_: Exception) {
                    parts[1]
                }
                when (key) {
                    "access_token" -> accessToken = value
                    "refresh_token" -> refreshToken = value
                    "error" -> error = value
                    "error_description" -> errorDescription = value
                }
            }
        }
    }

    val queryIndex = url.indexOf('?')
    if (queryIndex >= 0) {
        val endQuery = if (fragmentIndex > queryIndex) fragmentIndex else url.length
        val query = url.substring(queryIndex + 1, endQuery)
        val params = query.split("&")
        for (p in params) {
            val parts = p.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                val value = try {
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
                } catch (_: Exception) {
                    parts[1]
                }
                when (key) {
                    "access_token" -> if (accessToken == null) accessToken = value
                    "refresh_token" -> if (refreshToken == null) refreshToken = value
                    "code" -> if (code == null) code = value
                    "error" -> if (error == null) error = value
                    "error_description" -> if (errorDescription == null) errorDescription = value
                }
            }
        }
    }

    return AuthCallbackTokens(accessToken, refreshToken, code, error, errorDescription)
}

/**
 * Handles authentic Google Sign-In and session lifecycle via Supabase Auth.
 * Adheres to strict session verification (Supabase session is the single source of truth).
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
        observeSupabaseSession()
    }

    private fun observeSupabaseSession() {
        val client = SupabaseManager.getClient()
        if (client == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }
        scope.launch {
            try {
                // Check if Supabase client already has an active session from local session manager
                val currentUser = client.auth.currentUserOrNull()
                if (currentUser != null) {
                    val cached = getCachedProfile()
                    if (cached != null && cached.id == currentUser.id) {
                        _authState.value = AuthState.Authenticated(cached)
                    }
                    fetchOrCreateProfile(currentUser.id, currentUser.email, currentUser.userMetadata)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }

                client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = client.auth.currentUserOrNull()
                            if (user != null) {
                                fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                            } else {
                                _authState.value = AuthState.Unauthenticated
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            prefs.edit().clear().apply()
                            _authState.value = AuthState.Unauthenticated
                        }
                        is SessionStatus.Initializing -> {
                            _authState.value = AuthState.Loading
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (_authState.value is AuthState.Loading) {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    private fun getCachedProfile(): UserProfile? {
        val userId = prefs.getString(KEY_SAVED_USER_ID, null) ?: return null
        return UserProfile(
            id = userId,
            email = prefs.getString(KEY_SAVED_EMAIL, null),
            fullName = prefs.getString(KEY_SAVED_NAME, null),
            avatarUrl = prefs.getString(KEY_SAVED_AVATAR, null),
            reportsSubmitted = prefs.getInt(KEY_SAVED_REPORTS, 0)
        )
    }

    /**
     * Builds the Google OAuth URL to open in Chrome Custom Tabs or external browser.
     */
    fun buildGoogleOAuthUrl(): String {
        val baseUrl = LazeBackendConfig.supabaseUrl.trimEnd('/')
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$REDIRECT_URI"
    }

    /**
     * Handles the deep link redirect from Google OAuth via Uri.
     */
    suspend fun handleAuthCallback(uri: Uri): Boolean = handleAuthCallback(uri.toString())

    /**
     * Handles the deep link redirect from Google OAuth via string.
     */
    suspend fun handleAuthCallback(urlString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            val client = SupabaseManager.getClient()
            if (client == null) {
                _authState.value = AuthState.Error("خدمة المصادقة غير مهيأة")
                return@withContext false
            }

            val tokens = parseAuthCallbackUrl(urlString)

            if (tokens.error != null) {
                _authState.value = AuthState.Error(tokens.error)
                return@withContext false
            }

            if (!tokens.accessToken.isNullOrBlank()) {
                if (!tokens.refreshToken.isNullOrBlank()) {
                    try {
                        client.auth.importAuthToken(tokens.accessToken, tokens.refreshToken)
                    } catch (e: Exception) {
                        _authState.value = AuthState.Error("فشل استيراد جلسة تسجيل الدخول: ${e.localizedMessage ?: "خطأ غير معروف"}")
                        return@withContext false
                    }
                }

                val user = client.auth.currentUserOrNull()
                if (user == null) {
                    _authState.value = AuthState.Error("تعذر التحقق من هوية المستخدم بعد تسجيل الدخول")
                    return@withContext false
                }
                fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                return@withContext true
            } else if (!tokens.code.isNullOrBlank()) {
                try {
                    client.auth.exchangeCodeForSession(tokens.code)
                    val user = client.auth.currentUserOrNull()
                    if (user != null) {
                        fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                        return@withContext true
                    } else {
                        _authState.value = AuthState.Error("تعذر استخراج بيانات المستخدم من الرمز المستلم")
                        return@withContext false
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Error("فشل تبادل رمز المصادقة: ${e.localizedMessage ?: "خطأ غير معروف"}")
                    return@withContext false
                }
            }

            _authState.value = AuthState.Error("لم يتم استلام رمز مصادقة صالح")
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
                // Fallback to metadata creation or cached values if offline
            }
        }

        if (profile == null) {
            val fullName = metadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: metadata?.get("name")?.jsonPrimitive?.contentOrNull
                ?: email?.substringBefore('@')
                ?: "مستخدم Laze"
            val avatar = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                ?: metadata?.get("picture")?.jsonPrimitive?.contentOrNull

            profile = UserProfile(
                id = userId,
                email = email,
                fullName = fullName,
                avatarUrl = avatar,
                reportsSubmitted = 0
            )

            if (client != null) {
                try {
                    client.from("profiles").upsert(profile)
                } catch (e: Exception) {
                    // Ignored if offline
                }
            }
        }

        // Cache locally for display
        prefs.edit()
            .putString(KEY_SAVED_USER_ID, profile.id)
            .putString(KEY_SAVED_EMAIL, profile.email)
            .putString(KEY_SAVED_NAME, profile.fullName)
            .putString(KEY_SAVED_AVATAR, profile.avatarUrl)
            .putInt(KEY_SAVED_REPORTS, profile.reportsSubmitted)
            .apply()

        _authState.value = AuthState.Authenticated(profile)
    }

    /**
     * Signs out from Supabase Auth and clears all cached session data.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getClient()
            client?.auth?.signOut()
        } catch (e: Exception) {
            // Network failure during signOut should not prevent local cleanup
        } finally {
            prefs.edit().clear().apply()
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Permanently deletes the current user account and data (Google Play compliance).
     */
    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة الخادم غير متاحة"))
        try {
            client.postgrest.rpc("delete_own_account")
            signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
    }

    /**
     * Signs up a new user with Email and Password using Supabase Auth.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String
    ): Result<SignUpOutcome> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة المصادقة غير مهيأة"))
        try {
            _authState.value = AuthState.Loading
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                this.data = buildJsonObject {
                    put("full_name", fullName.trim())
                }
            }
            val user = client.auth.currentUserOrNull()
            if (user != null && client.auth.currentSessionOrNull() != null) {
                fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                val profile = getCurrentProfile() ?: UserProfile(user.id, user.email, fullName, null, 0)
                Result.success(SignUpOutcome.SuccessLoggedIn(profile))
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.success(SignUpOutcome.NeedsEmailConfirmation(email.trim()))
            }
        } catch (e: Exception) {
            val friendlyMsg = toUserFriendlyAuthError(e)
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Signs in an existing user with Email and Password using Supabase Auth.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة المصادقة غير مهيأة"))
        try {
            _authState.value = AuthState.Loading
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                fetchOrCreateProfile(user.id, user.email, user.userMetadata)
                val profile = getCurrentProfile() ?: UserProfile(user.id, user.email, email.substringBefore('@'), null, 0)
                Result.success(profile)
            } else {
                val msg = "البريد الإلكتروني أو كلمة المرور غير صحيحة"
                _authState.value = AuthState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            val friendlyMsg = toUserFriendlyAuthError(e)
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Sends a password reset recovery email to the given email address.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة المصادقة غير مهيأة"))
        try {
            client.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = REDIRECT_URI
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val friendlyMsg = toUserFriendlyAuthError(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Resends confirmation email for newly registered user.
     */
    suspend fun resendVerificationEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة المصادقة غير مهيأة"))
        try {
            client.auth.resendEmail(
                type = OtpType.Email.SIGNUP,
                email = email.trim()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val friendlyMsg = toUserFriendlyAuthError(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Updates user password (e.g. following password reset recovery).
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
            ?: return@withContext Result.failure(IllegalStateException("خدمة المصادقة غير مهيأة"))
        try {
            client.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val friendlyMsg = toUserFriendlyAuthError(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }
}

fun toUserFriendlyAuthError(throwable: Throwable): String {
    val msg = throwable.message?.lowercase().orEmpty()
    return when {
        msg.contains("invalid login credentials") || msg.contains("invalid_credentials") ->
            "البريد الإلكتروني أو كلمة المرور غير صحيحة"
        msg.contains("user already registered") || msg.contains("already registered") ->
            "هذا البريد الإلكتروني مسجل بالفعل، يرجى تسجيل الدخول"
        msg.contains("email not confirmed") ->
            "يرجى تأكيد بريدك الإلكتروني عبر الرابط المرسل إليك قبل تسجيل الدخول"
        msg.contains("rate limit") || msg.contains("over_request_rate_limit") || msg.contains("too many requests") ->
            "تم تجاوز عدد المحاولات المسموح بها، يرجى الانتظار قليلاً والمحاولة مرة أخرى"
        msg.contains("password should be at least") || msg.contains("weak_password") ->
            "كلمة المرور يجب ألا تقل عن 8 أحرف"
        msg.contains("unable to resolve host") || msg.contains("connectexception") || msg.contains("sockettimeoutexception") || msg.contains("network") ->
            "تعذر الاتصال بالخادم، يرجى التحقق من اتصالك بالإنترنت"
        else -> throwable.localizedMessage ?: "حدث خطأ غير متوقع أثناء تسجيل الدخول"
    }
}
