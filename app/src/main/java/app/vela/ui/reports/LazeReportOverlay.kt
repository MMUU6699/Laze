package app.vela.ui.reports

import androidx.compose.runtime.mutableStateOf
import app.vela.core.backend.RoadReport

/**
 * Global bridge for rendering and interacting with Laze road reports on the map
 * without bloating MapScreen parameter signature.
 */
object LazeReportOverlay {
    val activeReports = mutableStateOf<List<RoadReport>>(emptyList())
    var onReportTap: ((RoadReport) -> Unit)? = null
}
