package app.vela.core.backend

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficProbeRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `traffic probe serializes and deserializes properly`() {
        val probe = TrafficProbe(
            latitude = 33.3152,
            longitude = 44.3661,
            speedKmh = 65.5,
            bearing = 180.0
        )

        val encoded = json.encodeToString(TrafficProbe.serializer(), probe)
        val decoded = json.decodeFromString(TrafficProbe.serializer(), encoded)

        assertEquals(probe.latitude, decoded.latitude, 0.0001)
        assertEquals(probe.longitude, decoded.longitude, 0.0001)
        assertEquals(probe.speedKmh, decoded.speedKmh, 0.01)
        assertEquals(probe.bearing, decoded.bearing)
    }

    @Test
    fun `traffic probe repository ignores speeds under 3 kmh`() = runBlocking {
        val repo = TrafficProbeRepository(null)
        // Stationary or GPS drift < 3 km/h
        val sent = repo.sendProbe(33.3152, 44.3661, speedKmh = 1.5, bearing = 90.0)
        assertFalse(sent)
    }

    @Test
    fun `traffic probe repository returns false without configured backend client`() = runBlocking {
        val repo = TrafficProbeRepository(null)
        val sent = repo.sendProbe(33.3152, 44.3661, speedKmh = 50.0, bearing = 90.0)
        assertFalse(sent)
    }
}
