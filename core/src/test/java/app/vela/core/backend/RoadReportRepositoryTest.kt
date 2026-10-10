package app.vela.core.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RoadReportRepositoryTest {

    @Test
    fun `submitReport queues into outbox and returns SavedOffline when backend client is null`() = runBlocking {
        // Reset client so it's unconfigured / offline
        SupabaseManager.resetClient()

        val repo = RoadReportRepository()

        val result = repo.submitReport(
            type = RoadReportType.HAZARD,
            latitude = 33.3152,
            longitude = 44.3661,
            description = "مطب عميق"
        )

        // Must return SavedOffline honestly
        assertTrue("Expected SavedOffline, got $result", result is ReportSubmitResult.SavedOffline)
        val savedReport = (result as ReportSubmitResult.SavedOffline).report

        // Must appear in optimistic local reports list for map rendering
        assertEquals(1, repo.reports.value.size)
        assertEquals(savedReport.id, repo.reports.value[0].id)
        assertEquals("مطب عميق", repo.reports.value[0].description)

        // Must be queued in outbox
        val outbox = repo.getOutboxItemsForTesting()
        assertEquals(1, outbox.size)
        assertEquals(savedReport.id, outbox[0].report.id)
    }

    @Test
    fun `flushOutbox prunes items older than 30 minutes`() = runBlocking {
        val repo = RoadReportRepository()
        val now = System.currentTimeMillis()

        // Expired item (queued 35 minutes ago)
        val oldReport = RoadReport(
            id = UUID.randomUUID().toString(),
            type = RoadReportType.ACCIDENT,
            latitude = 33.3,
            longitude = 44.3,
            createdAt = "2026-10-10T00:00:00Z",
            expiresAt = "2026-10-10T01:00:00Z"
        )
        val expiredOutboxItem = OutboxReport(
            report = oldReport,
            queuedAtTimestampMs = now - (35 * 60 * 1000L)
        )

        // Fresh item (queued 5 minutes ago)
        val freshReport = RoadReport(
            id = UUID.randomUUID().toString(),
            type = RoadReportType.TRAFFIC,
            latitude = 33.4,
            longitude = 44.4,
            createdAt = "2026-10-10T00:00:00Z",
            expiresAt = "2026-10-10T01:00:00Z"
        )
        val freshOutboxItem = OutboxReport(
            report = freshReport,
            queuedAtTimestampMs = now - (5 * 60 * 1000L)
        )

        repo.addOutboxItemForTesting(expiredOutboxItem)
        repo.addOutboxItemForTesting(freshOutboxItem)

        assertEquals(2, repo.getOutboxItemsForTesting().size)

        // Flush without client will prune expired and retain fresh
        repo.flushOutbox()

        val remaining = repo.getOutboxItemsForTesting()
        assertEquals("Expired item should be discarded, retaining only fresh item", 1, remaining.size)
        assertEquals(freshReport.id, remaining[0].report.id)
    }
}
