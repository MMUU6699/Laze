package app.vela.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vela.R
import app.vela.core.backend.RoadReportSeverity
import app.vela.core.backend.RoadReportType
import app.vela.ui.icons.Sym

private data class ReportCategoryItem(
    val type: RoadReportType,
    val titleRes: Int,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (RoadReportType, RoadReportSeverity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val categories = remember {
        listOf(
            ReportCategoryItem(
                type = RoadReportType.ACCIDENT,
                titleRes = R.string.report_type_accident,
                icon = Sym.Warning,
                color = Color(0xFFE53935)
            ),
            ReportCategoryItem(
                type = RoadReportType.TRAFFIC,
                titleRes = R.string.report_type_traffic,
                icon = Sym.DirectionsCar,
                color = Color(0xFFFB8C00)
            ),
            ReportCategoryItem(
                type = RoadReportType.CLOSURE,
                titleRes = R.string.report_type_closure,
                icon = Sym.Close,
                color = Color(0xFFD32F2F)
            ),
            ReportCategoryItem(
                type = RoadReportType.ROADWORK,
                titleRes = R.string.report_type_roadwork,
                icon = Sym.Directions,
                color = Color(0xFFF57C00)
            ),
            ReportCategoryItem(
                type = RoadReportType.HAZARD,
                titleRes = R.string.report_type_hazard,
                icon = Sym.Warning,
                color = Color(0xFFFF5722)
            ),
            ReportCategoryItem(
                type = RoadReportType.BREAKDOWN,
                titleRes = R.string.report_type_breakdown,
                icon = Sym.DirectionsCar,
                color = Color(0xFF7B1FA2)
            ),
            ReportCategoryItem(
                type = RoadReportType.FLOOD,
                titleRes = R.string.report_type_flood,
                icon = Sym.DirectionsBoat,
                color = Color(0xFF0288D1)
            ),
            ReportCategoryItem(
                type = RoadReportType.SPEED_CAMERA,
                titleRes = R.string.report_type_camera,
                icon = Sym.Speed,
                color = Color(0xFF3949AB)
            ),
            ReportCategoryItem(
                type = RoadReportType.CHECKPOINT,
                titleRes = R.string.report_type_checkpoint,
                icon = Sym.Flag,
                color = Color(0xFF1976D2)
            ),
            ReportCategoryItem(
                type = RoadReportType.SPEED_BUMP,
                titleRes = R.string.report_type_speed_bump,
                icon = Sym.Speed,
                color = Color(0xFFFFB300)
            ),
            ReportCategoryItem(
                type = RoadReportType.POTHOLE,
                titleRes = R.string.report_type_pothole,
                icon = Sym.Warning,
                color = Color(0xFF8D6E63)
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.report_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Sym.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.report_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { item ->
                    ReportTypePill(
                        item = item,
                        onClick = {
                            onSubmit(item.type, RoadReportSeverity.MODERATE)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportTypePill(
    item: ReportCategoryItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = item.color.copy(alpha = 0.16f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.titleRes),
                    tint = item.color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
    }
}
