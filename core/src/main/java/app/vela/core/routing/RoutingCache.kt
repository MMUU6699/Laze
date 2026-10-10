package app.vela.core.routing

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import java.util.LinkedHashMap
import kotlin.math.roundToLong

/**
 * Thread-safe LRU in-memory routing cache with a 60-second TTL.
 *
 * Quantizes coordinates to ~11 meters (1e-4 degrees) so slight GPS jitter
 * or repeated taps between screens return cached routes instantaneously.
 */
object RoutingCache {
    const val TTL_MS = 60_000L
    private const val MAX_ENTRIES = 50

    data class Key(
        val originLat1e4: Long,
        val originLng1e4: Long,
        val destLat1e4: Long,
        val destLng1e4: Long,
        val mode: TravelMode,
        val waypointsKey: List<Pair<Long, Long>>,
        val avoidTolls: Boolean,
        val avoidHighways: Boolean,
        val avoidFerries: Boolean,
    )

    private data class Entry(
        val routes: List<Route>,
        val timestampMs: Long,
    )

    private val lock = Any()
    private val cache = object : LinkedHashMap<Key, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun quantize(coord: Double): Long = (coord * 10_000.0).roundToLong()

    fun makeKey(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        waypoints: List<LatLng> = emptyList(),
        avoidTolls: Boolean = false,
        avoidHighways: Boolean = false,
        avoidFerries: Boolean = false,
    ): Key = Key(
        originLat1e4 = quantize(origin.lat),
        originLng1e4 = quantize(origin.lng),
        destLat1e4 = quantize(destination.lat),
        destLng1e4 = quantize(destination.lng),
        mode = mode,
        waypointsKey = waypoints.map { quantize(it.lat) to quantize(it.lng) },
        avoidTolls = avoidTolls,
        avoidHighways = avoidHighways,
        avoidFerries = avoidFerries,
    )

    fun get(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        waypoints: List<LatLng> = emptyList(),
        avoidTolls: Boolean = false,
        avoidHighways: Boolean = false,
        avoidFerries: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Route>? {
        val key = makeKey(origin, destination, mode, waypoints, avoidTolls, avoidHighways, avoidFerries)
        synchronized(lock) {
            val entry = cache[key] ?: return null
            if (nowMs - entry.timestampMs > TTL_MS) {
                cache.remove(key)
                return null
            }
            return entry.routes
        }
    }

    fun put(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        waypoints: List<LatLng> = emptyList(),
        avoidTolls: Boolean = false,
        avoidHighways: Boolean = false,
        avoidFerries: Boolean = false,
        routes: List<Route>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (routes.isEmpty()) return
        val key = makeKey(origin, destination, mode, waypoints, avoidTolls, avoidHighways, avoidFerries)
        synchronized(lock) {
            cache[key] = Entry(routes, nowMs)
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    val size: Int
        get() = synchronized(lock) { cache.size }
}
