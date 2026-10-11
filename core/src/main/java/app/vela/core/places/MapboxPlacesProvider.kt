package app.vela.core.places

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.distanceTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MapboxPlacesProvider(
    private val accessTokenProvider: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.mapbox.com/geocoding/v5/mapbox.places",
) : PlacesProvider {

    override val name: String = "Mapbox"

    override suspend fun searchNearby(
        category: String,
        near: LatLng,
        radiusMeters: Double,
        limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        val token = accessTokenProvider().trim()
        if (token.isBlank() || token.startsWith("YOUR_")) return@withContext emptyList()

        val queryTerm = mapboxSearchTerm(category)
        val encodedQuery = URLEncoder.encode(queryTerm, "UTF-8")

        val url = "$baseUrl/$encodedQuery.json".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("proximity", "${near.lng},${near.lat}")
            ?.addQueryParameter("types", "poi")
            ?.addQueryParameter("language", "ar")
            ?.addQueryParameter("limit", limit.coerceIn(1, 20).toString())
            ?.addQueryParameter("access_token", token)
            ?.build() ?: return@withContext emptyList()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val json = response.body?.string() ?: return@withContext emptyList()
            parseMapboxFeatures(json, near, category)
        } catch (e: IOException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mapboxSearchTerm(category: String): String {
        return when (category.trim().lowercase()) {
            "مقهى", "قهوة", "كافيه", "كوفي", "cafe", "coffee" -> "cafe"
            "مطعم", "مطاعم", "اكل", "restaurant", "food", "restaurants" -> "restaurant"
            "محطة وقود", "وقود", "بنزين", "كاز", "fuel", "gas", "gas station" -> "gas station"
            "أسواق", "اسواق", "بقالة", "ماركت", "سوبرماركت", "supermarket", "grocery" -> "supermarket"
            "صيدلية", "صيدليات", "pharmacy" -> "pharmacy"
            "مستشفى", "مستشفيات", "طوارئ", "hospital", "clinic" -> "hospital"
            "صراف", "مصرف", "بنوك", "atm", "bank" -> "atm"
            "موقف سيارات", "مواقف", "كراج", "بارك", "parking" -> "parking"
            "جامع", "مسجد", "حسينية", "مرقد", "mosque", "place_of_worship" -> "mosque"
            "فندق", "فنادق", "hotel", "hotels" -> "hotel"
            "حديقة", "منتزه", "حدائق", "park" -> "park"
            "شحن كهربائي", "charging", "ev" -> "ev charging"
            else -> category
        }
    }

    private fun parseMapboxFeatures(json: String, near: LatLng, category: String): List<Place> {
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return emptyList()
        val results = mutableListOf<Place>()

        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val id = f.optString("id", "mapbox_$i")
            val name = f.optString("text").ifBlank { f.optString("place_name") }
            val center = f.optJSONArray("center") ?: continue
            if (center.length() < 2) continue
            val lng = center.optDouble(0)
            val lat = center.optDouble(1)
            val loc = LatLng(lat, lng)

            val placeName = f.optString("place_name")
            val address = placeName.takeIf { it.isNotBlank() && it != name }

            results.add(
                Place(
                    id = id,
                    name = name,
                    location = loc,
                    category = category,
                    address = address,
                )
            )
        }

        return results.sortedBy { it.location.distanceTo(near) }
    }
}
