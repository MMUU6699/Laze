package app.vela.core.tiles

/**
 * Tile provider serving MapTiler styles with optional API key.
 */
class MapTilerTileProvider(
    private val apiKey: String = "",
    private val styleName: String = "streets-v2",
) : TileProvider {
    override val name: String = "MapTiler"

    override fun getStyleUri(): String =
        if (apiKey.isNotBlank()) {
            "https://api.maptiler.com/maps/$styleName/style.json?key=$apiKey"
        } else {
            "https://tiles.openfreemap.org/styles/liberty"
        }
}
