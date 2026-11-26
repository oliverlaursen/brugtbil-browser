package dk.brugtbil.browser.data

/**
 * Builds Bilbasen search URLs. Brand and model go in the path
 * (https://www.bilbasen.dk/brugt/bil/{brand}/{model}); year and mileage go in the query.
 *
 * The app also filters every listing client-side ([Aggregator]), so results stay correct
 * even if Bilbasen ignores or renames a query parameter. The server-side parameters only
 * cut down how many pages have to be loaded.
 */
object BilbasenUrls {
    const val BASE = "https://www.bilbasen.dk"

    fun search(filters: SearchFilters, page: Int = 1): String {
        val sb = StringBuilder("$BASE/brugt/bil")
        val brand = Catalog.brandSlugFor(filters.brand)
        if (brand.isNotEmpty()) {
            sb.append('/').append(brand)
            val model = filters.modelSlug
            if (model.isNotEmpty()) sb.append('/').append(model)
        }
        val params = buildList {
            add("includeengroscvr" to "true")
            add("includeleasing" to "false")
            filters.yearFrom?.let { add("yearfrom" to it.toString()) }
            filters.yearTo?.let { add("yearto" to it.toString()) }
            filters.kmFrom?.let { add("mileagefrom" to it.toString()) }
            filters.kmTo?.let { add("mileageto" to it.toString()) }
            if (page > 1) add("page" to page.toString())
        }
        sb.append('?').append(params.joinToString("&") { "${it.first}=${it.second}" })
        return sb.toString()
    }

    private val listingPath = Regex("""/brugt/bil/([^/?#]+)/([^/?#]+)/(?:[^?#]*/)?(\d{5,})/?(?:[?#].*)?$""")

    /** Parses make slug, model slug and listing id from a listing URL. */
    fun parseListingUrl(url: String): Triple<String, String, String>? {
        val m = listingPath.find(url) ?: return null
        return Triple(m.groupValues[1].lowercase(), m.groupValues[2].lowercase(), m.groupValues[3])
    }
}
