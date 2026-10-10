package app.vela.core.routing

import android.util.Log
import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.CancellationException

/**
 * Composite routing provider that cascades through a prioritized chain of providers
 * (e.g. Mapbox -> Self-hosted Iraq OSRM -> Community OSRM -> On-Device).
 */
class CompositeRoutingProvider(
    val providers: List<RoutingProvider>,
) : RoutingProvider {

    override val name: String = "Composite"
    override val source: RouteSource = RouteSource.UNKNOWN

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
    ): List<Route> {
        for (provider in providers) {
            try {
                val routes = provider.route(
                    origin = origin,
                    destination = destination,
                    mode = mode,
                    waypoints = waypoints,
                    avoidTolls = avoidTolls,
                    avoidHighways = avoidHighways,
                    avoidFerries = avoidFerries,
                    departBearingDeg = departBearingDeg,
                    timeoutMs = timeoutMs,
                )
                if (routes.isNotEmpty()) {
                    runCatching {
                        Log.i("LazeRouting", "Route resolved by provider: ${provider.name} (count=${routes.size})")
                    }
                    return routes
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching {
                    Log.w("LazeRouting", "Provider ${provider.name} failed: ${t.message}")
                }
            }
        }
        return emptyList()
    }
}
