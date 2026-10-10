package app.vela.core.backend

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrafficProbe(
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("speed_kmh")
    val speedKmh: Double,
    @SerialName("bearing")
    val bearing: Double? = null,
)

/**
 * Manages anonymous live traffic probe telemetry (speed and position) for calculating
 * real-time congestion and feeding live speed updates.
 *
 * All transmissions are strictly anonymous: NO user identifiers, device IDs, or emails are included.
 */
class TrafficProbeRepository(
    private val authRepository: AuthRepository? = null,
) {
    private var lastProbeTimeMs: Long = 0L

    suspend fun sendProbe(
        latitude: Double,
        longitude: Double,
        speedKmh: Double,
        bearing: Double? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Rate-limit: at most one probe every 10 seconds per active drive
        if (now - lastProbeTimeMs < 10_000L) return@withContext false
        if (speedKmh < 3.0) return@withContext false // ignore parked/stationary jitter

        val client = SupabaseManager.getClient()
        if (client == null || !LazeBackendConfig.isConfigured) return@withContext false

        lastProbeTimeMs = now
        try {
            val probe = TrafficProbe(
                latitude = latitude,
                longitude = longitude,
                speedKmh = speedKmh.coerceIn(0.0, 250.0),
                bearing = bearing,
            )
            client.postgrest.from("traffic_probes").insert(probe)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
