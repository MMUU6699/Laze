package app.vela.core.places

import app.vela.core.model.LatLng
import app.vela.core.model.Place

/**
 * Common provider for discovering nearby places and categories around a location.
 */
interface PlacesProvider {
    val name: String

    /**
     * Search for places belonging to [category] near [near].
     *
     * @param category Arabic or English category or search term (e.g., "مقهى", "مطعم", "وقود").
     * @param near Center coordinate for searching.
     * @param radiusMeters Search radius in meters (default: 5000m).
     * @param limit Maximum number of places to return.
     */
    suspend fun searchNearby(
        category: String,
        near: LatLng,
        radiusMeters: Double = 5000.0,
        limit: Int = 20,
    ): List<Place>
}
