package app.vela.core.geocoding

import app.vela.core.model.LatLng
import app.vela.core.model.Place

/**
 * Common abstraction for search and forward geocoding in Laze.
 */
interface GeocodingProvider {
    val name: String

    /**
     * Search for places or addresses matching [query].
     *
     * @param query Search text (Arabic, English, or mixed).
     * @param near Approximate user location for geographic biasing.
     * @param lang Preferred response language ("ar", "en").
     * @param limit Maximum results to return.
     */
    suspend fun search(
        query: String,
        near: LatLng? = null,
        lang: String = "ar",
        limit: Int = 10,
    ): List<Place>
}
