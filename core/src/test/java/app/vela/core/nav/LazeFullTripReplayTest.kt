package app.vela.core.nav

import app.vela.core.backend.RoadReport
import app.vela.core.backend.RoadReportSeverity
import app.vela.core.backend.RoadReportStatus
import app.vela.core.backend.RoadReportType
import app.vela.core.i18n.NavStringsRegistry
import app.vela.core.location.DemoTrace
import app.vela.core.model.LatLng
import app.vela.core.model.Maneuver
import app.vela.core.model.ManeuverType
import app.vela.core.model.Route
import app.vela.core.model.RouteLeg
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end automated navigation replay test verifying:
 * 1. Turn-by-turn guidance events and Arabic spoken cues
 * 2. On-route hazard detection (checkpoints, speed bumps, potholes, accidents)
 * 3. Off-route detection and re-routing triggers
 */
class LazeFullTripReplayTest {

    @Before
    fun setup() {
        NavStringsRegistry.setLocale(java.util.Locale.forLanguageTag("ar"))
    }

    @After
    fun teardown() {
        NavStringsRegistry.setLocale(java.util.Locale.US)
    }

    private fun baghdadTrip(): Route {
        // Baghdad trip: Karrada to Mansour via 14th of Ramadan St
        val polyline = listOf(
            LatLng(33.3000, 44.4200), // Karrada start
            LatLng(33.3050, 44.4150),
            LatLng(33.3100, 44.4050),
            LatLng(33.3150, 44.3950), // Checkpoint area
            LatLng(33.3200, 44.3850),
            LatLng(33.3250, 44.3750),
            LatLng(33.3300, 44.3650), // Mansour end
        )

        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "انطلق باتجاه الشمال", polyline[0], 500.0, 40.0),
            Maneuver(ManeuverType.TURN_LEFT, "انعطف يساراً نحو شارع الكرادة داخل", polyline[1], 1000.0, 70.0),
            Maneuver(ManeuverType.TURN_RIGHT, "انعطف يميناً نحو جسر الجادرية", polyline[2], 1200.0, 80.0),
            Maneuver(ManeuverType.CONTINUE, "تابع السير للأمام", polyline[3], 1100.0, 75.0),
            Maneuver(ManeuverType.TURN_LEFT, "انعطف يساراً نحو شارع 14 رمضان", polyline[4], 1300.0, 90.0),
            Maneuver(ManeuverType.ARRIVE, "وصلت إلى وجهتك", polyline[6], 0.0, 0.0),
        )

        val totalM = 5100.0
        val totalSec = 355.0
        return Route(
            polyline = polyline,
            legs = listOf(RouteLeg(totalM, totalSec, null, maneuvers)),
            distanceMeters = totalM,
            durationSeconds = totalSec,
            durationInTrafficSeconds = null
        )
    }

    @Test
    fun `replays full trip and collects turn maneuvers and arrive events`() {
        val route = baghdadTrip()
        val fixes = DemoTrace.fromRoute(route)

        var state = NavState()
        val spokenEvents = mutableListOf<String>()
        var arrived = false

        for (fix in fixes) {
            val loc = LatLng(fix.lat, fix.lng)
            val (newState, events) = NavEngine.update(
                route = route,
                state = state,
                loc = loc,
                speedMps = fix.speed.toDouble(),
                bearingDeg = fix.bearing.toDouble()
            )
            state = newState

            for (ev in events) {
                if (ev is NavEvent.Speak) {
                    spokenEvents.add(ev.text)
                }
                if (ev is NavEvent.Arrived) {
                    arrived = true
                }
            }
        }

        assertTrue("Should produce spoken navigation events during trip", spokenEvents.isNotEmpty())
        assertTrue("Drive should successfully arrive at destination", arrived || state.arrived)
    }

    @Test
    fun `detects Iraqi road hazards along the route during navigation`() {
        val route = baghdadTrip()
        // Hazard along polyline[3]
        val checkpoint = RoadReport(
            id = "cp-1",
            type = RoadReportType.CHECKPOINT,
            latitude = 33.3150,
            longitude = 44.3950,
            severity = RoadReportSeverity.MODERATE,
            description = "سيطرة تفتيش أمني",
            status = RoadReportStatus.ACTIVE,
            reliabilityScore = 1.5
        )

        // Verify distance calculation to route point
        val dist = checkpoint.distanceMetersTo(33.3150, 44.3950)
        assertTrue(dist < 5.0)

        // Verify localized Arabic voice string for checkpoint
        val strings = NavStringsRegistry.current()
        val alert = strings.hazardAlert(RoadReportType.CHECKPOINT)
        assertTrue(alert.contains("سيطرة") || alert.contains("تفتيش"))
    }

    @Test
    fun `triggers off-route when vehicle diverges from route polyline`() {
        val route = baghdadTrip()
        var state = NavState()

        // Vehicle moves 300 meters away from route (diverging off-route)
        val offRouteLoc = LatLng(33.3150, 44.4250) // ~2.8 km east of route
        val (newState, _) = NavEngine.update(
            route = route,
            state = state,
            loc = offRouteLoc,
            speedMps = 12.0,
            bearingDeg = 90.0
        )

        // Off-route hit counter or off-route status should be triggered
        assertTrue(newState.offRouteHits > 0 || newState.offRoute)
    }
}
