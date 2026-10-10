package app.vela.core.geocoding

import app.vela.core.BuildConfig
import app.vela.core.data.PhotonGeocoder
import app.vela.core.model.LatLng
import app.vela.core.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Geocoding provider backed by Photon (either self-hosted Iraq instance or komoot community).
 */
class PhotonGeocodingProvider(
    private val httpClient: OkHttpClient,
    val baseUrl: String = BuildConfig.IRAQ_PHOTON_URL.ifBlank { "https://photon.komoot.io/api/" },
    override val name: String = if (BuildConfig.IRAQ_PHOTON_URL.isNotBlank()) "Photon-Iraq" else "Photon-Community",
) : GeocodingProvider {

    override suspend fun search(
        query: String,
        near: LatLng?,
        lang: String,
        limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        PhotonGeocoder.suggest(
            http = httpClient,
            q = query,
            near = near,
            lang = lang,
            limit = limit,
            hardBox = false,
        )
    }
}
