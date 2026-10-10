package app.vela.core.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileProviderTest {

    @Test
    fun openFreeMapTileProviderReturnsLibertyStyle() {
        val provider = OpenFreeMapTileProvider()
        assertEquals("OpenFreeMap", provider.name)
        assertEquals("https://tiles.openfreemap.org/styles/liberty", provider.getStyleUri())
    }

    @Test
    fun mapTilerTileProviderWithKeyReturnsMapTilerUrl() {
        val provider = MapTilerTileProvider(apiKey = "my_key", styleName = "streets-v2")
        assertEquals("MapTiler", provider.name)
        assertTrue(provider.getStyleUri().contains("api.maptiler.com"))
        assertTrue(provider.getStyleUri().contains("key=my_key"))
    }

    @Test
    fun mapTilerTileProviderWithoutKeyFallsBackToOpenFreeMap() {
        val provider = MapTilerTileProvider(apiKey = "")
        assertEquals("https://tiles.openfreemap.org/styles/liberty", provider.getStyleUri())
    }

    @Test
    fun dynamicTileProviderAllowsRuntimeSwitching() {
        val dynamic = DynamicTileProvider(OpenFreeMapTileProvider())
        assertEquals("OpenFreeMap", dynamic.name)
        assertEquals("https://tiles.openfreemap.org/styles/liberty", dynamic.getStyleUri())

        val selfHosted = SelfHostedTileProvider("https://tiles.laze-iraq.org/styles/basic/style.json")
        dynamic.switchProvider(selfHosted)

        assertEquals("SelfHosted-Iraq", dynamic.name)
        assertEquals("https://tiles.laze-iraq.org/styles/basic/style.json", dynamic.getStyleUri())
    }
}
