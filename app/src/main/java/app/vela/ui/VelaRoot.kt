package app.vela.ui

import kotlinx.coroutines.flow.first
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vela.R
import app.vela.ui.place.PlaceOverlays
import androidx.hilt.navigation.compose.hiltViewModel
import app.vela.ui.map.MapScreen
import app.vela.ui.map.MapViewModel
import app.vela.ui.settings.SettingsScreen
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Root composable. One [MapViewModel] instance is shared between the map and
 * settings (settings tweaks the same map/voice state), with a single
 * navigation host above the persistent map, passing the shared VM to every page. The first-run
 * [WelcomeScreen] gates everything else; the one-time [DonatePrompt] overlays the
 * map once the app has earned it (see [Onboarding]).
 */
@Composable
fun VelaRoot(vm: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val authState by vm.authState.collectAsState()

    when (val state = authState) {
        is app.vela.core.backend.AuthState.Loading -> {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return
        }
        is app.vela.core.backend.AuthState.Unauthenticated,
        is app.vela.core.backend.AuthState.Error -> {
            val errorMsg = (state as? app.vela.core.backend.AuthState.Error)?.message
            app.vela.ui.profile.LoginScreen(
                onGoogleSignIn = { vm.signInWithGoogle(context) },
                onEmailSignIn = { email, password, onRes -> vm.signInWithEmail(email, password, onRes) },
                onEmailSignUp = { email, password, name, onRes -> vm.signUpWithEmail(email, password, name, onRes) },
                onForgotPassword = { email, onRes -> vm.sendPasswordResetEmail(email, onRes) },
                onResendVerification = { email, onRes -> vm.resendVerificationEmail(email, onRes) },
                errorMessage = errorMsg,
            )
            return
        }
        is app.vela.core.backend.AuthState.Authenticated -> {
            // Authorized! Proceed to welcome / map screens.
        }
    }

    if (!Onboarding.welcomeDone.value) {
        WelcomeScreen(vm, onGetStarted = { Onboarding.completeWelcome(context) })
        return
    }

    // Day/night theme clock (issue #262). ONE ticker for the whole app: the sun answer lives in
    // AppTheme.night, so every composable that asks "am I dark" reads a plain state instead of
    // running the sun math itself. A minute is far finer than the thing being detected (sunset
    // moves the boundary once a day) and costs a closed-form calculation per tick.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            // Waits while the app is in the background: the effect outlives onStop, and nothing
            // drawn needs the answer until the app is back.
            app.vela.ui.AppVisibility.foreground.first { it }
            app.vela.ui.theme.AppTheme.refreshNight()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    val navController = androidx.navigation.compose.rememberNavController()
    val page by navController.currentBackStackEntryAsState()
    val showSettings = page?.destination?.route?.let { it != app.vela.ui.settings.MAP_ROUTE } == true
    ZoomKeys.mapShowing = !showSettings
    val openSettings: () -> Unit = {
        navController.navigate(app.vela.ui.settings.SettingsSection.HUB.route) { launchSingleTop = true }
    }
    // Location permission launcher for onboarding. The map no longer fires the raw system dialog on
    // its own (see MapScreen); this owns the first ask. A grant starts location immediately (coarse-
    // only works too, via the NETWORK provider); a denial just moves on and leaves search/browse
    // working, with the locate FAB re-asking later.
    // A coarse-only grant is easy to pick without meaning to, and it looks broken later (a wide
    // circle for a dot, navigation that can't start). Say what it means ONCE, right when it happens;
    // "Allow precise" re-runs the request, which Android shows as its approximate-to-precise choice.
    var approxPrompt by rememberSaveable { mutableStateOf(false) }
    var approxAsked by rememberSaveable { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) vm.startLocation()
        if (!fine && coarse && !approxAsked) {
            approxAsked = true
            approxPrompt = true
        } else {
            Onboarding.dismissLocationPrompt(context)
        }
    }
    // Ask for location the moment onboarding reaches this step - no separate rationale screen, since
    // the welcome screen is context enough for a maps app (one less thing to tap through). Fires once
    // as the block enters composition; the result arms the notification step next.
    if (Onboarding.showLocationPrompt.value) {
        LaunchedEffect(Unit) {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    // Notifications come right after location: turn-by-turn keeps your next move in a notification,
    // so asking during setup means the first navigation just works. Graceful on a no - a denial gets
    // one plain-words are-you-sure, and skipping never blocks anything (the nav start re-asks).
    var notifReasked by rememberSaveable { mutableStateOf(false) }
    var notifSure by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || notifReasked) Onboarding.dismissNotifPrompt(context) else notifSure = true
    }
    var notifRequested by rememberSaveable { mutableStateOf(false) }
    if (Onboarding.showNotifPrompt.value && !notifRequested) {
        LaunchedEffect(Unit) {
            notifRequested = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (approxPrompt) {
        VelaDialog(
            onDismissRequest = { approxPrompt = false; Onboarding.dismissLocationPrompt(context) },
            title = stringResource(R.string.loc_approx_title),
            confirmText = stringResource(R.string.loc_approx_allow),
            onConfirm = {
                approxPrompt = false
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            dismissText = stringResource(R.string.loc_approx_keep),
            onDismiss = { approxPrompt = false; Onboarding.dismissLocationPrompt(context) },
            dismissLowEmphasis = true,
            text = { Text(stringResource(R.string.loc_approx_body)) },
        )
    }
    if (notifSure) {
        VelaDialog(
            onDismissRequest = { notifSure = false; Onboarding.dismissNotifPrompt(context) },
            title = stringResource(R.string.notif_ask_title),
            confirmText = stringResource(R.string.notif_ask_allow),
            onConfirm = {
                notifSure = false
                notifReasked = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            dismissText = stringResource(R.string.notif_ask_skip),
            onDismiss = { notifSure = false; Onboarding.dismissNotifPrompt(context) },
            dismissLowEmphasis = true,
            text = { Text(stringResource(R.string.notif_ask_body)) },
        )
    }
    // Interface-size override (Settings -> Appearance): scale all Compose UI while the map
    // AndroidView keeps rendering at native size. Density scales dp AND sp together.
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val uiScale = app.vela.ui.UiScale.factor.value
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            baseDensity.density * uiScale,
            baseDensity.fontScale,
        ),
    ) {
    Box {
        // MapScreen stays composed even while Settings is open, and Settings draws OVER it as an
        // opaque overlay. Swapping the two out instead disposed the remembered MapLibre MapView, so
        // returning from Settings rebuilt the map from scratch and it snapped back to the stale
        // center at the default zoom, losing the user's pan/zoom (a reported bug).
        MapScreen(
            vm = vm,
            onOpenSettings = openSettings,
            // The no-voice heads-up's pill: straight into the voice library.
            onOpenVoiceSettings = {
                openSettings()
                navController.navigate(app.vela.ui.settings.SettingsSection.VOICE.route)
                navController.currentBackStackEntry?.savedStateHandle?.set("openLibrary", true)
            },
            onOpenProfile = {
                openSettings()
                navController.navigate(app.vela.ui.settings.SettingsSection.PROFILE.route)
            },
        )
        SettingsScreen(vm = vm, navController = navController)
        if (!showSettings) {
            // Location is asked via the system dialog fired above (no rationale screen). The voice
            // offer is the next step once that's answered.
            if (Onboarding.showVoicePrompt.value) {
                VoicePrompt(
                    onTestVoice = { vm.testVoice() },
                    onSetupVoice = {
                        Onboarding.dismissVoicePrompt(context)
                        vm.openVoiceHealthDialog()
                    },
                    onDismiss = { Onboarding.dismissVoicePrompt(context) },
                )
            } else if (Onboarding.showDonatePrompt.value) {
                DonatePrompt(
                    onDonate = {
                        Onboarding.openDonate(context)
                        Onboarding.dismissDonatePrompt(context)
                    },
                    onDismiss = { Onboarding.dismissDonatePrompt(context) },
                )
            } else if (app.vela.ui.WhatsNew.notes.value != null) {
                // After an update: this build's release notes, once. Last in the chain so it
                // never stacks on a setup prompt.
                WhatsNewPrompt(
                    version = app.vela.ui.WhatsNew.version.value,
                    notes = app.vela.ui.WhatsNew.notes.value.orEmpty(),
                    onOpenRelease = {
                        app.vela.ui.WhatsNew.openRelease(context)
                        app.vela.ui.WhatsNew.dismiss(context)
                    },
                    onDismiss = { app.vela.ui.WhatsNew.dismiss(context) },
                )
            }
        }
        // Full-screen photo viewer + reviews page render HERE, in the activity's own edge-to-edge
        // window (not a child Dialog) — genuinely full-screen and rotation-safe. Last child = top
        // z-order, above the map + sheet + settings.
        PlaceOverlays()
    }
    }
}

/** Lightweight onboarding check for system TTS health and Arabic speech availability. */
@Composable
private fun VoicePrompt(
    onTestVoice: () -> Unit,
    onSetupVoice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var health by remember { mutableStateOf<app.vela.core.voice.VoiceHealthStatus?>(null) }
    LaunchedEffect(Unit) {
        app.vela.ui.voice.VoiceHealthHelper.checkVoiceHealth(context) {
            health = it
        }
    }

    val currentHealth = health ?: return

    val isReady = currentHealth.canSpeak
    val title = if (isReady) {
        stringResource(R.string.onboarding_voice_ready_title)
    } else {
        stringResource(R.string.onboarding_voice_setup_needed)
    }
    val desc = if (isReady) {
        stringResource(R.string.onboarding_voice_ready_desc)
    } else {
        stringResource(R.string.onboarding_voice_setup_desc)
    }

    VelaDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = if (isReady) stringResource(R.string.settings_voice_health_test) else stringResource(R.string.settings_voice_health_fix),
        onConfirm = {
            if (isReady) onTestVoice() else onSetupVoice()
        },
        dismissText = stringResource(R.string.welcome_continue),
        onDismiss = onDismiss,
        dismissLowEmphasis = !isReady,
        text = {
            Text(desc)
        },
    )
}
