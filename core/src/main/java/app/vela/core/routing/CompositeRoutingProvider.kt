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
 *
 * Integrates in-memory LRU routing cache (60s TTL) to prevent redundant queries
 * and supports connection pre-warming across providers.
 */
class CompositeRoutingProvider(
    val providers: List<RoutingProvider>,
) : RoutingProvider {

    override val name: String = "Composite"
    override val source: RouteSource = RouteSource.UNKNOWN

    override fun prewarm() {
        for (provider in providers) {
            runCatching { provider.prewarm() }
        }
    }

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
        // Check in-memory LRU cache first (60s TTL)
        val cached = RoutingCache.get(
            origin = origin,
            destination = destination,
            mode = mode,
            waypoints = waypoints,
            avoidTolls = avoidTolls,
            avoidHighways = avoidHighways,
            avoidFerries = avoidFerries,
        )
        if (!cached.isNullOrEmpty()) {
            runCatching {
                Log.d("LazeRouting", "Cache HIT for route query ($mode, ${cached.size} route(s))")
            }
            return cached
        }

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
                    RoutingCache.put(
                        origin = origin,
                        destination = destination,
                        mode = mode,
                        waypoints = waypoints,
                        avoidTolls = avoidTolls,
                        avoidHighways = avoidHighways,
                        avoidFerries = avoidFerries,
                        routes = routes,
                    )
                    return routes
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching {
                    Log.w("LazeRouting", "Provider ${provider.name} failed: ${t.message}, falling back to next provider")
                }
            }
        }
        return emptyList()
    }
}
