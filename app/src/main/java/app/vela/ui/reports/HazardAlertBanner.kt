package app.vela.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vela.R
import app.vela.core.backend.RoadReport
import app.vela.core.backend.RoadReportType
import app.vela.ui.icons.Sym
import kotlinx.coroutines.delay

@Composable
fun HazardAlertBanner(
    report: RoadReport?,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onConfirm: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss confirmation prompt after 15 seconds to minimize driver distraction
    LaunchedEffect(report?.id) {
        if (report != null) {
            delay(15_000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = report != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier
    ) {
        if (report == null) return@AnimatedVisibility

        val (typeColor, iconVector) = when (report.type) {
            RoadReportType.ACCIDENT -> Color(0xFFE53935) to Sym.Warning
            RoadReportType.TRAFFIC -> Color(0xFFFB8C00) to Sym.DirectionsCar
            RoadReportType.CLOSURE -> Color(0xFFD32F2F) to Sym.Close
            RoadReportType.ROADWORK -> Color(0xFFF57C00) to Sym.Directions
            RoadReportType.HAZARD -> Color(0xFFFF5722) to Sym.Warning
            RoadReportType.BREAKDOWN -> Color(0xFF7B1FA2) to Sym.DirectionsCar
            RoadReportType.FLOOD -> Color(0xFF0288D1) to Sym.DirectionsBoat
            RoadReportType.SPEED_CAMERA -> Color(0xFF3949AB) to Sym.Speed
            RoadReportType.CHECKPOINT -> Color(0xFF1976D2) to Sym.Flag
            RoadReportType.SPEED_BUMP -> Color(0xFFFFB300) to Sym.Speed
            RoadReportType.POTHOLE -> Color(0xFF8D6E63) to Sym.Warning
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 8.dp,
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = typeColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = typeColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = report.type.titleAr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val distStr = if (distanceMeters != null && distanceMeters > 0) {
                                if (distanceMeters >= 1000) {
                                    String.format(java.util.Locale.US, "بعد %.1f كم", distanceMeters / 1000.0)
                                } else {
                                    "بعد ${distanceMeters.toInt()} م"
                                }
                            } else {
                                "على مسارك الحالي"
                            }
                            Text(
                                text = distStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Sym.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (onConfirm != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "هل ما زال هذا العائق موجوداً؟",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onConfirm(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Sym.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.report_still_there),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onConfirm(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Sym.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.report_cleared),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
