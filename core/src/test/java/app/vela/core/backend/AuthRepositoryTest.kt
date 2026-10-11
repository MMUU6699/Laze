package app.vela.core.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testParseAuthCallbackUrlWithFragment() {
        val url = "laze://auth/callback#access_token=test_access_123&refresh_token=test_refresh_456&token_type=bearer"
        val tokens = parseAuthCallbackUrl(url)
        assertEquals("test_access_123", tokens.accessToken)
        assertEquals("test_refresh_456", tokens.refreshToken)
    }

    @Test
    fun testParseAuthCallbackUrlWithCode() {
        val url = "laze://auth/callback?code=test_code_789"
        val tokens = parseAuthCallbackUrl(url)
        assertEquals("test_code_789", tokens.code)
    }

    @Test
    fun testParseAuthCallbackUrlWithError() {
        val url = "laze://auth/callback?error=access_denied&error_description=User+declined"
        val tokens = parseAuthCallbackUrl(url)
        assertEquals("access_denied", tokens.error)
        assertEquals("User declined", tokens.errorDescription)
        assertEquals("User declined", tokens.displayError)
    }

    @Test
    fun testUserFriendlyAuthErrorTranslation() {
        val invalidCreds = toUserFriendlyAuthError(Exception("Invalid login credentials"))
        assertEquals("البريد الإلكتروني أو كلمة المرور غير صحيحة", invalidCreds)

        val alreadyRegistered = toUserFriendlyAuthError(Exception("User already registered"))
        assertEquals("هذا البريد الإلكتروني مسجل بالفعل، يرجى تسجيل الدخول", alreadyRegistered)

        val rateLimit = toUserFriendlyAuthError(Exception("over_request_rate_limit"))
        assertTrue(rateLimit.contains("تجاوز"))

        val network = toUserFriendlyAuthError(java.net.UnknownHostException("Unable to resolve host"))
        assertTrue(network.contains("الإنترنت"))
    }
}
