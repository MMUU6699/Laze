package app.vela.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.R
import app.vela.core.voice.VoiceEngine
import app.vela.ui.map.MapViewModel
import app.vela.ui.settings.GroupDivider
import app.vela.ui.settings.Hint
import app.vela.ui.settings.SelectableRow
import app.vela.ui.settings.SettingsGroup
import app.vela.ui.settings.SettingsScaffold
import app.vela.ui.settings.ToggleRow
import app.vela.ui.settings.settingsAnchor
import app.vela.ui.dpadHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voice settings screen: Streamlined into a single concise screen without dead Piper/Kokoro code.
 * 1. Master toggle: تشغيل / كتم الصوت (settings_spoken_directions)
 * 2. اللهجة العراقية المبسّطة: نعم / لا (settings_voice_iraqi_dialect)
 * 3. مستوى صوت التوجيه: مرتفع (1.5f) / عادي (1.0f) / منخفض (0.7f)
 * 4. مستوى التفصيل: خياران فقط (توجيهات كاملة / تنبيهات فقط)
 * 5. نطق أسماء الشوارع: نعم / لا (settings_spoken_road_names)
 * 6. زر اختبار الصوت ينطق: «بعد مئتي متر، لف يمنة باتجاه شارع الرشيد»
 * 7. بطاقة فحص الصوت العربي مع حالة الجاهزية وزر تثبيت من متجر Play / إعدادات النظام.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VoiceSettingsScreen(vm: MapViewModel, onBack: () -> Unit, openLibrary: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(stringResource(R.string.settings_voice), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))

        // 1. Master Voice Toggle & Dialect Options
        SettingsGroup {
            ToggleRow(
                label = stringResource(R.string.settings_spoken_directions),
                checked = !state.voiceMuted,
                onCheckedChange = { vm.setSpokenDirections(it) },
                switchModifier = topRow,
            )

            if (!state.voiceMuted) {
                GroupDivider()

                // 2. Simplified Iraqi Dialect Toggle
                ToggleRow(
                    label = stringResource(R.string.settings_voice_iraqi_dialect),
                    checked = state.iraqiDialectEnabled,
                    onCheckedChange = { vm.setIraqiDialect(it) },
                    hint = stringResource(R.string.settings_voice_iraqi_dialect_hint),
                )

                GroupDivider()

                // 5. Say Street Names Toggle
                ToggleRow(
                    label = stringResource(R.string.settings_spoken_road_names),
                    checked = app.vela.ui.SpokenRoadNames.on.value,
                    onCheckedChange = { app.vela.ui.SpokenRoadNames.set(context, it) },
                    hint = stringResource(R.string.settings_spoken_road_names_hint),
                )

                GroupDivider()

                // 3. Guidance Volume (مرتفع / عادي / منخفض)
                Text(
                    stringResource(R.string.settings_voice_volume),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                val volPrefs = remember { context.getSharedPreferences("vela_settings", android.content.Context.MODE_PRIVATE) }
                var voiceVol by remember { mutableStateOf(volPrefs.getFloat("voice_volume", 1.0f)) }

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        1.5f to stringResource(R.string.settings_voice_volume_louder), // مرتفع
                        1.0f to stringResource(R.string.settings_voice_volume_normal), // عادي
                        0.7f to stringResource(R.string.settings_voice_volume_softer), // منخفض
                    ).forEach { (v, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(voiceVol - v) < 0.1f,
                            onClick = {
                                voiceVol = v
                                vm.setVoiceVolume(v)
                            },
                            label = { Text(label) },
                            shape = CircleShape,
                            modifier = Modifier.dpadHighlight(CircleShape),
                        )
                    }
                }
                Hint(stringResource(R.string.settings_voice_volume_hint))

                GroupDivider()

                // 4. Detail Level: Exactly two choices (توجيهات كاملة / تنبيهات فقط)
                Text(
                    stringResource(R.string.settings_spoken_detail),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.settingsAnchor(stringResource(R.string.settings_spoken_detail)).padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                val isBrief = app.vela.ui.SpokenDetail.mode.value == app.vela.core.nav.SpokenDetail.Mode.BRIEF
                SelectableRow(
                    label = stringResource(R.string.settings_spoken_detail_full),
                    selected = !isBrief,
                    onClick = { app.vela.ui.SpokenDetail.set(context, app.vela.core.nav.SpokenDetail.Mode.FULL) },
                )
                SelectableRow(
                    label = stringResource(R.string.settings_spoken_detail_brief),
                    selected = isBrief,
                    onClick = { app.vela.ui.SpokenDetail.set(context, app.vela.core.nav.SpokenDetail.Mode.BRIEF) },
                )
                Hint(stringResource(R.string.settings_spoken_detail_hint))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 7. Arabic Voice Health Card & 6. Voice Test Prompt Button
        var healthCheck by remember { mutableStateOf<app.vela.core.voice.VoiceHealthStatus?>(null) }
        var checkingHealth by remember { mutableStateOf(false) }

        LaunchedEffect(state.selectedEngine) {
            checkingHealth = true
            app.vela.ui.voice.VoiceHealthHelper.checkVoiceHealth(context, state.selectedEngine?.packageName) {
                healthCheck = it
                checkingHealth = false
            }
        }

        SettingsGroup {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.settings_voice_health_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val isReady = healthCheck?.isReady == true
                    Surface(
                        shape = CircleShape,
                        color = if (isReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = if (isReady) "جاهز" else if (checkingHealth) "جارٍ الفحص…" else "يحتاج إعداد",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                healthCheck?.let { h ->
                    Spacer(Modifier.height(8.dp))
                    val desc = when (h.level) {
                        app.vela.core.voice.VoiceHealthLevel.READY -> stringResource(R.string.settings_voice_health_ready)
                        app.vela.core.voice.VoiceHealthLevel.OFFLINE_MISSING -> stringResource(R.string.voice_health_offline_missing)
                        app.vela.core.voice.VoiceHealthLevel.DATA_MISSING -> stringResource(R.string.voice_health_data_missing)
                        app.vela.core.voice.VoiceHealthLevel.NOT_SUPPORTED -> stringResource(R.string.voice_health_not_supported)
                        app.vela.core.voice.VoiceHealthLevel.NO_ENGINE -> stringResource(R.string.voice_health_no_engine)
                        app.vela.core.voice.VoiceHealthLevel.INIT_FAILED -> stringResource(R.string.voice_health_init_failed)
                    }
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Actions: 6. Voice Test Prompt Button & Fix / Setup Action
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Test button speaks: «بعد مئتي متر، لف يمنة باتجاه شارع الرشيد»
                    FilledTonalButton(onClick = { vm.testVoicePrompt() }) {
                        Text(stringResource(R.string.settings_voice_health_test))
                    }

                    if (healthCheck?.isReady != true && !checkingHealth) {
                        FilledTonalButton(onClick = { vm.openVoiceHealthDialog() }) {
                            Text(stringResource(R.string.settings_voice_health_fix))
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent("com.android.settings.TTS_SETTINGS")
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_voice_system_settings))
                    }
                }
            }
        }

        // Available System Voice Engines (if multiple engines exist)
        val engines by androidx.compose.runtime.produceState<List<VoiceEngine>?>(null) {
            value = withContext(Dispatchers.IO) { vm.voiceEngines() }
        }
        val engineList = engines
        if (engineList != null && engineList.size > 1) {
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                Text(
                    stringResource(R.string.settings_voice_engine),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                engineList.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    SelectableRow(
                        label = e.label,
                        selected = state.selectedEngine?.packageName == e.packageName,
                        onClick = { vm.setVoiceEngine(e) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
