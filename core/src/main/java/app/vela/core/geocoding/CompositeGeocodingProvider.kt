package app.vela.core.geocoding

import android.util.Log
import app.vela.core.model.LatLng
import app.vela.core.model.Place
import kotlinx.coroutines.CancellationException

/**
 * Composite geocoding provider cascading through multiple backends (e.g. Photon -> Nominatim).
 */
class CompositeGeocodingProvider(
    val providers: List<GeocodingProvider>,
) : GeocodingProvider {

    override val name: String = "CompositeGeocoding"

    override suspend fun search(
        query: String,
        near: LatLng?,
        lang: String,
        limit: Int,
    ): List<Place> {
        for (provider in providers) {
            try {
                val results = provider.search(query, near, lang, limit)
                if (results.isNotEmpty()) {
                    runCatching {
                        Log.i("LazeGeocoding", "Query '$query' answered by ${provider.name} (count=${results.size})")
                    }
                    return results
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching {
                    Log.w("LazeGeocoding", "Provider ${provider.name} search failed: ${t.message}")
                }
            }
        }
        return emptyList()
    }
}
