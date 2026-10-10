package app.vela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vela.R
import app.vela.ui.icons.Sym

/** First run: what Laze is, then a single Get Started button. */
@Composable
fun WelcomeScreen(vm: app.vela.ui.map.MapViewModel, onGetStarted: () -> Unit) {
    val context = LocalContext.current
    WelcomeIntro(onNext = {
        GoogleFree.set(context, true)
        onGetStarted()
    })
}

/** What Laze is and why, then a single Get-started button. */
@Composable
private fun WelcomeIntro(onNext: () -> Unit) {
    val scroll = rememberScrollState()
    val minH = LocalConfiguration.current.screenHeightDp.dp
    val dpadFirst = rememberDpadFirstDevice()
    LaunchedEffect(dpadFirst) {
        if (dpadFirst) repeat(20) {
            if (scroll.maxValue > 0) { scroll.scrollTo(scroll.maxValue); return@LaunchedEffect }
            kotlinx.coroutines.delay(50)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(scroll)
                .heightIn(min = minH)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            VelaMark(84.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            WelcomeFeature(
                Sym.VisibilityOff,
                stringResource(R.string.welcome_feature_no_tracking_title),
                stringResource(R.string.welcome_feature_private_body),
            )
            WelcomeFeature(
                Sym.Place,
                stringResource(R.string.welcome_feature_places_title),
                stringResource(R.string.welcome_feature_places_body),
            )
            WelcomeFeature(
                Sym.Favorite,
                stringResource(R.string.welcome_feature_open_source_title),
                stringResource(R.string.welcome_feature_open_source_body),
            )
            Spacer(Modifier.height(40.dp))
            WelcomeButton(stringResource(R.string.welcome_get_started), onGetStarted = onNext)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The Laze mark as the launcher draws it: the two-tone sail on the brand gradient. */
@Composable
private fun VelaMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 27))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFF0D3D43), androidx.compose.ui.graphics.Color(0xFF149387)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** The filled primary button of a first-run page, which takes focus on a D-pad device. */
@Composable
private fun WelcomeButton(label: String, autoFocus: Boolean = true, onGetStarted: () -> Unit) {
    val fr = remember { FocusRequester() }
    val dpadFirst = rememberDpadFirstDevice()
    LaunchedEffect(dpadFirst) {
        if (dpadFirst && autoFocus) repeat(40) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(fr)
            .dpadHighlight(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .onKeyEvent { ev ->
                if ((ev.key == Key.DirectionCenter || ev.key == Key.Enter) && ev.type == KeyEventType.KeyUp) {
                    onGetStarted(); true
                } else {
                    false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { onGetStarted() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The release notes of the build just updated to, shown once (see [WhatsNew]). */
@Composable
fun WhatsNewPrompt(version: String, notes: String, onOpenRelease: () -> Unit, onDismiss: () -> Unit) {
    VelaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.whatsnew_title, version),
        confirmText = stringResource(R.string.whatsnew_got_it),
        onConfirm = onDismiss,
        dismissText = stringResource(R.string.whatsnew_full_notes),
        onDismiss = onOpenRelease,
        dismissLowEmphasis = true,
        icon = { Icon(Sym.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        text = {
          Column {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .dpadHighlight()
                    .dpadClickable { WhatsNew.setEnabled(context, !WhatsNew.enabled.value) },
            ) {
                androidx.compose.material3.Checkbox(checked = WhatsNew.enabled.value, onCheckedChange = null)
                Text(
                    stringResource(R.string.whatsnew_show_after_updates),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
          }
        },
    )
}

/** The one-time, low-pressure donation prompt (see [Onboarding] for the etiquette). */
@Composable
fun DonatePrompt(onDonate: () -> Unit, onDismiss: () -> Unit) {
    VelaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.welcome_donate_title),
        confirmText = stringResource(R.string.welcome_donate_confirm),
        onConfirm = onDonate,
        dismissText = stringResource(R.string.welcome_donate_dismiss),
        onDismiss = onDismiss,
        icon = { Icon(Sym.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        text = { Text(stringResource(R.string.welcome_donate_body)) },
    )
}
