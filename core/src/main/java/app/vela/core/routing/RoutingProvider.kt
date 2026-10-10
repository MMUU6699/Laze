package app.vela.core.routing

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode

/**
 * Common abstraction for routing engines in Laze (Mapbox Directions API, self-hosted Iraq OSRM,
 * community OSRM, and on-device offline routing).
 */
interface RoutingProvider {
    val name: String
    val source: RouteSource

    /**
     * Compute routes between [origin] and [destination], optionally visiting [waypoints].
     *
     * @param origin Starting point.
     * @param destination Target destination.
     * @param mode Transport mode (Drive, Walk, Bicycle).
     * @param waypoints Ordered intermediate stops.
     * @param avoidTolls Whether to avoid toll roads.
     * @param avoidHighways Whether to avoid motorways/highways.
     * @param avoidFerries Whether to avoid ferries.
     * @param departBearingDeg Initial vehicle heading in degrees, if available.
     * @param timeoutMs Timeout cap for this routing attempt.
     * @return List of parsed routes, or empty list on failure / no route found.
     */
    suspend fun route(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.DRIVE,
        waypoints: List<LatLng> = emptyList(),
        avoidTolls: Boolean = false,
        avoidHighways: Boolean = false,
        avoidFerries: Boolean = false,
        departBearingDeg: Double? = null,
        timeoutMs: Long? = null,
    ): List<Route>

    /**
     * Pre-warm network and TLS connections in advance so subsequent route computations
     * do not pay connection establishment latency.
     */
    fun prewarm() {}
}
