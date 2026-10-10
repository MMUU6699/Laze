package app.vela.core.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile in Laze, synchronized with Supabase public.profiles.
 */
@Serializable
data class UserProfile(
    @SerialName("id")
    val id: String = "",

    @SerialName("email")
    val email: String? = null,

    @SerialName("full_name")
    val fullName: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null,

    @SerialName("reports_submitted")
    val reportsSubmitted: Int = 0,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "مستخدم Laze"
}
