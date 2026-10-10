package app.vela.core.backend

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testUserProfileSerialization() {
        val profile = UserProfile(
            id = "user-abc-123",
            email = "driver@laze.app",
            fullName = "سائق Laze",
            avatarUrl = "https://example.com/avatar.jpg",
            reportsSubmitted = 15
        )

        val encoded = json.encodeToString(UserProfile.serializer(), profile)
        val decoded = json.decodeFromString(UserProfile.serializer(), encoded)

        assertEquals("user-abc-123", decoded.id)
        assertEquals("driver@laze.app", decoded.email)
        assertEquals("سائق Laze", decoded.fullName)
        assertEquals("سائق Laze", decoded.displayName)
        assertEquals(15, decoded.reportsSubmitted)
    }

    @Test
    fun testDisplayNameFallbacks() {
        val withName = UserProfile(id = "1", email = "test@laze.app", fullName = "أحمد محمد")
        assertEquals("أحمد محمد", withName.displayName)

        val withEmailOnly = UserProfile(id = "2", email = "khalid@laze.app", fullName = null)
        assertEquals("khalid", withEmailOnly.displayName)

        val empty = UserProfile(id = "3", email = null, fullName = null)
        assertEquals("مستخدم Laze", empty.displayName)
    }
}
