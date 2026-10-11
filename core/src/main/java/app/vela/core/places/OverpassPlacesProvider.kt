package app.vela.core.places

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.distanceTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class OverpassPlacesProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://overpass-api.de/api/interpreter",
) : PlacesProvider {

    override val name: String = "Overpass"

    private data class CacheKey(val category: String, val cellLat: Int, val cellLon: Int)
    private data class CacheEntry(val places: List<Place>, val timestampMs: Long)

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes cache

    override suspend fun searchNearby(
        category: String,
        near: LatLng,
        radiusMeters: Double,
        limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        val norm = normalizeCategory(category)
        val cellLat = (near.lat * 100.0).roundToInt()
        val cellLon = (near.lng * 100.0).roundToInt()
        val cacheKey = CacheKey(norm, cellLat, cellLon)

        val cached = cache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestampMs < cacheTtlMs) {
            return@withContext cached.places.take(limit)
        }

        val filter = osmFilterFor(norm) ?: return@withContext emptyList()
        val rad = radiusMeters.toInt().coerceIn(500, 25000)
        val ql = """
            [out:json][timeout:8];
            (
              node$filter(around:$rad,${near.lat},${near.lng});
              way$filter(around:$rad,${near.lat},${near.lng});
            );
            out center body $limit;
        """.trimIndent()

        try {
            val body = ql.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .header("User-Agent", "Laze/2.0 (Android; MapLibre/OSM)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }
            val jsonStr = response.body?.string() ?: return@withContext emptyList()
            val parsed = parseOverpassJson(jsonStr, near, category)
            cache[cacheKey] = CacheEntry(parsed, now)
            parsed.take(limit)
        } catch (e: IOException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun normalizeCategory(category: String): String {
        return category.trim().lowercase()
    }

    private fun osmFilterFor(category: String): String? {
        return when {
            category in listOf("مقهى", "قهوة", "كافيه", "كوفي", "cafe", "coffee") ->
                "[\"amenity\"=\"cafe\"]"
            category in listOf("مطعم", "مطاعم", "اكل", "وجبات", "restaurant", "food", "restaurants") ->
                "[\"amenity\"~\"restaurant|fast_food\"]"
            category in listOf("محطة وقود", "وقود", "بنزين", "كاز", "fuel", "gas", "gas station") ->
                "[\"amenity\"=\"fuel\"]"
            category in listOf("أسواق", "اسواق", "بقالة", "ماركت", "سوبرماركت", "supermarket", "grocery", "convenience", "groceries") ->
                "[\"shop\"~\"supermarket|convenience|grocery\"]"
            category in listOf("صيدلية", "صيدليات", "علاج", "دواء", "pharmacy") ->
                "[\"amenity\"=\"pharmacy\"]"
            category in listOf("مستشفى", "مستشفيات", "طوارئ", "مستوصف", "hospital", "clinic", "hospitals") ->
                "[\"amenity\"~\"hospital|clinic\"]"
            category in listOf("صراف", "مصرف", "بنوك", "صيرفة", "atm", "bank", "atms", "banks") ->
                "[\"amenity\"~\"atm|bank\"]"
            category in listOf("موقف سيارات", "مواقف", "كراج", "بارك", "parking", "parking lot") ->
                "[\"amenity\"=\"parking\"]"
            category in listOf("جامع", "مسجد", "حسينية", "مرقد", "mosque", "place_of_worship", "mosques") ->
                "[\"amenity\"=\"place_of_worship\"]"
            category in listOf("فندق", "فنادق", "hotel", "hotels", "motel") ->
                "[\"tourism\"~\"hotel|motel|guest_house\"]"
            category in listOf("حديقة", "منتزه", "حدائق", "park", "parks") ->
                "[\"leisure\"=\"park\"]"
            category in listOf("شحن كهربائي", "شحن", "charging", "ev", "ev station") ->
                "[\"amenity\"=\"charging_station\"]"
            else -> "[\"name\"~\"$category\",i]"
        }
    }

    private fun parseOverpassJson(jsonStr: String, near: LatLng, requestedCategory: String): List<Place> {
        val root = JSONObject(jsonStr)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val list = mutableListOf<Place>()

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            val id = "osm_" + el.optString("type", "node") + "_" + el.optLong("id")

            val name = tags.optString("name:ar")
                .ifBlank { tags.optString("name") }
                .ifBlank { tags.optString("name:en") }
                .ifBlank { requestedCategory }

            val lat = if (el.has("lat")) el.optDouble("lat") else el.optJSONObject("center")?.optDouble("lat") ?: continue
            val lon = if (el.has("lon")) el.optDouble("lon") else el.optJSONObject("center")?.optDouble("lon") ?: continue
            val loc = LatLng(lat, lon)

            val street = tags.optString("addr:street")
            val city = tags.optString("addr:city")
            val address = listOfNotNull(street.takeIf { it.isNotBlank() }, city.takeIf { it.isNotBlank() }).joinToString("، ").ifBlank { null }
            val phone = tags.optString("phone").ifBlank { tags.optString("contact:phone") }.ifBlank { null }
            val website = tags.optString("website").ifBlank { tags.optString("contact:website") }.ifBlank { null }
            val category = tags.optString("amenity").ifBlank { tags.optString("shop") }.ifBlank { tags.optString("tourism") }.ifBlank { requestedCategory }

            list.add(
                Place(
                    id = id,
                    name = name,
                    location = loc,
                    category = category,
                    address = address,
                    phone = phone,
                    website = website,
                )
            )
        }

        return list.sortedBy { it.location.distanceTo(near) }
    }
}
