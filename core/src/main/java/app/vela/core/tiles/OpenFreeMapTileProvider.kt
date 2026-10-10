package app.vela.core.tiles

/**
 * Tile provider serving OpenFreeMap Liberty style (FOSS, keyless).
 */
class OpenFreeMapTileProvider(
    private val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
) : TileProvider {
    override val name: String = "OpenFreeMap"
    override fun getStyleUri(): String = styleUrl
}
