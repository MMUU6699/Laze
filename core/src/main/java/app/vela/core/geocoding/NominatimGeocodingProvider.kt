package app.vela.core.geocoding

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.distanceTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Geocoding provider backed by OpenStreetMap Nominatim with Iraq prioritization and Arabic support.
 */
class NominatimGeocodingProvider(
    private val httpClient: OkHttpClient,
    val baseUrl: String = "https://nominatim.openstreetmap.org/search",
    override val name: String = "Nominatim",
) : GeocodingProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(
        query: String,
        near: LatLng?,
        lang: String,
        limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val viewboxParam = if (near != null) {
            val dLat = 0.5
            val dLng = 0.5
            "&viewbox=${near.lng - dLng},${near.lat + dLat},${near.lng + dLng},${near.lat - dLat}"
        } else ""

        val url = "$baseUrl?q=$encodedQuery" +
            "&format=jsonv2" +
            "&accept-language=$lang,en" +
            "&countrycodes=iq" +
            "&limit=$limit" +
            viewboxParam

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Laze/1.0 (Android; Iraq Navigation)")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()

                val rootArray = json.parseToJsonElement(body).jsonArray
                rootArray.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val loc = LatLng(lat, lon)
                    val displayName = obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBefore(",")
                    val type = obj["type"]?.jsonPrimitive?.content?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                        ?: "Location"
                    val placeId = obj["place_id"]?.jsonPrimitive?.content ?: "${lat},${lon}"

                    Place(
                        id = "nominatim:$placeId",
                        name = name.trim(),
                        location = loc,
                        category = type,
                        address = displayName,
                        distanceMeters = near?.distanceTo(loc),
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
