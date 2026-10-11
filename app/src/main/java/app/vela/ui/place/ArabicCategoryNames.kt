package app.vela.ui.place

import app.vela.ui.AppLocale
import java.util.Locale

/**
 * Maps English / OSM categories to natural Iraqi / Arabic terminology.
 * When in Arabic locale, returns natural Arabic category names.
 */
object ArabicCategoryNames {

    private val DICTIONARY = mapOf(
        // Food & Drink
        "restaurant" to "مطعم",
        "restaurants" to "مطاعم",
        "cafe" to "مقهى",
        "coffee" to "مقهى وكافيه",
        "coffee shop" to "مقهى",
        "fast food" to "وجبات سريعة",
        "fast food restaurant" to "وجبات سريعة",
        "pizza" to "بيتزا",
        "pizza restaurant" to "مطعم بيتزا",
        "burger" to "برغر",
        "hamburger restaurant" to "مطعم برغر",
        "bakery" to "مخبز ومعجنات",
        "pastry shop" to "حلويات",
        "sweet shop" to "حلويات",
        "ice cream" to "مثلجات وموطا",
        "ice cream shop" to "محل مثلجات",
        "juice shop" to "عصائر طبيعية",
        "barbecue" to "مشاوي وكباب",
        "barbecue restaurant" to "مطعم مشاوي",
        "seafood" to "مأكولات بحرية وسمك",
        "seafood restaurant" to "مطعم مأكولات بحرية",
        "middle eastern restaurant" to "مطعم شرقي",
        "iraqi restaurant" to "مطعم عراقي",
        "syrian restaurant" to "مطعم سوري",
        "turkish restaurant" to "مطعم تركي",
        "lebanese restaurant" to "مطعم لبناني",
        "shawarma" to "شاورما وكص",
        "falafel" to "فلافل",

        // Healthcare
        "hospital" to "مستشفى",
        "clinic" to "عيادة طبية",
        "medical clinic" to "عيادة طبية",
        "pharmacy" to "صيدلية",
        "chemist" to "صيدلية",
        "dentist" to "طب أسنان",
        "dental clinic" to "عيادة أسنان",
        "doctor" to "عيادة طبيب",
        "laboratory" to "مختبر تحليلات مرضية",
        "optician" to "بصريات ونظارات",
        "veterinary care" to "عيادة بيطرية",

        // Retail & Shopping
        "supermarket" to "سوبرماركت",
        "grocery" to "بقالة وأسواق",
        "grocery store" to "أسواق غذائية",
        "convenience store" to "متجر بقالة",
        "shopping mall" to "مجمع تجاري ومول",
        "mall" to "مول تجاري",
        "market" to "سوق",
        "clothing store" to "متجر ملابس",
        "shoe store" to "متجر أحذية",
        "electronics store" to "إلكترونيات وموبايل",
        "cell phone store" to "متجر هواتف",
        "hardware store" to "مواد إنشائية وكهربائية",
        "furniture store" to "مفروشات وأثاث",
        "jewelry store" to "مجوهرات وصاغة",
        "perfume store" to "عطور ومستحضرات تجميل",
        "bookstore" to "مكتبة وقرطاسية",
        "florist" to "زهور وورود",
        "butcher" to "قصاب ولحوم",

        // Fuel & Auto
        "gas station" to "محطة وقود (بنزين)",
        "petrol station" to "محطة وقود (بنزين)",
        "fuel" to "محطة وقود",
        "car repair" to "تصليح سيارات وفيتر",
        "auto repair" to "تصليح سيارات",
        "car wash" to "غسيل وتشحيم سيارات",
        "parking" to "موقف سيارات",
        "parking lot" to "كراج وموقف سيارات",
        "parking garage" to "كراج متعدد الطوابق",
        "tire shop" to "بنشر وإطارات",

        // Finance & Services
        "bank" to "مصرف وبنك",
        "atm" to "صراف آلي (ATM)",
        "post office" to "مكتب بريد",
        "police station" to "مركز شرطة",
        "police" to "شرطة",
        "fire station" to "مركز إطفاء ودفاع مدني",
        "embassy" to "سفارة",
        "courthouse" to "محكمة",
        "government office" to "دائرة حكومية",
        "city hall" to "بلدية",
        "real estate agency" to "مكتب عقارات",
        "travel agency" to "شركة سفر وسياحة",
        "laundry" to "مصبغة وغسيل ملابس",
        "dry cleaner" to "تنظيف جاف ومصبغة",
        "barber" to "حلاقة رجالية",
        "barber shop" to "صالون حلاقة",
        "hair salon" to "صالون حلاقة وتجميل",
        "beauty salon" to "صالون تجميل نسائي",

        // Education & Religion
        "school" to "مدرسة",
        "primary school" to "مدرسة ابتدائية",
        "secondary school" to "مدرسة متوسطة / إعدادية",
        "high school" to "ثانوية / إعدادية",
        "university" to "جامعة",
        "college" to "كلية",
        "kindergarten" to "روضة أطفال",
        "library" to "مكتبة عامة",
        "mosque" to "جامع ومسجد",
        "place of worship" to "دار عبادة",
        "shrine" to "مرقد ومزار شريف",
        "church" to "كنيسة",

        // Recreation & Accommodation
        "hotel" to "فندق",
        "motel" to "نزل",
        "park" to "حديقة ومتنزه",
        "amusement park" to "مدينة ألعاب",
        "gym" to "قاعة رياضية وجيم",
        "fitness center" to "مركز لياقة بدنية",
        "stadium" to "ملعب رياضي",
        "sports club" to "نادي رياضي",
        "swimming pool" to "مسبح",
        "museum" to "متحف",
        "cinema" to "سينما",
        "movie theater" to "دار عرض سينمائي",
        "theater" to "مسرح",

        // Transit
        "airport" to "مطار",
        "bus station" to "كراج حافلات",
        "bus stop" to "موقف حافلات",
        "train station" to "محطة قطار",
        "subway station" to "محطة مترو",
        "metro station" to "محطة مترو",
        "taxi stand" to "موقف تكسي",
    )

    /**
     * Returns the localized Arabic category if [category] matches known POI types
     * and the effective locale is Arabic. Otherwise returns [category] unchanged.
     */
    fun translate(category: String?, isArabic: Boolean = AppLocale.effective().language == "ar"): String? {
        if (category.isNullOrBlank()) return null
        if (!isArabic) return category

        val normalized = category.trim().lowercase(Locale.ROOT)
        // Direct dictionary match
        DICTIONARY[normalized]?.let { return it }

        // Strip trailing punctuation / plural
        val singular = normalized.removeSuffix("s")
        DICTIONARY[singular]?.let { return it }

        // Check if any key matches as substring
        for ((key, arabic) in DICTIONARY) {
            if (normalized == key || normalized.startsWith("$key ") || normalized.endsWith(" $key")) {
                return arabic
            }
        }

        return category
    }
}
