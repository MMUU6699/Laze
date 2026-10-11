package app.vela.ui.search

import app.vela.ui.icons.Sym
import app.vela.ui.icons.SymOutlined

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
// D-pad-only operation (docs/dpad.md) — one import block so upstream merges stay clean.
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape as DpadRoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.vela.ui.dpadHighlight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.vela.R

@Composable
fun SearchBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    /** Bumped whenever [query] was set from outside the keyboard (fill-in arrow, voice): the cursor moves to the end. */
    fillTick: Int = 0,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    onBack: (() -> Unit)? = null,
    offline: Boolean = false,
    /** Tapping the offline marker re-checks the connection (user 2026-10-03). */
    onOfflineTap: (() -> Unit)? = null,
    dpadMode: Boolean = false,
    // Voice search: shown only when there's a way to service it (a voice-input app, and later an
    // on-device model). Null = no mic. Sits at the right when the field is empty, Google-style.
    onMic: (() -> Unit)? = null,
    /** Opens Your lists. Null hides the button (issue #290 moved it here from the chip row). */
    onOpenLists: (() -> Unit)? = null,
    avatarUrl: String? = null,
    onOpenProfile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // D-pad (docs/dpad.md): mere focus traversal must NOT fall into the text field (its
    // focus opens the full-screen search overlay — a trap when you're just walking the
    // chrome). In dpadMode the whole bar is ONE focus stop; pressing OK "arms" the field
    // and focuses it. We deliberately do NOT raise the soft IME: on a keypad device the
    // user types on hardware keys (which reach the focused field directly), and a shown
    // soft IME holds an active InputConnection that SWALLOWS the BACK key — reproduced
    // on-device as the "can't get out of search" trap. Hiding it lets BACK reach the app
    // and close the overlay in one press.
    var fieldArmed by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(fieldArmed) {
        if (fieldArmed) {
            runCatching { fieldFocus.requestFocus() }
            // Show/hide the soft keyboard by HOW the field was armed, not the static device type.
            // On a hybrid touch+keypad phone (e.g. Qin F21) dpadMode is always true, so keying off
            // it hid the keyboard even when the user TAPPED the bar — they couldn't type by touch
            // (alltechdev tester report 2026-07-08). Keying off the live input mode shows the
            // keyboard for a touch tap and hides it for a D-pad OK, which is what each wants.
            if (inputMode.inputMode == InputMode.Keyboard) keyboard?.hide() else keyboard?.show()
        }
    }
    // Match the darker tone of the category chips (elevated chips sit on
    // surfaceContainerLow; the default Card uses the lightest surface) so the
    // search box and the chips read as one set.
    // D-pad: the "arm the field" clickable goes on the TEXT REGION only (below), NOT the
    // whole Card — a card-level clickable made the entire bar ONE focus stop and swallowed
    // the Settings gear inside it (the gear became unreachable by D-pad; measured on-device).
    // Light theme: a WHITE bar with a real shadow and a hairline border, the way Google's sits
    // on its near-white map. surfaceContainerLow on the light land color was almost the same
    // tone as the map, so the bar dissolved into it (issue #351). Dark keeps the flat tone: a
    // shadow reads as a smear on a dark map.
    val dark = app.vela.ui.theme.isAppInDarkTheme()
    // ── Liquid Glass surface ────────────────────────────────────────────
    // Frosted translucent background, soft rounded pill, glass-highlight
    // border, and a gentle diffused shadow — modern iOS/visionOS style.
    val glassBackground = if (dark)
        androidx.compose.ui.graphics.Color(0xFF1C2026).copy(alpha = 0.82f)
    else
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f)

    val glassBorder = if (dark)
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
    else
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f)

    val glassShadowElevation = if (dark) 0.dp else 8.dp

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = glassBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = glassShadowElevation),
        border = androidx.compose.foundation.BorderStroke(
            width = if (dark) 1.dp else 1.2.dp,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    glassBorder,
                    glassBorder.copy(alpha = glassBorder.alpha * 0.4f),
                ),
            ),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A back arrow while the full-screen search page is open, else the
            // search glyph.
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Sym.ArrowBack,
                        contentDescription = stringResource(R.string.search_close_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(
                    Sym.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Placeholder and input share one centered Box so they line up exactly.
            // In dpadMode this text region is the "arm the field" focus stop (OK arms +
            // focuses the field); the gear / clear / back stay independently focusable.
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .then(
                        if (dpadMode) {
                            Modifier
                                .dpadHighlight(DpadRoundedCornerShape(20.dp))
                                .clickable { fieldArmed = true }
                                // Inset the text INSIDE the focus ring - with none, the first
                                // letter started at the Box edge and the 2dp ring stroke drew
                                // straight through it (keypad phones see this on every search).
                                .padding(horizontal = 10.dp)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The TEXT stays controlled by [query] (the shown value is always the caller's;
                // the field keeps only the selection), so a frame where the view model's echo has
                // not landed yet cannot wipe what was just typed. The cursor jumps to the END only
                // on [fillTick]: a suggestion's fill-in arrow or voice input set the text from
                // outside, and typing continues after it rather than in the middle (2026-09-22).
                var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
                LaunchedEffect(fillTick) {
                    if (fillTick > 0) field = TextFieldValue(query, TextRange(query.length))
                }
                BasicTextField(
                    value = field.copy(text = query, selection = field.selection.let {
                        // A shorter outside text (the X clearing it) must not leave the cursor past the end.
                        TextRange(it.start.coerceIn(0, query.length), it.end.coerceIn(0, query.length))
                    }),
                    onValueChange = { v ->
                        field = v
                        if (v.text != query) onQueryChange(v.text)
                    },
                    // Until armed in dpadMode the field is DISABLED, so it doesn't swallow a TOUCH tap
                    // (a live but unfocusable field ate the tap and did nothing — the "can't tap the
                    // search bar" bug on hybrid touch+keypad phones, Qin F21). Disabled lets the tap
                    // reach the Box's arm-clickable, which arms it; the field stays MOUNTED (only the
                    // enabled flag flips), so arming focuses it cleanly with no remount race.
                    enabled = !dpadMode || fieldArmed,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        // Unfocusable until armed in dpadMode; untouched otherwise.
                        .focusProperties { canFocus = !dpadMode || fieldArmed }
                        .onPreviewKeyEvent { ev ->
                            when {
                                dpadMode && onBack != null &&
                                    (ev.key == Key.Back || ev.key == Key.Escape) -> {
                                    if (ev.type == KeyEventType.KeyDown) onBack()
                                    true
                                }
                                dpadMode && ev.key == Key.DirectionDown -> {
                                    if (ev.type == KeyEventType.KeyDown) {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged {
                            fieldArmed = it.isFocused
                            onFocusChange(it.isFocused)
                        },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Sym.Close,
                        contentDescription = stringResource(R.string.search_clear_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Quiet offline indicator: a grayed globe-with-a-slash + "Offline", shown when there's no
            // connection. Hidden while typing so it doesn't crowd the clear "X".
            if (offline && query.isEmpty() && onBack == null) {
                val recheckLabel = stringResource(R.string.search_offline_recheck)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(if (onOfflineTap != null) Modifier.dpadHighlight(CircleShape).clip(CircleShape)
                            .clickable(onClickLabel = recheckLabel) { onOfflineTap() } else Modifier)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Sym.PublicOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.search_offline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            // Voice search mic: only with the field empty
            if (onMic != null && query.isEmpty()) {
                IconButton(onClick = onMic, modifier = Modifier.size(40.dp)) {
                    Icon(
                        SymOutlined.Mic,
                        contentDescription = stringResource(R.string.search_voice_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (searching) {
                CircularProgressIndicator(Modifier.padding(horizontal = 6.dp).size(22.dp), strokeWidth = 2.dp)
            }
            // Account / profile avatar button opening menu: Profile, Saved Places, Settings
            var accountMenuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { accountMenuOpen = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.search_account_menu_profile),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(30.dp).clip(CircleShape),
                        )
                    } else {
                        androidx.compose.material3.Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Sym.Person,
                                    contentDescription = stringResource(R.string.search_account_menu_profile),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = accountMenuOpen,
                    onDismissRequest = { accountMenuOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_account_menu_profile)) },
                        leadingIcon = { Icon(Sym.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            accountMenuOpen = false
                            onOpenProfile?.invoke() ?: onOpenSettings()
                        },
                    )
                    if (onOpenLists != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.search_account_menu_saved)) },
                            leadingIcon = { Icon(Sym.Bookmark, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                accountMenuOpen = false
                                onOpenLists()
                            },
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_account_menu_settings)) },
                        leadingIcon = { Icon(SymOutlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            accountMenuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }
    }
}
