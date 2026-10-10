package app.vela.core.routing

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import app.vela.core.model.distanceTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class CompositeRoutingProviderTest {

    @Before
    fun setUp() {
        RoutingCache.clear()
    }

    private fun dummyRoute(source: RouteSource): Route = Route(
        polyline = listOf(LatLng(33.3152, 44.3661), LatLng(33.3200, 44.3700)),
        legs = emptyList(),
        distanceMeters = 1500.0,
        durationSeconds = 180.0,
        durationInTrafficSeconds = 190.0,
        source = source,
    )

    private class MockRoutingProvider(
        override val name: String,
        override val source: RouteSource,
        val simulatedDelayMs: Long = 0,
        val failWithException: Boolean = false,
        val returnEmpty: Boolean = false,
    ) : RoutingProvider {
        var callCount = 0

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
            callCount++
            if (simulatedDelayMs > 0) delay(simulatedDelayMs)
            if (failWithException) throw IllegalStateException("Simulated network failure on $name")
            if (returnEmpty) return emptyList()
            return listOf(
                Route(
                    polyline = listOf(origin, destination),
                    legs = emptyList(),
                    distanceMeters = origin.distanceTo(destination),
                    durationSeconds = 200.0,
                    durationInTrafficSeconds = 210.0,
                    source = source,
                )
            )
        }
    }

    @Test
    fun primaryProviderSuccessReturnsImmediatelyAndCaches() = runBlocking {
        val mapboxProvider = MockRoutingProvider("Mapbox", RouteSource.MAPBOX)
        val osrmProvider = MockRoutingProvider("OSRM", RouteSource.OSRM)
        val composite = CompositeRoutingProvider(listOf(mapboxProvider, osrmProvider))

        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)

        val routes1 = composite.route(origin, dest, TravelMode.DRIVE)
        assertEquals(1, routes1.size)
        assertEquals(RouteSource.MAPBOX, routes1.first().source)
        assertEquals(1, mapboxProvider.callCount)
        assertEquals(0, osrmProvider.callCount)

        // Second call should hit RoutingCache
        val routes2 = composite.route(origin, dest, TravelMode.DRIVE)
        assertEquals(1, routes2.size)
        assertEquals(1, mapboxProvider.callCount) // no additional provider calls
    }

    @Test
    fun fallbackCascadesToBackupWhenPrimaryFails() = runBlocking {
        // Mapbox fails with 429 / server error
        val mapboxProvider = MockRoutingProvider("Mapbox", RouteSource.MAPBOX, failWithException = true)
        val osrmProvider = MockRoutingProvider("OSRM-Iraq", RouteSource.OSRM)
        val composite = CompositeRoutingProvider(listOf(mapboxProvider, osrmProvider))

        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)

        val routes = composite.route(origin, dest, TravelMode.DRIVE)
        assertEquals(1, routes.size)
        assertEquals(RouteSource.OSRM, routes.first().source)
        assertEquals(1, mapboxProvider.callCount)
        assertEquals(1, osrmProvider.callCount)
    }

    @Test
    fun simulated4gLatencySatisfiesTimingTarget() = runBlocking {
        // Simulated 4G round-trip latency of 100ms
        val mapboxProvider = MockRoutingProvider("Mapbox", RouteSource.MAPBOX, simulatedDelayMs = 100)
        val composite = CompositeRoutingProvider(listOf(mapboxProvider))

        val origin = LatLng(33.3152, 44.3661) // Baghdad
        val dest = LatLng(36.1911, 44.0091)   // Erbil

        val elapsedMs = measureTimeMillis {
            val routes = composite.route(origin, dest, TravelMode.DRIVE)
            assertEquals(1, routes.size)
        }

        // Must appear in less than 1500 ms (1.5 seconds)
        assertTrue("Expected route time < 1500ms, actual: ${elapsedMs}ms", elapsedMs < 1500)
    }

    @Test
    fun noProviderAvailableReturnsEmptyWithoutCrash() = runBlocking {
        val mapboxProvider = MockRoutingProvider("Mapbox", RouteSource.MAPBOX, failWithException = true)
        val osrmProvider = MockRoutingProvider("OSRM", RouteSource.OSRM, failWithException = true)
        val composite = CompositeRoutingProvider(listOf(mapboxProvider, osrmProvider))

        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)

        val routes = composite.route(origin, dest, TravelMode.DRIVE)
        assertTrue(routes.isEmpty())
    }
}
