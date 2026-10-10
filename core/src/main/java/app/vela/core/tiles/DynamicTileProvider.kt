package app.vela.core.tiles

import java.util.concurrent.atomic.AtomicReference

/**
 * Dynamic tile provider that allows over-the-air style updates or runtime fallback.
 */
class DynamicTileProvider(
    initialProvider: TileProvider = OpenFreeMapTileProvider(),
) : TileProvider {

    private val current = AtomicReference(initialProvider)

    override val name: String
        get() = current.get().name

    override fun getStyleUri(): String =
        current.get().getStyleUri()

    fun switchProvider(provider: TileProvider) {
        current.set(provider)
    }
}
