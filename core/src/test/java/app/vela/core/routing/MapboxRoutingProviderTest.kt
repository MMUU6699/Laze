package app.vela.core.routing

import app.vela.core.data.google.PolylineCodec
import app.vela.core.model.LatLng
import app.vela.core.model.ManeuverType
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapboxRoutingProviderTest {

    private val httpClient = OkHttpClient()

    @Test
    fun blankTokenReturnsEmptyWithoutNetworkCall() = runBlocking {
        val provider = MapboxRoutingProvider(httpClient, token = "")
        val result = provider.route(
            origin = LatLng(33.3152, 44.3661),
            destination = LatLng(36.1911, 44.0091),
            mode = TravelMode.DRIVE,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun prewarmDoesNotThrowWithOrWithoutToken() {
        val providerWithToken = MapboxRoutingProvider(httpClient, token = "test_token")
        providerWithToken.prewarm()

        val providerBlank = MapboxRoutingProvider(httpClient, token = "")
        providerBlank.prewarm()
    }

    @Test
    fun parseMapboxSampleResponseWithArabicGuidanceAndTrafficSpans() {
        // Encode a small test line with precision 6
        val coords = listOf(
            LatLng(33.3152, 44.3661),
            LatLng(33.3200, 44.3700),
            LatLng(33.3300, 44.3800),
        )
        val poly6 = PolylineCodec.encode(coords, 6)

        val json = """
        {
            "code": "Ok",
            "routes": [
                {
                    "geometry": "$poly6",
                    "distance": 2500.0,
                    "duration": 300.0,
                    "duration_typical": 240.0,
                    "legs": [
                        {
                            "distance": 2500.0,
                            "duration": 300.0,
                            "summary": "شارع الرشيد",
                            "annotation": {
                                "congestion": ["low", "heavy", "heavy", "moderate", "low"],
                                "distance": [500.0, 400.0, 300.0, 800.0, 500.0]
                            },
                            "steps": [
                                {
                                    "distance": 1000.0,
                                    "duration": 120.0,
                                    "name": "شارع الكرادة داخل",
                                    "maneuver": {
                                        "type": "depart",
                                        "instruction": "انطلق باتجاه الشمال على شارع الكرادة داخل",
                                        "location": [44.3661, 33.3152]
                                    }
                                },
                                {
                                    "distance": 1500.0,
                                    "duration": 180.0,
                                    "name": "شارع الرشيد",
                                    "ref": "1",
                                    "intersections": [
                                        {
                                            "lanes": [
                                                { "valid": true, "indications": ["straight"] },
                                                { "valid": true, "indications": ["right"] },
                                                { "valid": false, "indications": ["left"] }
                                            ]
                                        }
                                    ],
                                    "maneuver": {
                                        "type": "turn",
                                        "modifier": "right",
                                        "instruction": "انعطف يميناً إلى شارع الرشيد",
                                        "location": [44.3700, 33.3200]
                                    }
                                },
                                {
                                    "distance": 0.0,
                                    "duration": 0.0,
                                    "maneuver": {
                                        "type": "arrive",
                                        "instruction": "لقد وصلت إلى وجهتك",
                                        "location": [44.3800, 33.3300]
                                    }
                                }
                            ]
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val provider = MapboxRoutingProvider(httpClient, token = "test_token")
        val routes = provider.parseMapboxResponse(json)

        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(RouteSource.MAPBOX, route.source)
        assertEquals(2500.0, route.distanceMeters, 0.1)
        assertEquals(240.0, route.durationSeconds, 0.1)
        assertEquals(300.0, route.durationInTrafficSeconds ?: 0.0, 0.1)
        assertEquals(1, route.legs.size)

        // Verify Traffic Spans:
        // Segment 0: 500m low (0)
        // Segment 1+2: 400m + 300m = 700m heavy (2), starting at 500m
        // Segment 3: 800m moderate (1), starting at 1200m
        // Segment 4: 500m low (0)
        assertEquals(2, route.trafficSpans.size)
        val span1 = route.trafficSpans[0]
        assertEquals(2, span1.level)
        assertEquals(500.0, span1.startMeters, 0.1)
        assertEquals(700.0, span1.lengthMeters, 0.1)

        val span2 = route.trafficSpans[1]
        assertEquals(1, span2.level)
        assertEquals(1200.0, span2.startMeters, 0.1)
        assertEquals(800.0, span2.lengthMeters, 0.1)

        val maneuvers = route.legs.first().maneuvers
        assertEquals(3, maneuvers.size)

        // Step 1: Depart
        assertEquals(ManeuverType.DEPART, maneuvers[0].type)
        assertEquals("انطلق باتجاه الشمال على شارع الكرادة داخل", maneuvers[0].instruction)
        assertEquals(LatLng(33.3152, 44.3661), maneuvers[0].location)

        // Step 2: Turn Right with lanes
        assertEquals(ManeuverType.TURN_RIGHT, maneuvers[1].type)
        assertEquals("انعطف يميناً إلى شارع الرشيد", maneuvers[1].instruction)
        assertEquals("شارع الرشيد", maneuvers[1].road)
        assertEquals("1", maneuvers[1].ref)
        assertEquals(3, maneuvers[1].lanes.size)
        assertTrue(maneuvers[1].lanes[0].valid)
        assertTrue(maneuvers[1].lanes[1].valid)
        assertEquals(false, maneuvers[1].lanes[2].valid)

        // Step 3: Arrive
        assertEquals(ManeuverType.ARRIVE, maneuvers[2].type)
        assertEquals("لقد وصلت إلى وجهتك", maneuvers[2].instruction)
    }

    @Test
    fun mapboxManeuverTypeMappingCoversAllCases() {
        val provider = MapboxRoutingProvider(httpClient, token = "test")

        assertEquals(ManeuverType.DEPART, provider.mapManeuverType("depart", null))
        assertEquals(ManeuverType.ARRIVE, provider.mapManeuverType("arrive", null))
        assertEquals(ManeuverType.MERGE, provider.mapManeuverType("merge", null))
        assertEquals(ManeuverType.ROUNDABOUT, provider.mapManeuverType("roundabout", null))
        assertEquals(ManeuverType.EXIT_ROUNDABOUT, provider.mapManeuverType("roundabout turn", null))

        assertEquals(ManeuverType.TURN_LEFT, provider.mapManeuverType("turn", "left"))
        assertEquals(ManeuverType.TURN_RIGHT, provider.mapManeuverType("turn", "right"))
        assertEquals(ManeuverType.SLIGHT_LEFT, provider.mapManeuverType("turn", "slight left"))
        assertEquals(ManeuverType.SLIGHT_RIGHT, provider.mapManeuverType("turn", "slight right"))
        assertEquals(ManeuverType.SHARP_LEFT, provider.mapManeuverType("turn", "sharp left"))
        assertEquals(ManeuverType.SHARP_RIGHT, provider.mapManeuverType("turn", "sharp right"))
        assertEquals(ManeuverType.UTURN, provider.mapManeuverType("turn", "uturn"))

        assertEquals(ManeuverType.RAMP_LEFT, provider.mapManeuverType("on ramp", "left"))
        assertEquals(ManeuverType.RAMP_RIGHT, provider.mapManeuverType("on ramp", "right"))
        assertEquals(ManeuverType.FORK_LEFT, provider.mapManeuverType("fork", "left"))
        assertEquals(ManeuverType.FORK_RIGHT, provider.mapManeuverType("fork", "right"))

        assertEquals(ManeuverType.CONTINUE, provider.mapManeuverType("continue", "straight"))
        assertEquals(ManeuverType.KEEP_LEFT, provider.mapManeuverType("continue", "slight left"))
        assertEquals(ManeuverType.KEEP_RIGHT, provider.mapManeuverType("continue", "slight right"))
    }
}
