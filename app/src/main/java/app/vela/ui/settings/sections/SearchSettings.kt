package app.vela.ui.settings.sections

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.R
import app.vela.ui.map.MapViewModel
import app.vela.ui.settings.GroupDivider
import app.vela.ui.settings.SelectableRow
import app.vela.ui.settings.SettingsGroup
import app.vela.ui.settings.SettingsScaffold
import app.vela.ui.settings.SubHead
import app.vela.ui.settings.ToggleRow
import app.vela.ui.dpadHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape as DpadShape

/** Search sub-screen: the voice-search toggle, contacts search, and speech provider picker. */
@Composable
internal fun SearchSettingsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_search), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        SettingsGroup {
            ToggleRow(
                label = stringResource(R.string.settings_voice_search_toggle),
                checked = app.vela.ui.VoiceSearch.enabled.value,
                onCheckedChange = { app.vela.ui.VoiceSearch.set(context, it) },
                hint = stringResource(R.string.settings_voice_search_hint),
                switchModifier = topRow,
            )
            OtherLocationsRow()
            val scope = rememberCoroutineScope()
            val contactsPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    app.vela.ui.ContactsSearch.set(context, true)
                    scope.launch(Dispatchers.IO) { app.vela.data.ContactAddresses.reload(context) }
                }
            }
            ToggleRow(
                label = stringResource(R.string.settings_contacts_search),
                checked = app.vela.ui.ContactsSearch.enabled.value,
                onCheckedChange = { on ->
                    when {
                        !on -> app.vela.ui.ContactsSearch.set(context, false)
                        app.vela.data.ContactAddresses.hasPermission(context) -> {
                            app.vela.ui.ContactsSearch.set(context, true)
                            scope.launch(Dispatchers.IO) { app.vela.data.ContactAddresses.reload(context) }
                        }
                        else -> contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                },
                hint = stringResource(R.string.settings_contacts_search_hint),
            )
        }

        Spacer(Modifier.height(8.dp))
        VoiceCommandList()

        val voiceProviders by produceState(initialValue = emptyList<app.vela.ui.VoiceSearch.Provider>()) {
            value = withContext(Dispatchers.IO) { app.vela.ui.VoiceSearch.providers(context) }
        }
        if (voiceProviders.size > 1) {
            Spacer(Modifier.height(8.dp))
            SubHead(stringResource(R.string.settings_voice_search_engine_title))
            SettingsGroup {
                val savedPick = app.vela.ui.VoiceSearch.provider.value
                val savedValid = voiceProviders.any { it.component.flattenToString() == savedPick }
                SelectableRow(
                    stringResource(R.string.settings_voice_search_engine_default),
                    !savedValid,
                    onClick = {
                        app.vela.ui.VoiceSearch.clearProvider(context)
                    },
                )
                voiceProviders.forEach { p ->
                    GroupDivider()
                    SelectableRow(
                        p.label,
                        savedValid && p.component.flattenToString() == savedPick,
                        onClick = {
                            app.vela.ui.VoiceSearch.setProvider(context, p.component)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VoiceCommandList() {
    val examples = remember { app.vela.core.search.VoiceCommandExamples.forLanguage(app.vela.ui.AppLocale.effective().language) }
    SettingsGroup(title = stringResource(R.string.voice_capture_examples_title)) {
        val kinds = examples.map { it.kind }.distinct()
        kinds.forEachIndexed { i, kind ->
            if (i > 0) GroupDivider()
            Column(Modifier.fillMaxWidth().dpadHighlight(DpadShape(10.dp)).focusable().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(
                        when (kind) {
                            app.vela.core.search.VoiceCommandExamples.Kind.HOME -> R.string.voice_cmd_home
                            app.vela.core.search.VoiceCommandExamples.Kind.WORK -> R.string.voice_cmd_work
                            app.vela.core.search.VoiceCommandExamples.Kind.GO -> R.string.voice_cmd_go
                            app.vela.core.search.VoiceCommandExamples.Kind.ROUTE -> R.string.voice_cmd_route
                            app.vela.core.search.VoiceCommandExamples.Kind.NEARBY -> R.string.voice_cmd_nearby
                            app.vela.core.search.VoiceCommandExamples.Kind.ETA -> R.string.voice_cmd_eta
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                examples.filter { it.kind == kind }.forEach { e ->
                    Text(
                        "\u201C${e.phrase}\u201D",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Text(
        stringResource(R.string.voice_cmd_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
