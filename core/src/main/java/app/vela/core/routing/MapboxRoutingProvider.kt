package app.vela.core.routing

import app.vela.core.BuildConfig
import app.vela.core.data.google.PolylineCodec
import app.vela.core.model.LatLng
import app.vela.core.model.Maneuver
import app.vela.core.model.ManeuverType
import app.vela.core.model.Route
import app.vela.core.model.RouteLeg
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Routing provider backed by Mapbox Directions API v5 with native Arabic guidance.
 *
 * Requirements:
 * - Public token MUST be provided via [token] (defaults to [BuildConfig.MAPBOX_TOKEN]).
 * - When token is blank or invalid, falls back immediately without throwing uncaught exceptions.
 * - Respects distance-scaled timeout and cancellations.
 */
class MapboxRoutingProvider(
    private val httpClient: OkHttpClient,
    private val token: String = BuildConfig.MAPBOX_TOKEN,
    private val baseUrl: String = "https://api.mapbox.com/directions/v5/mapbox",
) : RoutingProvider {

    override val name: String = "Mapbox"
    override val source: RouteSource = RouteSource.MAPBOX

    private val json = Json { ignoreUnknownKeys = true }

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
    ): List<Route> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext emptyList()
        }

        val profile = when (mode) {
            TravelMode.DRIVE -> "driving"
            TravelMode.WALK -> "walking"
            TravelMode.BICYCLE -> "cycling"
            TravelMode.TRANSIT -> return@withContext emptyList()
        }

        val allPoints = buildList {
            add(origin)
            addAll(waypoints)
            add(destination)
        }

        if (allPoints.size < 2) return@withContext emptyList()

        val coordsStr = allPoints.joinToString(";") { "${it.lng},${it.lat}" }
        val alternatives = waypoints.isEmpty()

        val excludeList = buildList {
            if (avoidTolls) add("toll")
            if (avoidHighways) add("motorway")
            if (avoidFerries) add("ferry")
        }
        val excludeParam = if (excludeList.isNotEmpty() && mode == TravelMode.DRIVE) {
            "&exclude=" + excludeList.joinToString(",")
        } else ""

        val bearingParam = if (departBearingDeg != null) {
            val normalized = (((departBearingDeg % 360.0) + 360.0) % 360.0).toInt()
            "&bearings=$normalized,60" + ";".repeat(allPoints.size - 1)
        } else ""

        val url = "$baseUrl/$profile/$coordsStr" +
            "?access_token=$token" +
            "&geometries=polyline6" +
            "&overview=full" +
            "&steps=true" +
            "&language=ar" +
            (if (alternatives) "&alternatives=true" else "&alternatives=false") +
            bearingParam +
            excludeParam

        val client = if (timeoutMs != null && timeoutMs > 0) {
            httpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        } else {
            httpClient
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Laze/1.0 (Android; Arabic Navigation)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) return@withContext emptyList()

                parseMapboxResponse(bodyStr)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyList()
        }
    }

    internal fun parseMapboxResponse(jsonString: String): List<Route> {
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
            ?: return emptyList()

        val routesArray = root["routes"]?.jsonArray ?: return emptyList()

        return routesArray.mapNotNull { routeElem ->
            val routeObj = routeElem.jsonObject
            val geometryStr = routeObj["geometry"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val polyline = PolylineCodec.decode(geometryStr, 6).takeIf { it.size >= 2 }
                ?: return@mapNotNull null

            val totalDistance = routeObj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val totalDuration = routeObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            val legsArray = routeObj["legs"]?.jsonArray ?: return@mapNotNull null
            val parsedLegs = legsArray.map { legElem ->
                val legObj = legElem.jsonObject
                val legDist = legObj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val legDur = legObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val summary = legObj["summary"]?.jsonPrimitive?.content

                val stepsArray = legObj["steps"]?.jsonArray ?: emptyList()
                val maneuvers = stepsArray.mapNotNull { stepElem ->
                    parseStep(stepElem.jsonObject)
                }

                RouteLeg(
                    distanceMeters = legDist,
                    durationSeconds = legDur,
                    durationInTrafficSeconds = null,
                    maneuvers = maneuvers,
                )
            }

            val routeSummary = legsArray.firstOrNull()?.jsonObject?.get("summary")?.jsonPrimitive?.content

            Route(
                polyline = polyline,
                legs = parsedLegs,
                distanceMeters = totalDistance,
                durationSeconds = totalDuration,
                durationInTrafficSeconds = null,
                summary = routeSummary,
                source = RouteSource.MAPBOX,
            )
        }
    }

    private fun parseStep(stepObj: kotlinx.serialization.json.JsonObject): Maneuver? {
        val manObj = stepObj["maneuver"]?.jsonObject ?: return null
        val type = manObj["type"]?.jsonPrimitive?.content ?: return null
        val modifier = manObj["modifier"]?.jsonPrimitive?.content
        val instruction = manObj["instruction"]?.jsonPrimitive?.content.orEmpty()

        val locArray = manObj["location"]?.jsonArray ?: return null
        val lng = locArray.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = locArray.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null

        val distance = stepObj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val duration = stepObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val name = stepObj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val ref = stepObj["ref"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val maneuverType = mapManeuverType(type, modifier)
        val exitNumber = manObj["exit"]?.jsonPrimitive?.intOrNull

        return Maneuver(
            type = maneuverType,
            instruction = instruction,
            location = LatLng(lat, lng),
            distanceMeters = distance,
            durationSeconds = duration,
            road = name ?: ref,
            ref = ref?.substringBefore(";")?.trim(),
            roundaboutExit = exitNumber,
        )
    }

    internal fun mapManeuverType(type: String, modifier: String?): ManeuverType {
        fun byModifier(): ManeuverType = when (modifier?.lowercase()) {
            "left" -> ManeuverType.TURN_LEFT
            "right" -> ManeuverType.TURN_RIGHT
            "slight left" -> ManeuverType.SLIGHT_LEFT
            "slight right" -> ManeuverType.SLIGHT_RIGHT
            "sharp left" -> ManeuverType.SHARP_LEFT
            "sharp right" -> ManeuverType.SHARP_RIGHT
            "uturn" -> ManeuverType.UTURN
            "straight" -> ManeuverType.STRAIGHT
            else -> ManeuverType.STRAIGHT
        }

        return when (type.lowercase()) {
            "depart" -> ManeuverType.DEPART
            "arrive" -> ManeuverType.ARRIVE
            "merge" -> ManeuverType.MERGE
            "on ramp", "off ramp", "ramp" -> {
                if (modifier?.contains("left") == true) ManeuverType.RAMP_LEFT else ManeuverType.RAMP_RIGHT
            }
            "fork" -> {
                if (modifier?.contains("left") == true) ManeuverType.FORK_LEFT else ManeuverType.FORK_RIGHT
            }
            "roundabout", "rotary" -> ManeuverType.ROUNDABOUT
            "roundabout turn", "exit roundabout", "exit rotary" -> ManeuverType.EXIT_ROUNDABOUT
            "continue", "new name" -> {
                if (modifier == null || modifier == "straight") ManeuverType.CONTINUE else byModifier()
            }
            "turn", "end of road" -> byModifier()
            else -> ManeuverType.STRAIGHT
        }
    }
}
