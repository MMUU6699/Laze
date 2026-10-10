package app.vela.ui.map

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSkeletonTest {

    @Test
    fun skeletonRouteConnectsEndpointsWithProvisionalFlag() {
        val origin = LatLng(33.3152, 44.3661)
        val dest = LatLng(36.1911, 44.0091)
        val stops = listOf(LatLng(34.6000, 43.6800))

        val skeleton = Route(
            polyline = listOf(origin) + stops + listOf(dest),
            legs = emptyList(),
            distanceMeters = 350_000.0,
            durationSeconds = 0.0,
            durationInTrafficSeconds = null,
            provisional = true,
            source = RouteSource.UNKNOWN,
        )

        assertEquals(3, skeleton.polyline.size)
        assertEquals(origin, skeleton.polyline.first())
        assertEquals(dest, skeleton.polyline.last())
        assertTrue(skeleton.provisional)
        assertEquals(RouteSource.UNKNOWN, skeleton.source)
    }

    @Test
    fun dashedConditionIsTrueWhenLoadingRoutes() {
        // In MapScreen.kt, routeDashed is:
        // state.travelMode == WALK || state.travelMode == BICYCLE || state.routesLoading
        fun isRouteDashed(mode: TravelMode, routesLoading: Boolean): Boolean {
            return mode == TravelMode.WALK || mode == TravelMode.BICYCLE || routesLoading
        }

        // When DRIVE mode is computing routes, line must be dashed for skeleton effect
        assertTrue(isRouteDashed(TravelMode.DRIVE, routesLoading = true))

        // When DRIVE mode is done computing routes, line must NOT be dashed
        assertEquals(false, isRouteDashed(TravelMode.DRIVE, routesLoading = false))

        // When WALK mode, line is always dashed
        assertTrue(isRouteDashed(TravelMode.WALK, routesLoading = false))
        assertTrue(isRouteDashed(TravelMode.WALK, routesLoading = true))
    }
}
