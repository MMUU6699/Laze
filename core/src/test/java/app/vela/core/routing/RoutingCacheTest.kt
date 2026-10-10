package app.vela.core.routing

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoutingCacheTest {

    @Before
    fun setUp() {
        RoutingCache.clear()
    }

    private fun sampleRoute(): Route = Route(
        polyline = listOf(LatLng(33.3152, 44.3661), LatLng(33.3200, 44.3700)),
        legs = emptyList(),
        distanceMeters = 1000.0,
        durationSeconds = 120.0,
        durationInTrafficSeconds = 130.0,
        source = RouteSource.MAPBOX,
    )

    @Test
    fun putAndGetWithinTtlReturnsCachedRoute() {
        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)
        val routes = listOf(sampleRoute())
        val t0 = 1_000_000L

        RoutingCache.put(origin, dest, TravelMode.DRIVE, routes = routes, nowMs = t0)

        // Query 30 seconds later (within 60s TTL)
        val cached = RoutingCache.get(origin, dest, TravelMode.DRIVE, nowMs = t0 + 30_000L)
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals(routes.first().distanceMeters, cached?.first()?.distanceMeters ?: 0.0, 0.1)
    }

    @Test
    fun entryExpiresAfter60Seconds() {
        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)
        val routes = listOf(sampleRoute())
        val t0 = 1_000_000L

        RoutingCache.put(origin, dest, TravelMode.DRIVE, routes = routes, nowMs = t0)

        // Query 61 seconds later (past 60s TTL)
        val cached = RoutingCache.get(origin, dest, TravelMode.DRIVE, nowMs = t0 + 61_000L)
        assertNull(cached)
    }

    @Test
    fun nearbyCoordinatesWithin10MetersHitCache() {
        val origin = LatLng(33.315200, 44.366100)
        val dest = LatLng(33.320000, 44.370000)
        val routes = listOf(sampleRoute())
        val t0 = 1_000_000L

        RoutingCache.put(origin, dest, TravelMode.DRIVE, routes = routes, nowMs = t0)

        // Jitter by ~5 meters (approx 0.00004 degrees)
        val jitterOrigin = LatLng(33.315240, 44.366130)
        val jitterDest = LatLng(33.320020, 44.369980)

        val cached = RoutingCache.get(jitterOrigin, jitterDest, TravelMode.DRIVE, nowMs = t0 + 10_000L)
        assertNotNull(cached)
        assertEquals(1, cached?.size)
    }

    @Test
    fun differentTravelModesDoNotCollide() {
        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)
        val driveRoute = listOf(sampleRoute())
        val t0 = 1_000_000L

        RoutingCache.put(origin, dest, TravelMode.DRIVE, routes = driveRoute, nowMs = t0)

        // Query for WALK should be cache miss
        val walkCached = RoutingCache.get(origin, dest, TravelMode.WALK, nowMs = t0 + 10_000L)
        assertNull(walkCached)
    }

    @Test
    fun clearRemovesAllEntries() {
        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(33.3200, 44.3700)
        RoutingCache.put(origin, dest, TravelMode.DRIVE, routes = listOf(sampleRoute()), nowMs = 1000L)
        assertEquals(1, RoutingCache.size)

        RoutingCache.clear()
        assertEquals(0, RoutingCache.size)
    }
}
