package app.vela.ui

import app.vela.ui.icons.Sym
import androidx.compose.ui.graphics.vector.ImageVector
import app.vela.R

/**
 * The unified list of quick categories for Laze in Iraqi Arabic and general navigation.
 * Standardized across the search sheet 8-item grid, route chooser, drive overlays, and car screens.
 */
object QuickCategories {
    data class Chip(val label: Int, val query: String, val icon: ImageVector)

    /** The primary 8 categories shown in the 2x4 / 4x2 grid on the search sheet. */
    fun primary(): List<Chip> = listOf(
        Chip(R.string.cat_coffee, "مقهى", Sym.LocalCafe),
        Chip(R.string.cat_restaurants, "مطعم", Sym.Restaurant),
        Chip(R.string.cat_gas, "محطة وقود", Sym.LocalGasStation),
        Chip(R.string.cat_groceries, "أسواق", Sym.LocalGroceryStore),
        Chip(R.string.cat_pharmacy, "صيدلية", Sym.LocalPharmacy),
        Chip(R.string.cat_hospitals, "مستشفى", Sym.LocalHospital),
        Chip(R.string.cat_atms, "صراف", Sym.LocalAtm),
        Chip(R.string.cat_parking, "موقف سيارات", Sym.LocalParking),
    )

    /** Additional categories available under "المزيد" / More. */
    fun more(): List<Chip> = listOf(
        Chip(R.string.cat_mosques, "جامع", Sym.Mosque),
        Chip(R.string.cat_hotels, "فندق", Sym.Hotel),
        Chip(R.string.cat_banks, "مصرف", Sym.AccountBalance),
        Chip(R.string.cat_parks, "حديقة", Sym.Park),
        Chip(R.string.cat_ev, "شحن كهربائي", Sym.EvStation),
    )

    /** All categories. */
    fun all(): List<Chip> = primary() + more()

    /** Priority categories during active driving (fuel, coffee, mosques, parking lead). */
    fun forDrive(): List<Chip> = all().let { list ->
        val first = setOf(R.string.cat_gas, R.string.cat_coffee, R.string.cat_mosques, R.string.cat_parking)
        list.filter { it.label in first } + list.filter { it.label !in first }
    }
}
