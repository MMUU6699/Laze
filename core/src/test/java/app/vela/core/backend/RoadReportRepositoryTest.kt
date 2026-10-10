package app.vela.core.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RoadReportRepositoryTest {

    private fun createReport(
        id: String = UUID.randomUUID().toString(),
        type: RoadReportType = RoadReportType.HAZARD,
        lat: Double,
        lng: Double,
        status: RoadReportStatus = RoadReportStatus.ACTIVE,
        score: Double = 1.0,
    ): RoadReport = RoadReport(
        id = id,
        userId = "test-user",
        type = type,
        latitude = lat,
        longitude = lng,
        status = status,
        reliabilityScore = score,
        createdAt = "2026-10-10T00:00:00Z",
        expiresAt = "2026-10-10T02:00:00Z",
    )

    @Test
    fun `findReportsAlongPath returns matching reports within buffer and filters out distant reports via bbox`() {
        val repo = RoadReportRepository()

        // Path between Baghdad center and Kadhimiya (~10 km)
        val path = listOf(
            33.3152 to 44.3661, // Tahrir Square
            33.3400 to 44.3500, // Intermediate
            33.3800 to 44.3400  // Kadhimiya
        )

        val reportOnRoute = createReport(id = "r1", lat = 33.3400, lng = 44.3500) // On path
        val reportNearRoute = createReport(id = "r2", lat = 33.3405, lng = 44.3505) // ~70m from path
        val reportInBasra = createReport(id = "r3", lat = 30.5081, lng = 47.7835) // 500 km away (Basra)
        val reportInErbil = createReport(id = "r4", lat = 36.1900, lng = 44.0100) // 350 km away (Erbil)
        val reportCleared = createReport(id = "r5", lat = 33.3400, lng = 44.3500, status = RoadReportStatus.CLEARED)

        repo.setReportsForTesting(listOf(reportOnRoute, reportNearRoute, reportInBasra, reportInErbil, reportCleared))

        val results = repo.findReportsAlongPath(path, bufferMeters = 200.0)

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "r1" })
        assertTrue(results.any { it.id == "r2" })
        assertTrue(results.none { it.id == "r3" })
        assertTrue(results.none { it.id == "r4" })
        assertTrue(results.none { it.id == "r5" })
    }

    @Test
    fun `findReportsAlongPath returns empty list on empty path or empty reports`() {
        val repo = RoadReportRepository()
        assertTrue(repo.findReportsAlongPath(emptyList()).isEmpty())

        repo.setReportsForTesting(listOf(createReport(lat = 33.3, lng = 44.3)))
        assertTrue(repo.findReportsAlongPath(emptyList()).isEmpty())

        repo.setReportsForTesting(emptyList())
        val path = listOf(33.3 to 44.3)
        assertTrue(repo.findReportsAlongPath(path).isEmpty())
    }

    @Test
    fun `dynamic timeout scales appropriately from short trips to long Iraq trips`() {
        fun calcTimeoutSec(distanceKm: Double): Double =
            (8.0 + 0.1 * distanceKm).coerceIn(15.0, 60.0)

        // Short city trip (3 km): floor at 15s
        assertEquals(15.0, calcTimeoutSec(3.0), 0.001)

        // Mid-distance trip (100 km): 8 + 10 = 18s
        assertEquals(18.0, calcTimeoutSec(100.0), 0.001)

        // Baghdad to Erbil (358 km): 8 + 35.8 = 43.8s
        assertEquals(43.8, calcTimeoutSec(358.0), 0.001)

        // Baghdad to Basra (532 km): capped at 60s
        assertEquals(60.0, calcTimeoutSec(532.0), 0.001)
    }
}
