package app.vela.core.backend

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

sealed class ReportSubmitResult {
    data class SentOnline(val report: RoadReport) : ReportSubmitResult()
    data class SavedOffline(val report: RoadReport) : ReportSubmitResult()
    data class Error(val message: String) : ReportSubmitResult()
}

@Serializable
data class OutboxReport(
    val report: RoadReport,
    val queuedAtTimestampMs: Long = System.currentTimeMillis()
)

@Serializable
private data class NearbyRpcParams(
    val center_lat: Double,
    val center_lng: Double,
    val radius_meters: Double
)

@Serializable
private data class ConfirmRpcParams(
    val p_report_id: String,
    val p_is_still_there: Boolean,
    val p_device_id: String? = null
)

/**
 * Manages community road reports, spatial querying, confirmations, local in-memory caching,
 * and an offline Outbox queue with automatic resubmission and 30-minute expiration pruning.
 */
class RoadReportRepository(
    private val context: Context? = null,
    private val authRepository: AuthRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _reports = MutableStateFlow<List<RoadReport>>(emptyList())
    val reports: StateFlow<List<RoadReport>> = _reports.asStateFlow()

    private val _outbox = MutableStateFlow<List<OutboxReport>>(emptyList())
    val outbox: StateFlow<List<OutboxReport>> = _outbox.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val outboxFile: File? by lazy {
        context?.let { File(it.filesDir, "laze_reports_outbox.json") }
    }

    private val deviceId: String by lazy {
        val prefs = context?.getSharedPreferences("laze_device_prefs", Context.MODE_PRIVATE)
        var id = prefs?.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs?.edit()?.putString("device_id", id)?.apply()
        }
        id
    }

    init {
        loadOutbox()
        startPeriodicOutboxFlusher()
    }

    private fun loadOutbox() {
        val file = outboxFile ?: return
        if (file.exists()) {
            try {
                val text = file.readText()
                if (text.isNotBlank()) {
                    val loaded = json.decodeFromString<List<OutboxReport>>(text)
                    _outbox.value = pruneExpiredOutbox(loaded)
                }
            } catch (_: Exception) {
                // If corrupt, leave empty
            }
        }
    }

    @Synchronized
    private fun persistOutbox(items: List<OutboxReport>) {
        _outbox.value = items
        val file = outboxFile ?: return
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(items))
            if (temp.exists()) {
                temp.renameTo(file)
            }
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun pruneExpiredOutbox(items: List<OutboxReport>): List<OutboxReport> {
        val now = System.currentTimeMillis()
        val maxAgeMs = 30 * 60 * 1000L // 30 minutes expiration
        return items.filter { (now - it.queuedAtTimestampMs) <= maxAgeMs }
    }

    private fun startPeriodicOutboxFlusher() {
        scope.launch {
            while (isActive) {
                delay(30_000L) // Flush check every 30 seconds
                try {
                    flushOutbox()
                } catch (_: Exception) {
                    // Periodic background loop should not terminate
                }
            }
        }
    }

    /**
     * Flushes queued outbox reports to Supabase. Prunes any reports older than 30 minutes.
     * Returns the count of successfully transmitted reports.
     */
    suspend fun flushOutbox(): Int = withContext(Dispatchers.IO) {
        val currentItems = pruneExpiredOutbox(_outbox.value)
        if (currentItems.size != _outbox.value.size) {
            persistOutbox(currentItems)
        }

        val client = SupabaseManager.getClient() ?: return@withContext 0
        if (currentItems.isEmpty()) {
            return@withContext 0
        }

        var transmitted = 0
        val remaining = mutableListOf<OutboxReport>()

        for (item in currentItems) {
            try {
                client.from("road_reports").insert(item.report)
                transmitted++
            } catch (_: Exception) {
                // Network still unreachable or server error, retain for next cycle
                remaining.add(item)
            }
        }

        persistOutbox(remaining)
        transmitted
    }

    internal fun setReportsForTesting(testReports: List<RoadReport>) {
        _reports.value = testReports
    }

    internal fun getOutboxItemsForTesting(): List<OutboxReport> {
        return _outbox.value
    }

    internal fun addOutboxItemForTesting(item: OutboxReport) {
        val current = _outbox.value.toMutableList()
        current.add(item)
        persistOutbox(current)
    }

    /**
     * Submit a new road report.
     * When online, transmits directly to Supabase and returns [ReportSubmitResult.SentOnline].
     * When offline or upon network failure, queues to persistent Outbox and returns [ReportSubmitResult.SavedOffline].
     */
    suspend fun submitReport(
        type: RoadReportType,
        latitude: Double,
        longitude: Double,
        bearing: Double? = null,
        severity: RoadReportSeverity = RoadReportSeverity.MODERATE,
        description: String? = null,
        roadName: String? = null
    ): ReportSubmitResult = withContext(Dispatchers.IO) {
        try {
            val now = Instant.now()
            val expirationMinutes = when (type) {
                RoadReportType.ACCIDENT -> 60L
                RoadReportType.TRAFFIC -> 30L
                RoadReportType.CLOSURE -> 180L
                RoadReportType.ROADWORK -> 240L
                RoadReportType.HAZARD -> 45L
                RoadReportType.BREAKDOWN -> 45L
                RoadReportType.FLOOD -> 90L
                RoadReportType.SPEED_CAMERA -> 180L
            }
            val expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES).toString()
            val createdAt = now.toString()

            val userId = authRepository?.getCurrentProfile()?.id

            val report = RoadReport(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = type,
                latitude = latitude,
                longitude = longitude,
                bearing = bearing,
                severity = severity,
                description = description,
                roadName = roadName,
                status = RoadReportStatus.ACTIVE,
                confirmationsCount = 1,
                rejectionsCount = 0,
                reliabilityScore = 1.0,
                createdAt = createdAt,
                expiresAt = expiresAt
            )

            // Optimistically update local active reports list for immediate map rendering
            val currentList = _reports.value.toMutableList()
            currentList.add(0, report)
            _reports.value = currentList

            // Increment report submission in user profile
            authRepository?.incrementReportsCount()

            // Transmit to Supabase if available
            val client = SupabaseManager.getClient()
            if (client != null) {
                try {
                    client.from("road_reports").insert(report)
                    return@withContext ReportSubmitResult.SentOnline(report)
                } catch (_: Exception) {
                    // Network or server error: queue in outbox for background retry
                    val updatedOutbox = _outbox.value.toMutableList()
                    updatedOutbox.add(OutboxReport(report, System.currentTimeMillis()))
                    persistOutbox(updatedOutbox)
                    return@withContext ReportSubmitResult.SavedOffline(report)
                }
            } else {
                // Client not configured or offline: queue in outbox
                val updatedOutbox = _outbox.value.toMutableList()
                updatedOutbox.add(OutboxReport(report, System.currentTimeMillis()))
                persistOutbox(updatedOutbox)
                return@withContext ReportSubmitResult.SavedOffline(report)
            }
        } catch (e: Exception) {
            ReportSubmitResult.Error(e.localizedMessage ?: "فشل إنشاء البلاغ")
        }
    }

    /**
     * Fetches active road reports within radiusMeters of center coordinate.
     */
    suspend fun fetchNearbyReports(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double = 25000.0
    ): Result<List<RoadReport>> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getClient()
            var fetchedReports: List<RoadReport> = emptyList()

            if (client != null) {
                try {
                    // 1. Try spatial RPC
                    fetchedReports = client.postgrest.rpc(
                        "get_nearby_road_reports",
                        NearbyRpcParams(centerLat, centerLng, radiusMeters)
                    ).decodeList<RoadReport>()
                } catch (_: Exception) {
                    // 2. Fallback to bounding box SELECT query
                    try {
                        val deltaLat = (radiusMeters / 111000.0) * 1.2
                        val deltaLng = deltaLat / Math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)

                        val minLat = centerLat - deltaLat
                        val maxLat = centerLat + deltaLat
                        val minLng = centerLng - deltaLng
                        val maxLng = centerLng + deltaLng

                        fetchedReports = client.from("road_reports")
                            .select {
                                filter {
                                    eq("status", "active")
                                    gte("latitude", minLat)
                                    lte("latitude", maxLat)
                                    gte("longitude", minLng)
                                    lte("longitude", maxLng)
                                }
                            }
                            .decodeList<RoadReport>()
                    } catch (_: Exception) {
                        // Offline or network error
                    }
                }
            }

            // Merge with local reports and eliminate duplicates
            val combinedMap = mutableMapOf<String, RoadReport>()
            fetchedReports.forEach { combinedMap[it.id] = it }
            _reports.value.forEach { combinedMap[it.id] = it }

            // Filter out cleared or expired reports
            val activeList = combinedMap.values
                .filter { it.status == RoadReportStatus.ACTIVE && it.isReliable }
                .sortedBy { it.distanceMetersTo(centerLat, centerLng) }

            _reports.value = activeList
            Result.success(activeList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Confirm ("Still there") or reject ("Cleared") an existing road report.
     */
    suspend fun confirmReport(reportId: String, isStillThere: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update in local cache immediately
            val updatedList = _reports.value.map { report ->
                if (report.id == reportId) {
                    if (isStillThere) {
                        report.copy(
                            confirmationsCount = report.confirmationsCount + 1,
                            reliabilityScore = (report.reliabilityScore + 0.5).coerceAtMost(5.0)
                        )
                    } else {
                        val newRejections = report.rejectionsCount + 1
                        val newScore = (report.reliabilityScore - 0.7).coerceAtLeast(0.0)
                        val newStatus = if (newRejections >= 3 && newScore <= 0.3) {
                            RoadReportStatus.CLEARED
                        } else {
                            report.status
                        }
                        report.copy(
                            rejectionsCount = newRejections,
                            reliabilityScore = newScore,
                            status = newStatus
                        )
                    }
                } else {
                    report
                }
            }.filter { it.status == RoadReportStatus.ACTIVE }

            _reports.value = updatedList

            // Sync to backend if available
            val client = SupabaseManager.getClient()
            if (client != null) {
                try {
                    client.postgrest.rpc(
                        "confirm_road_report",
                        ConfirmRpcParams(
                            p_report_id = reportId,
                            p_is_still_there = isStillThere,
                            p_device_id = deviceId
                        )
                    )
                } catch (_: Exception) {
                    // Offline ignore
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finds active reports located within bufferMeters along a polyline.
     * Uses bounding-box pre-filtering and fast coordinate delta checks to avoid O(P * R) heavy calculations.
     */
    fun findReportsAlongPath(
        pathPoints: List<Pair<Double, Double>>,
        bufferMeters: Double = 150.0
    ): List<RoadReport> {
        if (pathPoints.isEmpty()) return emptyList()
        val activeReports = _reports.value.filter { it.status == RoadReportStatus.ACTIVE && it.isReliable }
        if (activeReports.isEmpty()) return emptyList()

        // 1. Calculate overall BBox of the given path
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        for (i in pathPoints.indices) {
            val (lat, lng) = pathPoints[i]
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }
        val bufferDegLat = (bufferMeters / 111_000.0) * 1.5
        val cosMid = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.01)
        val bufferDegLng = bufferDegLat / cosMid

        val bboxMinLat = minLat - bufferDegLat
        val bboxMaxLat = maxLat + bufferDegLat
        val bboxMinLng = minLng - bufferDegLng
        val bboxMaxLng = maxLng + bufferDegLng

        // 2. Fast candidate filter by BBox
        val candidateReports = activeReports.filter { r ->
            r.latitude in bboxMinLat..bboxMaxLat && r.longitude in bboxMinLng..bboxMaxLng
        }
        if (candidateReports.isEmpty()) return emptyList()

        // 3. Coordinate delta pre-filtering before haversine check
        return candidateReports.filter { report ->
            val rLat = report.latitude
            val rLng = report.longitude
            pathPoints.any { (pLat, pLng) ->
                kotlin.math.abs(rLat - pLat) <= bufferDegLat &&
                kotlin.math.abs(rLng - pLng) <= bufferDegLng &&
                report.distanceMetersTo(pLat, pLng) <= bufferMeters
            }
        }
    }
}
