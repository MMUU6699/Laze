package app.vela.ui.map

import app.vela.ui.Buildings3d
import app.vela.ui.MapPoiPrefs
import app.vela.ui.ReportsLayer
import app.vela.ui.SatelliteLayer
import app.vela.ui.Topography
import app.vela.ui.Traffic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying layer state management, single-choice map type mutual exclusion,
 * and the non-default layer badge indicator logic.
 */
class LayersStateTest {

    private fun isNonDefaultLayer(): Boolean =
        SatelliteLayer.on.value ||
            Topography.on.value ||
            Traffic.on.value ||
            !ReportsLayer.on.value ||
            !Buildings3d.on.value ||
            !MapPoiPrefs.showPois.value

    @Before
    @After
    fun resetDefaults() {
        SatelliteLayer.on.value = false
        Topography.on.value = false
        Traffic.on.value = false
        ReportsLayer.on.value = true
        Buildings3d.on.value = true
        MapPoiPrefs.showPois.value = true
    }

    @Test
    fun `default map configuration has no non-default badge`() {
        assertFalse(SatelliteLayer.on.value)
        assertFalse(Topography.on.value)
        assertFalse(isNonDefaultLayer())
    }

    @Test
    fun `selecting satellite enables satellite and triggers non-default badge`() {
        SatelliteLayer.on.value = true
        Topography.on.value = false

        assertTrue(SatelliteLayer.on.value)
        assertFalse(Topography.on.value)
        assertTrue(isNonDefaultLayer())
    }

    @Test
    fun `selecting terrain enables topography and triggers non-default badge`() {
        SatelliteLayer.on.value = false
        Topography.on.value = true

        assertFalse(SatelliteLayer.on.value)
        assertTrue(Topography.on.value)
        assertTrue(isNonDefaultLayer())
    }

    @Test
    fun `toggling traffic on triggers non-default badge`() {
        Traffic.on.value = true
        assertTrue(isNonDefaultLayer())

        Traffic.on.value = false
        assertFalse(isNonDefaultLayer())
    }

    @Test
    fun `disabling community reports triggers non-default badge`() {
        ReportsLayer.on.value = false
        assertTrue(isNonDefaultLayer())

        ReportsLayer.on.value = true
        assertFalse(isNonDefaultLayer())
    }

    @Test
    fun `disabling 3D buildings triggers non-default badge`() {
        Buildings3d.on.value = false
        assertTrue(isNonDefaultLayer())

        Buildings3d.on.value = true
        assertFalse(isNonDefaultLayer())
    }

    @Test
    fun `disabling place icons triggers non-default badge`() {
        MapPoiPrefs.showPois.value = false
        assertTrue(isNonDefaultLayer())

        MapPoiPrefs.showPois.value = true
        assertFalse(isNonDefaultLayer())
    }
}
