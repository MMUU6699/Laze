package app.vela.core.routing

import app.vela.core.BuildConfig
import app.vela.core.data.RouteGeometry
import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Routing provider backed by OSRM (either self-hosted Iraq server or open community server).
 */
class OsrmRoutingProvider(
    private val httpClient: OkHttpClient,
    val baseUrl: String = BuildConfig.IRAQ_OSRM_URL.ifBlank { "https://routing.openstreetmap.de" },
    override val name: String = if (BuildConfig.IRAQ_OSRM_URL.isNotBlank()) "OSRM-Iraq" else "OSRM-Community",
) : RoutingProvider {

    override val source: RouteSource = RouteSource.OSRM

    override suspend fun route(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        waypoints: List<LatLng>,
        avoidTolls: Boolean,
        avoidHighways: Boolean,
        avoidFerries: Boolean,
        departBearingDeg: Double?,
        timeoutMs: Long?,
    ): List<Route> = withContext(Dispatchers.IO) {
        val allPoints = buildList {
            add(origin)
            addAll(waypoints)
            add(destination)
        }

        if (allPoints.size < 2) return@withContext emptyList()

        if (waypoints.isEmpty()) {
            RouteGeometry.route(
                http = httpClient,
                origin = origin,
                dest = destination,
                mode = mode,
                avoidTolls = avoidTolls,
                avoidHighways = avoidHighways,
                avoidFerries = avoidFerries,
                departBearingDeg = departBearingDeg,
                callTimeoutMs = timeoutMs,
            )
        } else {
            RouteGeometry.routeVia(
                http = httpClient,
                waypoints = allPoints,
                mode = mode,
                avoidTolls = avoidTolls,
                avoidHighways = avoidHighways,
                avoidFerries = avoidFerries,
                departBearingDeg = departBearingDeg,
                callTimeoutMs = timeoutMs,
            )
        }
    }
}
