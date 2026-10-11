package app.vela.core.places

import app.vela.core.model.LatLng
import app.vela.core.model.Place

class CompositePlacesProvider(
    private val providers: List<PlacesProvider>
) : PlacesProvider {

    override val name: String = "Composite(${providers.joinToString { it.name }})"

    override suspend fun searchNearby(
        category: String,
        near: LatLng,
        radiusMeters: Double,
        limit: Int,
    ): List<Place> {
        for (provider in providers) {
            val results = runCatching {
                provider.searchNearby(category, near, radiusMeters, limit)
            }.getOrDefault(emptyList())

            if (results.isNotEmpty()) {
                return results
            }
        }
        return emptyList()
    }
}
