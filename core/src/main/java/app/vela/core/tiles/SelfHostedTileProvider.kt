package app.vela.core.tiles

/**
 * Tile provider serving self-hosted Iraq PMTiles / vector tiles from VPS or object storage CDN.
 */
class SelfHostedTileProvider(
    private val styleUrl: String,
    override val name: String = "SelfHosted-Iraq",
) : TileProvider {
    override fun getStyleUri(): String = styleUrl
}
