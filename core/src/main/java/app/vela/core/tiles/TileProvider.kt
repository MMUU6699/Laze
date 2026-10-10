package app.vela.core.tiles

/**
 * Common abstraction for basemap tile styles in Laze (OpenFreeMap, MapTiler, Self-hosted PMTiles).
 */
interface TileProvider {
    val name: String

    /**
     * Returns the full URL or JSON spec URI for the map style.
     */
    fun getStyleUri(): String
}
