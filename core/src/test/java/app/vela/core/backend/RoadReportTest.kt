package app.vela.core.backend

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadReportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRoadReportSerialization() {
        val report = RoadReport(
            id = "123e4567-e89b-12d3-a456-426614174000",
            userId = "user-123",
            type = RoadReportType.ACCIDENT,
            latitude = 24.7136,
            longitude = 46.6753,
            severity = RoadReportSeverity.HIGH,
            description = "Accident on King Fahd Rd",
            roadName = "King Fahd Rd",
            status = RoadReportStatus.ACTIVE,
            confirmationsCount = 3,
            rejectionsCount = 0,
            reliabilityScore = 2.0
        )

        val encoded = json.encodeToString(RoadReport.serializer(), report)
        val decoded = json.decodeFromString(RoadReport.serializer(), encoded)

        assertEquals(report.id, decoded.id)
        assertEquals(report.type, decoded.type)
        assertEquals(report.severity, decoded.severity)
        assertEquals(report.latitude, decoded.latitude, 0.0001)
        assertEquals(report.longitude, decoded.longitude, 0.0001)
        assertEquals("King Fahd Rd", decoded.roadName)
        assertTrue(decoded.isReliable)
    }

    @Test
    fun testReliabilityStatus() {
        val activeReliable = RoadReport(
            type = RoadReportType.TRAFFIC,
            latitude = 0.0,
            longitude = 0.0,
            status = RoadReportStatus.ACTIVE,
            reliabilityScore = 1.0,
            rejectionsCount = 0
        )
        assertTrue(activeReliable.isReliable)

        val lowScore = activeReliable.copy(reliabilityScore = 0.3)
        assertFalse(lowScore.isReliable)

        val rejected = activeReliable.copy(rejectionsCount = 3)
        assertFalse(rejected.isReliable)

        val cleared = activeReliable.copy(status = RoadReportStatus.CLEARED)
        assertFalse(cleared.isReliable)
    }

    @Test
    fun testDistanceCalculation() {
        // Riyadh (24.7136, 46.6753) to nearby point ~1.1 km away (24.7236, 46.6753)
        val report = RoadReport(
            type = RoadReportType.SPEED_CAMERA,
            latitude = 24.7136,
            longitude = 46.6753
        )

        val distance = report.distanceMetersTo(24.7236, 46.6753)
        // 0.01 deg latitude ~= 1110 meters
        assertTrue("Distance should be around 1110m but was $distance", distance in 1000.0..1200.0)
    }

    @Test
    fun testTypeAndSeverityMappings() {
        assertEquals(RoadReportType.ACCIDENT, RoadReportType.fromApi("accident"))
        assertEquals(RoadReportType.SPEED_CAMERA, RoadReportType.fromApi("camera"))
        assertEquals(RoadReportType.CLOSURE, RoadReportType.fromApi("closure"))
        assertEquals(RoadReportType.HAZARD, RoadReportType.fromApi("unknown_type"))

        assertEquals(RoadReportSeverity.SEVERE, RoadReportSeverity.fromApi("severe"))
        assertEquals(RoadReportSeverity.LOW, RoadReportSeverity.fromApi("low"))
        assertEquals(RoadReportSeverity.MODERATE, RoadReportSeverity.fromApi("other"))
    }
}
