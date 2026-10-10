package app.vela.core.backend

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

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
 * Manages community road reports, spatial querying, confirmations, and in-memory caching.
 */
class RoadReportRepository(
    private val context: Context? = null,
    private val authRepository: AuthRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _reports = MutableStateFlow<List<RoadReport>>(emptyList())
    val reports: StateFlow<List<RoadReport>> = _reports.asStateFlow()

    private val deviceId: String by lazy {
        val prefs = context?.getSharedPreferences("laze_device_prefs", Context.MODE_PRIVATE)
        var id = prefs?.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs?.edit()?.putString("device_id", id)?.apply()
        }
        id
    }

    internal fun setReportsForTesting(testReports: List<RoadReport>) {
        _reports.value = testReports
    }

    /**
     * Submit a new road report (works both online with Supabase and offline with local cache).
     */
    suspend fun submitReport(
        type: RoadReportType,
        latitude: Double,
        longitude: Double,
        bearing: Double? = null,
        severity: RoadReportSeverity = RoadReportSeverity.MODERATE,
        description: String? = null,
        roadName: String? = null
    ): Result<RoadReport> = withContext(Dispatchers.IO) {
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

            // Optimistically update local state
            val currentList = _reports.value.toMutableList()
            currentList.add(0, report)
            _reports.value = currentList

            // Increment report submission in user profile
            authRepository?.incrementReportsCount()

            // If Supabase is available, sync to remote database
            val client = SupabaseManager.getClient()
            if (client != null) {
                try {
                    client.from("road_reports").insert(report)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Still return success because optimistic update succeeded locally
                }
            }

            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
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
                } catch (e: Exception) {
                    // 2. Fallback to bounding box SELECT query
                    try {
                        // 1 deg lat ~= 111 km
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
                    } catch (e2: Exception) {
                        e2.printStackTrace()
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
                } catch (e: Exception) {
                    e.printStackTrace()
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
