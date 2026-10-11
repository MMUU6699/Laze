package app.vela.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.R
import app.vela.core.backend.SignUpOutcome
import app.vela.core.backend.UserProfile
import app.vela.ui.icons.Sym

/**
 * Full-screen Authentication Gate for Laze.
 * Supports:
 * - Official Google Sign-In with official G logo
 * - Tabbed Email & Password Login and Sign-Up
 * - Password visibility toggle and strength indicator
 * - Forgot Password dialog with reset email dispatch
 * - Verification screen for unconfirmed email accounts
 * - Proper RTL and LTR text inputs for email/password
 * - Accessible 48dp+ touch targets and scrollable container
 */
@Composable
fun LoginScreen(
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: ((email: String, password: String, onResult: (Result<UserProfile>) -> Unit) -> Unit)? = null,
    onEmailSignUp: ((email: String, password: String, fullName: String, onResult: (Result<SignUpOutcome>) -> Unit) -> Unit)? = null,
    onForgotPassword: ((email: String, onResult: (Result<Unit>) -> Unit) -> Unit)? = null,
    onResendVerification: ((email: String, onResult: (Result<Unit>) -> Unit) -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Login, 1 = Sign Up
    var isLocalLoading by remember { mutableStateOf(false) }
    var activeError by remember { mutableStateOf<String?>(null) }
    var activeSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Form inputs
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Password visibility
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Verification screen state
    var verificationPendingEmail by remember { mutableStateOf<String?>(null) }

    // Forgot password dialog state
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val effectiveLoading = isLoading || isLocalLoading
    val displayedError = activeError ?: errorMessage

    // Password strength calculation
    val passwordStrength = remember(password) {
        calculatePasswordStrength(password)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Emblem
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Sym.Directions,
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title: Laze
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = stringResource(R.string.auth_sign_in_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Banner: Error
            if (!displayedError.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Sym.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = displayedError,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Banner: Success / Info
            if (!activeSuccessMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Sym.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeSuccessMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Verification Pending Screen (if user registered and needs verification)
            if (verificationPendingEmail != null) {
                EmailVerificationCard(
                    email = verificationPendingEmail.orEmpty(),
                    isLoading = effectiveLoading,
                    onResend = {
                        isLocalLoading = true
                        activeError = null
                        onResendVerification?.invoke(verificationPendingEmail.orEmpty()) { result ->
                            isLocalLoading = false
                            if (result.isSuccess) {
                                activeSuccessMessage = "تم إرسال رابط التحقق مجدداً إلى بريدك."
                            } else {
                                activeError = result.exceptionOrNull()?.message ?: "فشل إعادة الإرسال"
                            }
                        }
                    },
                    onBackToLogin = {
                        verificationPendingEmail = null
                        selectedTab = 0
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            // Official Google Sign-In Button
            GoogleSignInButton(
                onClick = onGoogleSignIn,
                enabled = !effectiveLoading,
                isLoading = effectiveLoading && selectedTab == -1
            )

            Spacer(modifier = Modifier.height(20.dp))

            // OR Divider
            OrDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Tabs: تسجيل الدخول / إنشاء حساب
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sign In Tab
                    val isSignIn = selectedTab == 0
                    val signInBg by animateColorAsState(
                        targetValue = if (isSignIn) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "signInBg"
                    )
                    val signInTextColor by animateColorAsState(
                        targetValue = if (isSignIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "signInTextColor"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(signInBg)
                            .clickable(enabled = !effectiveLoading) {
                                selectedTab = 0
                                activeError = null
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.auth_tab_login),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSignIn) FontWeight.Bold else FontWeight.Medium,
                            color = signInTextColor
                        )
                    }

                    // Sign Up Tab
                    val isSignUp = selectedTab == 1
                    val signUpBg by animateColorAsState(
                        targetValue = if (isSignUp) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "signUpBg"
                    )
                    val signUpTextColor by animateColorAsState(
                        targetValue = if (isSignUp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "signUpTextColor"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(signUpBg)
                            .clickable(enabled = !effectiveLoading) {
                                selectedTab = 1
                                activeError = null
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.auth_tab_signup),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium,
                            color = signUpTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form inputs
            if (selectedTab == 0) {
                // ==========================================
                // SIGN IN TAB
                // ==========================================
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_email)) },
                    leadingIcon = {
                        Icon(Sym.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_password)) },
                    leadingIcon = {
                        Icon(Sym.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Sym.Visibility else Sym.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (email.isNotBlank() && password.isNotBlank()) {
                                isLocalLoading = true
                                activeError = null
                                onEmailSignIn?.invoke(email, password) { res ->
                                    isLocalLoading = false
                                    if (res.isFailure) {
                                        activeError = res.exceptionOrNull()?.message ?: "خطأ في تسجيل الدخول"
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Forgot password button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            forgotPasswordEmail = email
                            showForgotPasswordDialog = true
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.auth_forgot_password),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sign In Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (!isValidEmail(email)) {
                            activeError = "يرجى إدخال بريد إلكتروني صالح"
                            return@Button
                        }
                        if (password.length < 6) {
                            activeError = "يرجى إدخال كلمة المرور"
                            return@Button
                        }
                        isLocalLoading = true
                        activeError = null
                        onEmailSignIn?.invoke(email, password) { res ->
                            isLocalLoading = false
                            if (res.isFailure) {
                                activeError = res.exceptionOrNull()?.message ?: "خطأ في تسجيل الدخول"
                            }
                        }
                    },
                    enabled = !effectiveLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (effectiveLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_tab_login),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // ==========================================
                // SIGN UP TAB
                // ==========================================
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_full_name)) },
                    leadingIcon = {
                        Icon(Sym.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_email)) },
                    leadingIcon = {
                        Icon(Sym.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_password)) },
                    leadingIcon = {
                        Icon(Sym.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Sym.Visibility else Sym.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password strength bar
                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PasswordStrengthIndicator(passwordStrength)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        activeError = null
                    },
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
                    leadingIcon = {
                        Icon(Sym.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Sym.Visibility else Sym.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Terms agreement text
                Text(
                    text = stringResource(R.string.auth_terms_agreement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Sign Up Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (fullName.trim().isBlank()) {
                            activeError = "يرجى إدخال اسمك الكامل"
                            return@Button
                        }
                        if (!isValidEmail(email)) {
                            activeError = "يرجى إدخال بريد إلكتروني صالح"
                            return@Button
                        }
                        if (password.length < 8) {
                            activeError = "يجب ألا تقل كلمة المرور عن 8 أحرف"
                            return@Button
                        }
                        if (password != confirmPassword) {
                            activeError = "كلمتا المرور غير متطابقتين"
                            return@Button
                        }

                        isLocalLoading = true
                        activeError = null
                        onEmailSignUp?.invoke(email, password, fullName) { res ->
                            isLocalLoading = false
                            if (res.isSuccess) {
                                when (val outcome = res.getOrNull()) {
                                    is SignUpOutcome.NeedsEmailConfirmation -> {
                                        verificationPendingEmail = outcome.email
                                    }
                                    is SignUpOutcome.SuccessLoggedIn -> {
                                        // Handled by state listener
                                    }
                                    null -> Unit
                                }
                            } else {
                                activeError = res.exceptionOrNull()?.message ?: "فشل إنشاء الحساب"
                            }
                        }
                    },
                    enabled = !effectiveLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (effectiveLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_create_account),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.auth_forgot_password_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.auth_forgot_password_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { forgotPasswordEmail = it },
                        label = { Text(stringResource(R.string.auth_email)) },
                        singleLine = true,
                        textStyle = TextStyle(textDirection = TextDirection.Ltr),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isValidEmail(forgotPasswordEmail)) {
                            activeError = "يرجى إدخال بريد إلكتروني صالح"
                            showForgotPasswordDialog = false
                            return@Button
                        }
                        showForgotPasswordDialog = false
                        isLocalLoading = true
                        activeError = null
                        onForgotPassword?.invoke(forgotPasswordEmail.trim()) { res ->
                            isLocalLoading = false
                            if (res.isSuccess) {
                                activeSuccessMessage = "تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك."
                            } else {
                                activeError = res.exceptionOrNull()?.message ?: "فشل إرسال رابط الاستعادة"
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.auth_send_reset_link))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/**
 * Official Google Sign-In Button compliant with Google Identity Guidelines.
 */
@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.auth_continue_with_google),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.auth_or_divider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

internal enum class PasswordStrengthLevel(val label: String, val color: Color, val progress: Float) {
    EMPTY("", Color.Transparent, 0f),
    WEAK("ضعيفة", Color(0xFFE53935), 0.33f),
    MEDIUM("متوسطة", Color(0xFFFB8C00), 0.66f),
    STRONG("قوية", Color(0xFF43A047), 1f)
}

internal fun calculatePasswordStrength(pass: String): PasswordStrengthLevel {
    if (pass.isEmpty()) return PasswordStrengthLevel.EMPTY
    var score = 0
    if (pass.length >= 8) score++
    if (pass.any { it.isDigit() } && pass.any { it.isLetter() }) score++
    if (pass.length >= 12 || pass.any { !it.isLetterOrDigit() }) score++

    return when (score) {
        1 -> PasswordStrengthLevel.WEAK
        2 -> PasswordStrengthLevel.MEDIUM
        3 -> PasswordStrengthLevel.STRONG
        else -> PasswordStrengthLevel.WEAK
    }
}

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

internal fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

@Composable
private fun PasswordStrengthIndicator(strength: PasswordStrengthLevel) {
    if (strength == PasswordStrengthLevel.EMPTY) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "قوة كلمة المرور: ${strength.label}",
                style = MaterialTheme.typography.labelSmall,
                color = strength.color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { strength.progress },
            color = strength.color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun EmailVerificationCard(
    email: String,
    isLoading: Boolean,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Sym.Mail,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.auth_verify_email_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.auth_verify_email_desc, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onResend,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(stringResource(R.string.auth_resend_verification))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBackToLogin,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.auth_back_to_login))
            }
        }
    }
}
