package app.vela.core.routing

import app.vela.core.data.google.PolylineCodec
import app.vela.core.model.LatLng
import app.vela.core.model.ManeuverType
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
    fun parseMapboxSampleResponseWithArabicGuidance() {
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
                    "legs": [
                        {
                            "distance": 2500.0,
                            "duration": 300.0,
                            "summary": "شارع الرشيد",
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
        assertEquals(300.0, route.durationSeconds, 0.1)
        assertEquals(1, route.legs.size)

        val maneuvers = route.legs.first().maneuvers
        assertEquals(3, maneuvers.size)

        // Step 1: Depart
        assertEquals(ManeuverType.DEPART, maneuvers[0].type)
        assertEquals("انطلق باتجاه الشمال على شارع الكرادة داخل", maneuvers[0].instruction)
        assertEquals(LatLng(33.3152, 44.3661), maneuvers[0].location)

        // Step 2: Turn Right
        assertEquals(ManeuverType.TURN_RIGHT, maneuvers[1].type)
        assertEquals("انعطف يميناً إلى شارع الرشيد", maneuvers[1].instruction)
        assertEquals("شارع الرشيد", maneuvers[1].road)
        assertEquals("1", maneuvers[1].ref)

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
    }
}
