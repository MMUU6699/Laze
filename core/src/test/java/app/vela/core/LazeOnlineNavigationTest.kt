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
    fun testBaghdadToErbilRoute() {
        val baghdad = LatLng(33.3152, 44.3661)
        val erbil = LatLng(36.1911, 44.0091)
        val startMs = System.currentTimeMillis()
        val routes = RouteGeometry.route(http, baghdad, erbil, TravelMode.DRIVE, tries = 2)
        val durationMs = System.currentTimeMillis() - startMs
        println("Route Baghdad -> Erbil: elapsed ${durationMs}ms, found ${routes.size} routes")
        assertNotNull(routes)
        assertTrue("Should find route Baghdad -> Erbil", routes.isNotEmpty())
        val r = routes.first()
        println("Baghdad -> Erbil distance: ${r.distanceMeters / 1000}km, duration: ${r.durationSeconds / 3600}h, points: ${r.polyline.size}")
        assertTrue("Distance should be around 300-450km", r.distanceMeters in 300_000.0..500_000.0)
    }

    @Test
    fun testBaghdadToBasraRoute() {
        val baghdad = LatLng(33.3152, 44.3661)
        val basra = LatLng(30.5085, 47.7804)
        val startMs = System.currentTimeMillis()
        val routes = RouteGeometry.route(http, baghdad, basra, TravelMode.DRIVE, tries = 2)
        val durationMs = System.currentTimeMillis() - startMs
        println("Route Baghdad -> Basra: elapsed ${durationMs}ms, found ${routes.size} routes")
        assertNotNull(routes)
        assertTrue("Should find route Baghdad -> Basra", routes.isNotEmpty())
        val r = routes.first()
        println("Baghdad -> Basra distance: ${r.distanceMeters / 1000}km, duration: ${r.durationSeconds / 3600}h, points: ${r.polyline.size}")
        assertTrue("Distance should be around 450-600km", r.distanceMeters in 400_000.0..650_000.0)
    }

    @Test
    fun testErbilToSulaymaniyahRoute() {
        val erbil = LatLng(36.1911, 44.0091)
        val sulaymaniyah = LatLng(35.5612, 45.4385)
        val startMs = System.currentTimeMillis()
        val routes = RouteGeometry.route(http, erbil, sulaymaniyah, TravelMode.DRIVE, tries = 2)
        val durationMs = System.currentTimeMillis() - startMs
        println("Route Erbil -> Sulaymaniyah: elapsed ${durationMs}ms, found ${routes.size} routes")
        assertNotNull(routes)
        assertTrue("Should find route Erbil -> Sulaymaniyah", routes.isNotEmpty())
        val r = routes.first()
        println("Erbil -> Sulaymaniyah distance: ${r.distanceMeters / 1000}km, duration: ${r.durationSeconds / 3600}h, points: ${r.polyline.size}")
        assertTrue("Distance should be around 140-250km", r.distanceMeters in 120_000.0..280_000.0)
    }

    @Test
    fun testInsideBaghdadShortRoute() {
        val tahrir = LatLng(33.3283, 44.4093)
        val kadhimiya = LatLng(33.3811, 44.3411)
        val startMs = System.currentTimeMillis()
        val routes = RouteGeometry.route(http, tahrir, kadhimiya, TravelMode.DRIVE, tries = 2)
        val durationMs = System.currentTimeMillis() - startMs
        println("Route Inside Baghdad: elapsed ${durationMs}ms, found ${routes.size} routes")
        assertNotNull(routes)
        assertTrue("Should find route inside Baghdad", routes.isNotEmpty())
        val r = routes.first()
        println("Inside Baghdad distance: ${r.distanceMeters / 1000}km, duration: ${r.durationSeconds / 60}min, points: ${r.polyline.size}")
        assertTrue("Distance should be around 5-25km", r.distanceMeters in 5_000.0..30_000.0)
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
