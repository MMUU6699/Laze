package app.vela.ui.map

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.vela.R
import app.vela.ui.Buildings3d
import app.vela.ui.MapPoiPrefs
import app.vela.ui.ReportsLayer
import app.vela.ui.SatelliteLayer
import app.vela.ui.Topography
import app.vela.ui.Traffic
import app.vela.ui.icons.Sym
import app.vela.ui.theme.VelaTheme

/**
 * Material 3 bottom sheet for map layer customization.
 * Replaces the legacy popup menu with a clear, localized design:
 * 1. Map Type (Single-choice: Default / Satellite / Terrain).
 * 2. Map Details (Traffic, Community Reports, 3D Buildings, Place Icons).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    onDismiss: () -> Unit,
    onSatelliteToggled: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val isSatellite = SatelliteLayer.on.value
    val isTerrain = Topography.on.value
    val isDefault = !isSatellite && !isTerrain

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.map_layers_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Sym.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Map Type
            Text(
                text = stringResource(R.string.map_type_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapTypeCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.map_type_default),
                    icon = Sym.Map,
                    selected = isDefault,
                    onClick = {
                        SatelliteLayer.set(context, false)
                        Topography.set(context, false)
                        onSatelliteToggled()
                    },
                )

                MapTypeCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.map_type_satellite),
                    icon = Sym.SatelliteAlt,
                    selected = isSatellite,
                    onClick = {
                        SatelliteLayer.set(context, true)
                        Topography.set(context, false)
                        onSatelliteToggled()
                    },
                )

                MapTypeCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.map_type_terrain),
                    icon = Sym.Terrain,
                    selected = isTerrain,
                    onClick = {
                        SatelliteLayer.set(context, false)
                        Topography.set(context, true)
                        onSatelliteToggled()
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Map Details
            Text(
                text = stringResource(R.string.map_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Traffic Switch
                    MapDetailToggleRow(
                        title = stringResource(R.string.map_detail_traffic),
                        subtitle = stringResource(R.string.map_detail_traffic_subtitle),
                        icon = Sym.Traffic,
                        checked = Traffic.on.value,
                        onCheckedChange = { Traffic.set(context, it) },
                    )

                    // Community Reports Switch
                    MapDetailToggleRow(
                        title = stringResource(R.string.map_detail_reports),
                        subtitle = stringResource(R.string.map_detail_reports_subtitle),
                        icon = Sym.Warning,
                        checked = ReportsLayer.on.value,
                        onCheckedChange = { ReportsLayer.set(context, it) },
                    )

                    // 3D Buildings Switch
                    MapDetailToggleRow(
                        title = stringResource(R.string.map_detail_buildings),
                        subtitle = stringResource(R.string.map_detail_buildings_subtitle),
                        icon = Sym.Apartment,
                        checked = Buildings3d.on.value,
                        onCheckedChange = { Buildings3d.set(context, it) },
                    )

                    // Place Icons Switch
                    MapDetailToggleRow(
                        title = stringResource(R.string.map_detail_places),
                        subtitle = stringResource(R.string.map_detail_places_subtitle),
                        icon = Sym.Place,
                        checked = MapPoiPrefs.showPois.value,
                        onCheckedChange = { MapPoiPrefs.setShowPois(context, it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Visual card representing a map type selection (Default / Satellite / Terrain).
 */
@Composable
private fun MapTypeCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * A detail toggle row with accessible touch target, leading icon, title, description, and Switch.
 */
@Composable
private fun MapDetailToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// --- Previews ---

@Preview(name = "Layers BottomSheet - Arabic Light", locale = "ar", showBackground = true)
@Composable
private fun LayersBottomSheetPreviewLight() {
    VelaTheme(darkTheme = false) {
        Surface {
            LayersBottomSheetContentPreview(isSatellite = false, isTerrain = false)
        }
    }
}

@Preview(name = "Layers BottomSheet - Arabic Dark", locale = "ar", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LayersBottomSheetPreviewDark() {
    VelaTheme(darkTheme = true) {
        Surface {
            LayersBottomSheetContentPreview(isSatellite = true, isTerrain = false)
        }
    }
}

@Preview(name = "Layers BottomSheet - Arabic Large Font", locale = "ar", fontScale = 1.5f, showBackground = true)
@Composable
private fun LayersBottomSheetPreviewLargeFont() {
    VelaTheme(darkTheme = false) {
        Surface {
            LayersBottomSheetContentPreview(isSatellite = false, isTerrain = true)
        }
    }
}

@Composable
private fun LayersBottomSheetContentPreview(
    isSatellite: Boolean,
    isTerrain: Boolean,
) {
    val isDefault = !isSatellite && !isTerrain
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "الخريطة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = {}) {
                Icon(Sym.Close, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "نوع الخريطة",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapTypeCard(
                modifier = Modifier.weight(1f),
                title = "افتراضية",
                icon = Sym.Map,
                selected = isDefault,
                onClick = {},
            )
            MapTypeCard(
                modifier = Modifier.weight(1f),
                title = "قمر صناعي",
                icon = Sym.SatelliteAlt,
                selected = isSatellite,
                onClick = {},
            )
            MapTypeCard(
                modifier = Modifier.weight(1f),
                title = "تضاريس",
                icon = Sym.Terrain,
                selected = isTerrain,
                onClick = {},
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "تفاصيل الخريطة",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Column {
                MapDetailToggleRow(
                    title = "حركة المرور",
                    subtitle = "ألوان الازدحام على الطرق",
                    icon = Sym.Traffic,
                    checked = true,
                    onCheckedChange = {},
                )
                MapDetailToggleRow(
                    title = "البلاغات",
                    subtitle = "حوادث وإغلاقات يبلّغ عنها السائقون",
                    icon = Sym.Warning,
                    checked = true,
                    onCheckedChange = {},
                )
                MapDetailToggleRow(
                    title = "المباني ثلاثية الأبعاد",
                    subtitle = "مجسمات المباني بالأبعاد الثلاثية",
                    icon = Sym.Apartment,
                    checked = true,
                    onCheckedChange = {},
                )
                MapDetailToggleRow(
                    title = "أسماء الأماكن",
                    subtitle = "إظهار أيقونات المعالم والخدمات على الخريطة",
                    icon = Sym.Place,
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
    }
}
