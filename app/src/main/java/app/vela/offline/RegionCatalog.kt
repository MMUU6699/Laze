package app.vela.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** One downloadable region asset, from a manifest. [s]/[w]/[n]/[e] = covered bbox. Shared by the obf,
 *  place-pack, overlay and basemap catalogs (same row shape); the trailing fields are pack-only
 *  (update revision + row counts + optional row-level delta) and stay at their defaults elsewhere. */
data class RoutingRegion(
    val id: String,
    val name: String,
    val url: String,
    val sizeMb: Int,
    val s: Double,
    val w: Double,
    val n: Double,
    val e: Double,
    val installedMb: Int = 0,               // unpacked on-disk size; 0 in old manifests (estimate from the zip)
    val rev: Int = 0,                       // pack revision (bumped every rebuild)
    val counts: Map<String, Long> = emptyMap(), // expected per-table row counts, verifies a delta apply
    val deltaUrl: String? = null,           // row-level delta from [deltaFromRev] to [rev], if published
    val deltaFromRev: Int = 0,
    val deltaSizeMb: Int = 0,
) {
    /** Whether this region holds the point: its real boundary where [RegionPolys] has one, else
     *  the box. Every "which region is this point in" decision goes through here (issue #599). */
    fun covers(lat: Double, lng: Double): Boolean =
        RegionPolys.covers(id, lat, lng) ?: RegionPolys.boxCovers(s, w, n, e, lat, lng)

    /** The box's area in square degrees, the tie-break among covering regions: the smallest one is
     *  the specific region for a point where boxes overlap at a border. */
    fun boxArea(): Double = (n - s) * (e - w)
}

/**
 * The region catalog fetch: reads a `{regions:[...]}` manifest into [RoutingRegion] rows. The obf
 * routing catalog is the one caller today; the store that used to live here (the GraphHopper
 * graph download, its index and the road-name sidecar) was retired on 2026-09-15 once the obf
 * carried the speed limit and the romanized names itself.
 */
@Singleton
class RegionCatalog @Inject constructor(
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    /** The catalog as last fetched, kept on disk (2026-09-23): with no signal the fetch failed and
     *  the Offline maps page listed nothing, installed regions included, so a user who had just
     *  downloaded a state read it as "nothing is downloaded". */
    private fun cacheFile(url: String) = java.io.File(context.filesDir, "catalog-${url.hashCode().toUInt()}.json")

    suspend fun manifest(manifestUrl: String): List<RoutingRegion> = withContext(Dispatchers.IO) {
        val fetched = runCatching {
            val json = runCatching {
                http.newCall(Request.Builder().url(manifestUrl).build()).execute()
                    .use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.string() }
                    .also { fresh -> runCatching { app.vela.core.util.AtomicFiles.writeText(cacheFile(manifestUrl), fresh) } }
            }.getOrElse { cacheFile(manifestUrl).takeIf { it.exists() }?.readText() ?: throw it }
            val arr = JSONObject(json).getJSONArray("regions")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val b = o.getJSONArray("bbox") // [S, W, N, E]
                RoutingRegion(
                    o.getString("id"), o.getString("name"), o.getString("url"), o.optInt("sizeMb"),
                    b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3),
                    installedMb = o.optInt("installedMb"),
                    rev = o.optInt("rev"),
                )
            }
        }.getOrDefault(emptyList())

        if (fetched.isNotEmpty()) fetched else IRAQ_DEFAULT_REGIONS
    }

    companion object {
        val IRAQ_DEFAULT_REGIONS = listOf(
            RoutingRegion(
                id = "iq-all",
                name = "العراق (كامل المحافظات) / Iraq Complete",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/iraq.obf.zip",
                sizeMb = 85,
                s = 29.06, w = 38.79, n = 37.38, e = 48.63,
                installedMb = 120,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-baghdad",
                name = "بغداد / Baghdad",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/baghdad.obf.zip",
                sizeMb = 24,
                s = 32.88, w = 44.02, n = 33.72, e = 44.75,
                installedMb = 35,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-erbil",
                name = "أربيل (هەولێر) / Erbil",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/erbil.obf.zip",
                sizeMb = 18,
                s = 35.45, w = 43.25, n = 37.15, e = 45.15,
                installedMb = 28,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-basra",
                name = "البصرة / Basra",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/basra.obf.zip",
                sizeMb = 16,
                s = 29.75, w = 46.50, n = 31.40, e = 48.60,
                installedMb = 24,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-sulaymaniyah",
                name = "السليمانية (سلێمانی) / Sulaymaniyah",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/sulaymaniyah.obf.zip",
                sizeMb = 15,
                s = 34.90, w = 44.80, n = 36.40, e = 46.40,
                installedMb = 22,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-najaf",
                name = "النجف الأشرف / Najaf",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/najaf.obf.zip",
                sizeMb = 12,
                s = 30.40, w = 43.10, n = 32.35, e = 44.60,
                installedMb = 18,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-karbala",
                name = "كربلاء المقدسة / Karbala",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/karbala.obf.zip",
                sizeMb = 10,
                s = 32.10, w = 43.30, n = 32.85, e = 44.30,
                installedMb = 15,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-nineveh",
                name = "نينوى (الموصل) / Nineveh",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/nineveh.obf.zip",
                sizeMb = 19,
                s = 35.10, w = 41.20, n = 37.05, e = 43.60,
                installedMb = 29,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-kirkuk",
                name = "كركوك / Kirkuk",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/kirkuk.obf.zip",
                sizeMb = 11,
                s = 34.85, w = 43.40, n = 35.85, e = 44.85,
                installedMb = 16,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-anbar",
                name = "الأنبار / Anbar",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/anbar.obf.zip",
                sizeMb = 17,
                s = 31.10, w = 38.79, n = 35.15, e = 44.20,
                installedMb = 25,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-dhi-qar",
                name = "ذي قار (الناصرية) / Dhi Qar",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/dhi_qar.obf.zip",
                sizeMb = 11,
                s = 30.55, w = 45.60, n = 32.00, e = 47.25,
                installedMb = 17,
                rev = 1,
            ),
            RoutingRegion(
                id = "iq-babil",
                name = "بابل (الحلة) / Babylon",
                url = "https://github.com/MMUU6699/Laze/releases/download/obf-regions/babil.obf.zip",
                sizeMb = 12,
                s = 32.15, w = 44.00, n = 33.05, e = 45.10,
                installedMb = 18,
                rev = 1,
            ),
        )
    }
}
