package app.vela.core.routing

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeRoutingProviderTest {

    private class MockProvider(
        override val name: String,
        override val source: RouteSource,
        private val shouldFail: Boolean = false,
        private val returnRoutes: List<Route> = emptyList(),
    ) : RoutingProvider {
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
            if (shouldFail) throw RuntimeException("Network down")
            return returnRoutes
        }
    }

    private fun sampleRoute(src: RouteSource): Route = Route(
        polyline = listOf(LatLng(33.0, 44.0), LatLng(33.1, 44.1)),
        legs = emptyList(),
        distanceMeters = 1000.0,
        durationSeconds = 60.0,
        durationInTrafficSeconds = null,
        source = src,
    )

    @Test
    fun returnsFirstSuccessfulProviderRoute() = runBlocking {
        val p1 = MockProvider("Mapbox", RouteSource.MAPBOX, returnRoutes = listOf(sampleRoute(RouteSource.MAPBOX)))
        val p2 = MockProvider("OSRM", RouteSource.OSRM, returnRoutes = listOf(sampleRoute(RouteSource.OSRM)))

        val composite = CompositeRoutingProvider(listOf(p1, p2))
        val result = composite.route(LatLng(33.0, 44.0), LatLng(33.1, 44.1))

        assertEquals(1, result.size)
        assertEquals(RouteSource.MAPBOX, result.first().source)
    }

    @Test
    fun fallsBackToSecondProviderWhenFirstFails() = runBlocking {
        val p1 = MockProvider("Mapbox", RouteSource.MAPBOX, shouldFail = true)
        val p2 = MockProvider("OSRM-Iraq", RouteSource.OSRM, returnRoutes = listOf(sampleRoute(RouteSource.OSRM)))

        val composite = CompositeRoutingProvider(listOf(p1, p2))
        val result = composite.route(LatLng(33.0, 44.0), LatLng(33.1, 44.1))

        assertEquals(1, result.size)
        assertEquals(RouteSource.OSRM, result.first().source)
    }

    @Test
    fun fallsBackToSecondProviderWhenFirstReturnsEmpty() = runBlocking {
        val p1 = MockProvider("Mapbox", RouteSource.MAPBOX, returnRoutes = emptyList())
        val p2 = MockProvider("OSRM-Community", RouteSource.OSRM, returnRoutes = listOf(sampleRoute(RouteSource.OSRM)))

        val composite = CompositeRoutingProvider(listOf(p1, p2))
        val result = composite.route(LatLng(33.0, 44.0), LatLng(33.1, 44.1))

        assertEquals(1, result.size)
        assertEquals(RouteSource.OSRM, result.first().source)
    }

    @Test
    fun returnsEmptyWhenAllProvidersFail() = runBlocking {
        val p1 = MockProvider("Mapbox", RouteSource.MAPBOX, shouldFail = true)
        val p2 = MockProvider("OSRM", RouteSource.OSRM, returnRoutes = emptyList())

        val composite = CompositeRoutingProvider(listOf(p1, p2))
        val result = composite.route(LatLng(33.0, 44.0), LatLng(33.1, 44.1))

        assertTrue(result.isEmpty())
    }
}
