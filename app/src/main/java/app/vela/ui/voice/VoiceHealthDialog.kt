package app.vela.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vela.R
import app.vela.core.voice.VoiceHealthLevel
import app.vela.core.voice.VoiceHealthStatus

@Composable
fun VoiceHealthDialog(
    status: VoiceHealthStatus?,
    onDismiss: () -> Unit,
    onInstallData: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDownloadGoogleTts: () -> Unit,
    onUseEnglish: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.voice_health_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(16.dp))

                val desc = when (status?.level) {
                    VoiceHealthLevel.NO_ENGINE -> stringResource(R.string.voice_health_no_engine)
                    VoiceHealthLevel.NOT_SUPPORTED -> stringResource(R.string.voice_health_not_supported)
                    VoiceHealthLevel.DATA_MISSING -> stringResource(R.string.voice_health_data_missing)
                    VoiceHealthLevel.OFFLINE_MISSING -> stringResource(R.string.voice_health_offline_missing)
                    VoiceHealthLevel.INIT_FAILED -> stringResource(R.string.voice_health_init_failed)
                    else -> stringResource(R.string.voice_health_data_missing)
                }

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (status?.engineLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "محرك الصوت: ${status.engineLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Action 1: Install data (or settings)
                    if (status?.level == VoiceHealthLevel.DATA_MISSING || status?.level == VoiceHealthLevel.OFFLINE_MISSING) {
                        Button(
                            onClick = onInstallData,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.voice_health_btn_install_data))
                        }
                    }

                    // Action 2: TTS Settings
                    OutlinedButton(
                        onClick = onOpenTtsSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.voice_health_btn_tts_settings))
                    }

                    // Action 3: Google TTS download (if missing or not supported)
                    if (status?.level == VoiceHealthLevel.NO_ENGINE || status?.level == VoiceHealthLevel.NOT_SUPPORTED) {
                        Button(
                            onClick = onDownloadGoogleTts,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.voice_health_btn_google_tts))
                        }
                    }

                    // Action 4: Fallback to English
                    TextButton(
                        onClick = onUseEnglish,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.voice_health_btn_use_english))
                    }

                    // Action 5: Dismiss / mute
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.voice_health_btn_dismiss))
                    }
                }
            }
        }
    }
}
