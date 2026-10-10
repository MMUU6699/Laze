package app.vela.core.backend

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlacesRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `remote saved place serializes correctly`() {
        val place = RemoteSavedPlace(
            id = "place-uuid-1",
            userId = "user-uuid-1",
            name = "المنزل",
            type = "home",
            latitude = 33.3152,
            longitude = 44.3661,
            address = "بغداد، الكرادة",
        )

        val encoded = json.encodeToString(RemoteSavedPlace.serializer(), place)
        val decoded = json.decodeFromString(RemoteSavedPlace.serializer(), encoded)

        assertEquals(place.id, decoded.id)
        assertEquals(place.userId, decoded.userId)
        assertEquals(place.name, decoded.name)
        assertEquals(place.type, decoded.type)
        assertEquals(place.latitude, decoded.latitude, 0.0001)
        assertEquals(place.longitude, decoded.longitude, 0.0001)
        assertEquals(place.address, decoded.address)
    }
}
