package app.vela.core.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `parseAuthCallbackUrl extracts fragment tokens successfully`() {
        val url = "laze://auth/callback#access_token=eyJhbGciOiJIUzI1NiJ9.test&refresh_token=refresh_123&token_type=bearer"
        val tokens = parseAuthCallbackUrl(url)

        assertEquals("eyJhbGciOiJIUzI1NiJ9.test", tokens.accessToken)
        assertEquals("refresh_123", tokens.refreshToken)
        assertNull(tokens.code)
        assertNull(tokens.error)
    }

    @Test
    fun `parseAuthCallbackUrl extracts query code successfully`() {
        val url = "laze://auth/callback?code=oauth_code_xyz"
        val tokens = parseAuthCallbackUrl(url)

        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertEquals("oauth_code_xyz", tokens.code)
        assertNull(tokens.error)
    }

    @Test
    fun `parseAuthCallbackUrl extracts error description`() {
        val url = "laze://auth/callback?error=access_denied&error_description=The+user+denied+access"
        val tokens = parseAuthCallbackUrl(url)

        assertNull(tokens.accessToken)
        assertNull(tokens.code)
        assertEquals("The user denied access", tokens.error)
    }

    @Test
    fun `parseAuthCallbackUrl handles malformed url gracefully`() {
        val url = "laze://auth/callback"
        val tokens = parseAuthCallbackUrl(url)

        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.code)
        assertNull(tokens.error)
    }
}
