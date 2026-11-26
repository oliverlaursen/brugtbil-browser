package dk.brugtbil.browser.data

/** Applies the filters to listings and collapses them to one row per car model. */
object Aggregator {

    fun matches(l: Listing, f: SearchFilters): Boolean {
        val brand = Catalog.brandSlugFor(f.brand)
        if (brand.isNotEmpty() && Slugs.loose(l.makeSlug.orEmpty()) != Slugs.loose(brand)) return false
        val model = f.modelSlug
        if (model.isNotEmpty() && !Slugs.loose(l.modelSlug.orEmpty()).startsWith(Slugs.loose(model))) return false
        // Listings missing a value are excluded when the user filters on that value.
        if (f.yearFrom != null && (l.year == null || l.year < f.yearFrom)) return false
        if (f.yearTo != null && (l.year == null || l.year > f.yearTo)) return false
        if (f.kmFrom != null && (l.km == null || l.km < f.kmFrom)) return false
        if (f.kmTo != null && (l.km == null || l.km > f.kmTo)) return false
        return true
    }

    fun group(listings: List<Listing>, f: SearchFilters): List<ModelGroup> =
        listings
            .asSequence()
            .filter { it.priceDkk != null && it.makeSlug != null && it.modelSlug != null }
            .filter { matches(it, f) }
            .groupBy { it.makeSlug!! to it.modelSlug!! }
            .map { (key, items) ->
                val (make, model) = key
                val prices = items.mapNotNull { it.priceDkk }
                val years = items.mapNotNull { it.year }
                val kms = items.mapNotNull { it.km }
                ModelGroup(
                    makeSlug = make,
                    modelSlug = model,
                    displayName = "${Catalog.brandDisplayName(make)} ${Catalog.modelDisplayName(make, model)}",
                    minPrice = prices.min(),
                    maxPrice = prices.max(),
                    count = items.size,
                    minYear = years.minOrNull(),
                    maxYear = years.maxOrNull(),
                    minKm = kms.minOrNull(),
                    maxKm = kms.maxOrNull(),
                )
            }
            .sortedWith(compareByDescending<ModelGroup> { it.count }.thenBy { it.displayName })
}
