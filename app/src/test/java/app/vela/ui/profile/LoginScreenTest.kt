package app.vela.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginScreenTest {

    @Test
    fun testPasswordStrengthLevels() {
        assertEquals(PasswordStrengthLevel.EMPTY, calculatePasswordStrength(""))
        assertEquals(PasswordStrengthLevel.WEAK, calculatePasswordStrength("123"))
        assertEquals(PasswordStrengthLevel.WEAK, calculatePasswordStrength("short"))
        assertEquals(PasswordStrengthLevel.MEDIUM, calculatePasswordStrength("Password123"))
        assertEquals(PasswordStrengthLevel.STRONG, calculatePasswordStrength("P@ssw0rd!LongSecure"))
    }

    @Test
    fun testEmailValidationRegex() {
        assertTrue(isValidEmail("driver@laze.app"))
        assertTrue(isValidEmail("user.name+tag@domain.co"))
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("@domain.com"))
        assertFalse(isValidEmail("user@"))
        assertFalse(isValidEmail(""))
    }
}
