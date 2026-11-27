package dk.brugtbil.browser.data

/** A single used-car listing scraped from a Bilbasen search result page. */
data class Listing(
    val id: String,
    val url: String,
    val title: String,
    /** Normalised brand slug, e.g. "vw", "mercedes-benz". */
    val makeSlug: String?,
    /** Normalised model slug, e.g. "golf", "c-klasse". */
    val modelSlug: String?,
    val priceDkk: Int?,
    val year: Int?,
    val km: Int?,
    val fuel: FuelType? = null,
    val gearbox: Gearbox? = null,
    val imageUrl: String? = null,
    /** How many photos the listing has. Listings with a single "photo" are often a
     *  dealer placeholder ("fotografen arbejder") or logo, so they rank last for pictures. */
    val imageCount: Int = 0,
    /** Electric driving range in km ("490 km rækkevidde"), for EVs and plug-ins. */
    val rangeKm: Int? = null,
)

enum class FuelType(val label: String) {
    PETROL("Benzin"),
    DIESEL("Diesel"),
    ELECTRIC("El"),
    HYBRID("Hybrid"),
    PLUGIN_HYBRID("Plug-in hybrid"),
}

enum class Gearbox(val label: String) { AUTOMATIC("Automatisk"), MANUAL("Manuel") }

/** The filters the user can apply. Empty / null values mean "no restriction". */
data class SearchFilters(
    val brand: String = "",
    val model: String = "",
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val kmFrom: Int? = null,
    val kmTo: Int? = null,
    val priceFrom: Int? = null,
    val priceTo: Int? = null,
    /** Empty = all fuel types. */
    val fuels: Set<FuelType> = emptySet(),
    /** null = both. */
    val gearbox: Gearbox? = null,
    /** Minimum electric driving range in km; null = any. */
    val rangeFrom: Int? = null,
) {
    /** Filters that are only applied on the device, so changing them needs no new download. */
    fun withoutLocalOnly() = copy(fuels = emptySet(), gearbox = null, rangeFrom = null)

    val brandSlug: String get() = Slugs.slugify(brand)
    val modelSlug: String get() = Slugs.slugify(model)
}

/** One row in the result list: one car model with its price range. */
data class ModelGroup(
    val makeSlug: String,
    val modelSlug: String,
    val displayName: String,
    val minPrice: Int,
    val maxPrice: Int,
    val count: Int,
    val minYear: Int?,
    val maxYear: Int?,
    val minKm: Int?,
    val maxKm: Int?,
    val imageUrl: String? = null,
    val fuels: Set<FuelType> = emptySet(),
    val minRange: Int? = null,
    val maxRange: Int? = null,
    /** False when no listing in the group has a real photo (see [Listing.imageCount]). */
    val hasGoodPhoto: Boolean = false,
) {
    val key: String get() = "$makeSlug/$modelSlug"
}

object Slugs {
    private val danish = mapOf('æ' to "ae", 'ø' to "oe", 'å' to "aa", 'é' to "e", 'ë' to "e", 'ö' to "o", 'ü' to "u", 'ä' to "a")

    fun slugify(s: String): String {
        val sb = StringBuilder()
        for (c in s.trim().lowercase()) {
            when {
                danish.containsKey(c) -> sb.append(danish[c])
                c.isLetterOrDigit() || c == '.' -> sb.append(c)
                else -> if (sb.isNotEmpty() && sb.last() != '-') sb.append('-')
            }
        }
        return sb.toString().trim('-')
    }

    /** Compares slugs loosely so "id.3", "id-3" and "id3" match. */
    fun loose(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private val generation = Regex("""^(i|ii|iii|iv|v|vi|vii|viii|ix|x|xi|mk\d+)$""")

    /**
     * Strips trailing generation markers so "golf-viii", "golf-vii" and "golf" all become
     * one model ("golf"). At least one token is always kept.
     */
    fun baseModel(modelSlug: String): String {
        val parts = modelSlug.lowercase().split('-').toMutableList()
        while (parts.size > 1 && generation.matches(parts.last())) parts.removeAt(parts.size - 1)
        return parts.joinToString("-")
    }
}
