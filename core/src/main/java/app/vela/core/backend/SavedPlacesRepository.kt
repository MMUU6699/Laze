package app.vela.core.backend

import app.vela.core.model.SavedPlace
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteSavedPlace(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: String = "favorite", // 'home', 'work', 'favorite'
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("address")
    val address: String? = null,
)

/**
 * Manages cloud synchronization of user's saved places (Home, Work, Favorites)
 * with the Supabase backend.
 */
class SavedPlacesRepository(
    private val authRepository: AuthRepository? = null,
) {
    suspend fun getSavedPlaces(): List<RemoteSavedPlace> = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
        val user = authRepository?.getCurrentProfile()
        if (client == null || user == null || !LazeBackendConfig.isConfigured) {
            return@withContext emptyList()
        }

        try {
            client.postgrest.from("saved_places")
                .select {
                    filter {
                        eq("user_id", user.id)
                    }
                }
                .decodeList<RemoteSavedPlace>()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun savePlace(
        name: String,
        type: String,
        latitude: Double,
        longitude: Double,
        address: String? = null,
    ): RemoteSavedPlace? = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
        val user = authRepository?.getCurrentProfile()
        if (client == null || user == null || !LazeBackendConfig.isConfigured) {
            return@withContext null
        }

        try {
            val place = RemoteSavedPlace(
                userId = user.id,
                name = name,
                type = type,
                latitude = latitude,
                longitude = longitude,
                address = address,
            )
            client.postgrest.from("saved_places")
                .insert(place) {
                    select()
                }
                .decodeSingle<RemoteSavedPlace>()
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun deletePlace(id: String): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
        val user = authRepository?.getCurrentProfile()
        if (client == null || user == null || !LazeBackendConfig.isConfigured) {
            return@withContext false
        }

        try {
            client.postgrest.from("saved_places")
                .delete {
                    filter {
                        eq("id", id)
                        eq("user_id", user.id)
                    }
                }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Uploads local places (favorites, home, work) to Supabase cloud if they don't already exist.
     */
    suspend fun syncLocalPlaces(
        favorites: List<SavedPlace>,
        home: SavedPlace?,
        work: SavedPlace?,
    ): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseManager.getClient()
        val user = authRepository?.getCurrentProfile()
        if (client == null || user == null || !LazeBackendConfig.isConfigured) {
            return@withContext false
        }

        try {
            val existing = getSavedPlaces()
            val existingKeys = existing.map { "${it.latitude},${it.longitude},${it.type}" }.toSet()

            val toUpload = mutableListOf<RemoteSavedPlace>()

            home?.let { h ->
                val key = "${h.location.lat},${h.location.lng},home"
                if (key !in existingKeys) {
                    toUpload.add(
                        RemoteSavedPlace(
                            userId = user.id,
                            name = h.name.ifBlank { "Home" },
                            type = "home",
                            latitude = h.location.lat,
                            longitude = h.location.lng,
                            address = h.address,
                        )
                    )
                }
            }

            work?.let { w ->
                val key = "${w.location.lat},${w.location.lng},work"
                if (key !in existingKeys) {
                    toUpload.add(
                        RemoteSavedPlace(
                            userId = user.id,
                            name = w.name.ifBlank { "Work" },
                            type = "work",
                            latitude = w.location.lat,
                            longitude = w.location.lng,
                            address = w.address,
                        )
                    )
                }
            }

            for (fav in favorites) {
                val key = "${fav.location.lat},${fav.location.lng},favorite"
                if (key !in existingKeys) {
                    toUpload.add(
                        RemoteSavedPlace(
                            userId = user.id,
                            name = fav.name,
                            type = "favorite",
                            latitude = fav.location.lat,
                            longitude = fav.location.lng,
                            address = fav.address,
                        )
                    )
                }
            }

            if (toUpload.isNotEmpty()) {
                client.postgrest.from("saved_places").insert(toUpload)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
