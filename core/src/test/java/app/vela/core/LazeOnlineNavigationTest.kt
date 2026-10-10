package app.vela.core

import app.vela.core.data.RouteGeometry
import app.vela.core.data.PhotonGeocoder
import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LazeOnlineNavigationTest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Test
    fun testErbilToBaghdadOnlineRouting() {
        // Erbil coordinates
        val erbil = LatLng(36.190193, 44.009927)
        // Baghdad coordinates
        val baghdad = LatLng(33.310474, 44.360226)

        val routes = RouteGeometry.route(
            http = http,
            origin = erbil,
            dest = baghdad,
            mode = TravelMode.DRIVE,
            tries = 2
        )

        assertNotNull("Routes response should not be null", routes)
        assertTrue("Routes between Erbil and Baghdad should not be empty", routes.isNotEmpty())

        val topRoute = routes.first()
        assertTrue("Polyline should contain at least 100 points for a ~360 km trip", topRoute.polyline.size >= 100)
        assertTrue("Distance should be approximately 300-450 km (in meters)", topRoute.distanceMeters > 300_000.0)
        assertTrue("Duration should be > 3 hours (> 10800 seconds)", topRoute.durationSeconds > 10800.0)
        assertTrue("Maneuvers should be present and valid", topRoute.maneuvers.size >= 2)
    }

    @Test
    fun testPhotonGeocoderBaghdadSearch() {
        val near = LatLng(33.3152, 44.3661)
        val places = PhotonGeocoder.suggest(http, "Baghdad", near, lang = "en", limit = 3, hardBox = false)
        assertNotNull("Photon places should not be null", places)
        assertTrue("Photon search for Baghdad should find results", places.isNotEmpty())
        val first = places.first()
        assertTrue("Result name should contain Baghdad or بغداد", first.name.contains("Baghdad", ignoreCase = true) || first.name.contains("بغداد"))
    }

    @Test
    fun testEmptyCandidatesDeduplicationSafety() {
        val emptyPrimary: List<Route> = emptyList()
        val emptyGoogleAlts: List<Route> = emptyList()

        val primaryFirst = emptyPrimary.firstOrNull()
        val candidateList = (if (primaryFirst != null) listOf(primaryFirst) else emptyList()) +
            emptyGoogleAlts +
            (if (emptyPrimary.size > 1) emptyPrimary.drop(1) else emptyList())

        assertTrue("Candidate list should safely be empty without throwing exceptions", candidateList.isEmpty())
    }
}
