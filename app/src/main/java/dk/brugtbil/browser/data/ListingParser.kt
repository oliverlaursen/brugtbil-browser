package dk.brugtbil.browser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Raw data the extractor script ([ExtractorScript]) pulls out of a rendered search page. */
@Serializable
data class RawPage(
    val cards: List<RawCard> = emptyList(),
    val jsonld: List<String> = emptyList(),
    val challenge: Boolean = false,
    val title: String = "",
)

@Serializable
data class RawCard(
    val href: String,
    val title: String = "",
    val text: String = "",
    /** Candidate image URLs found inside the card, in document order. */
    val images: List<String> = emptyList(),
)

/**
 * Turns the raw page dump into [Listing]s. Two sources are combined:
 *  1. schema.org JSON-LD blocks (structured, preferred where present)
 *  2. the visible text of each listing card (fallback, parsed with regexes)
 */
object ListingParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decodePage(rawJson: String): RawPage = json.decodeFromString(RawPage.serializer(), rawJson)

    fun parse(page: RawPage): List<Listing> {
        val byId = LinkedHashMap<String, Listing>()
        for (card in page.cards) {
            val l = fromCard(card) ?: continue
            byId[l.id] = l
        }
        for (block in page.jsonld) {
            for (l in fromJsonLd(block)) {
                val existing = byId[l.id]
                byId[l.id] = if (existing == null) l else existing.copy(
                    priceDkk = l.priceDkk ?: existing.priceDkk,
                    year = l.year ?: existing.year,
                    km = l.km ?: existing.km,
                    fuel = existing.fuel ?: l.fuel,
                    gearbox = existing.gearbox ?: l.gearbox,
                    imageUrl = existing.imageUrl ?: l.imageUrl,
                    title = existing.title.ifBlank { l.title },
                )
            }
        }
        return byId.values.toList()
    }

    // ---- Card text -------------------------------------------------------------------

    fun fromCard(card: RawCard): Listing? {
        val (make, model, id) = BilbasenUrls.parseListingUrl(card.href) ?: return null
        val text = card.text.replace(' ', ' ')
        val all = card.title + "\n" + text
        return Listing(
            id = id,
            url = card.href.substringBefore('?'),
            title = card.title.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
            makeSlug = make,
            modelSlug = model,
            priceDkk = parsePrice(text),
            year = parseYear(text),
            km = parseKm(text),
            fuel = parseFuel(all),
            gearbox = parseGearbox(all),
            imageUrl = pickImage(card.images),
        )
    }

    private val pluginRe = Regex("""\b(plug-?in|phev|e-hybrid|recharge)\b""", RegexOption.IGNORE_CASE)
    private val hybridRe = Regex("""\b(hybrid|hev|mhev)\b""", RegexOption.IGNORE_CASE)
    private val electricRe = Regex("""\b(el|electric|elbil|kwh|e-tron|bev)\b""", RegexOption.IGNORE_CASE)
    private val dieselRe = Regex("""\b(diesel|tdi|crdi|dci|hdi|bluehdi|cdi|multijet|ecoblue)\b""", RegexOption.IGNORE_CASE)
    private val petrolRe = Regex("""\b(benzin|tsi|tfsi|t-gdi|gdi|puretech|ecoboost|tce|vti)\b""", RegexOption.IGNORE_CASE)

    /** Order matters: "Hybrid (benzin)" is a hybrid, a plug-in often mentions kWh, etc. */
    fun parseFuel(text: String): FuelType? = when {
        pluginRe.containsMatchIn(text) -> FuelType.PLUGIN_HYBRID
        hybridRe.containsMatchIn(text) -> FuelType.HYBRID
        electricRe.containsMatchIn(text) -> FuelType.ELECTRIC
        dieselRe.containsMatchIn(text) -> FuelType.DIESEL
        petrolRe.containsMatchIn(text) -> FuelType.PETROL
        else -> null
    }

    private val autoRe = Regex("""\b(automatisk|automatgear|automat|aut|dsg|s[- ]tronic|steptronic|cvt|e-cvt|eat\d|edc|tiptronic|powershift)\b""", RegexOption.IGNORE_CASE)
    private val manualRe = Regex("""\b(manuel|manuelt)\b""", RegexOption.IGNORE_CASE)

    fun parseGearbox(text: String): Gearbox? = when {
        manualRe.containsMatchIn(text) -> Gearbox.MANUAL
        autoRe.containsMatchIn(text) -> Gearbox.AUTOMATIC
        parseFuel(text) == FuelType.ELECTRIC -> Gearbox.AUTOMATIC // EVs have no manual gearbox
        else -> null
    }

    private val notCarImage = Regex("""(logo|icon|sprite|placeholder|avatar|badge|\.svg)""", RegexOption.IGNORE_CASE)

    fun pickImage(candidates: List<String>): String? =
        candidates.firstOrNull { it.startsWith("http") && !notCarImage.containsMatchIn(it) }

    private const val NUM = """\d{1,3}(?:[.\s]\d{3})+|\d+"""

    // "249.900 kr." but not "2.499 kr./md." (leasing / monthly payment)
    private val priceRe = Regex("""($NUM)\s*kr(?!\.?\s*(?:/\s*md|pr\.?\s*md|/\s*måned|om måneden))""", RegexOption.IGNORE_CASE)

    // "123.000 km" but not "18,5 km/l" or "450 km rækkevidde"
    private val kmRe = Regex("""(?<![\d,])($NUM)\s*km\b(?!\s*/)(?!\s*rækkevidde)(?!\s*\(WLTP)""", RegexOption.IGNORE_CASE)

    // "3/2019", "03-2019", or a standalone "2019"
    private val monthYearRe = Regex("""(?<![\d.])(?:0?[1-9]|1[0-2])[/-]((?:19|20)\d{2})(?!\d)""")
    private val yearRe = Regex("""(?<![\d.,/])((?:19[5-9]|20[0-4])\d)(?![\d.,])""")

    fun parsePrice(text: String): Int? =
        priceRe.findAll(text).mapNotNull { toInt(it.groupValues[1]) }.firstOrNull { it >= 1000 }

    fun parseKm(text: String): Int? {
        val m = kmRe.findAll(text).firstOrNull() ?: return null
        return toInt(m.groupValues[1])
    }

    fun parseYear(text: String): Int? {
        monthYearRe.find(text)?.let { return it.groupValues[1].toInt() }
        // Remove numbers that belong to prices / mileage before looking for a bare year.
        val cleaned = text.replace(priceRe, " ").replace(kmRe, " ")
        return yearRe.findAll(cleaned).map { it.groupValues[1].toInt() }.firstOrNull { it in 1950..2035 }
    }

    private fun toInt(s: String): Int? = s.filter { it.isDigit() }.toIntOrNull()

    // ---- JSON-LD ---------------------------------------------------------------------

    fun fromJsonLd(block: String): List<Listing> {
        val root = runCatching { json.parseToJsonElement(block) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Listing>()
        collectVehicles(root, out)
        return out
    }

    private fun collectVehicles(e: JsonElement, out: MutableList<Listing>) {
        when (e) {
            is JsonArray -> e.forEach { collectVehicles(it, out) }
            is JsonObject -> {
                vehicleFrom(e)?.let { out += it }
                // ItemList -> itemListElement -> ListItem -> item, @graph, etc.
                for (key in listOf("itemListElement", "item", "@graph", "mainEntity")) {
                    e[key]?.let { collectVehicles(it, out) }
                }
            }
            else -> Unit
        }
    }

    private fun vehicleFrom(o: JsonObject): Listing? {
        val types = when (val t = o["@type"]) {
            is JsonPrimitive -> listOf(t.contentOrNull.orEmpty())
            is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        if (types.none { it in setOf("Car", "Vehicle", "Product", "Offer") }) return null
        val url = o.str("url") ?: (o["offers"] as? JsonObject)?.str("url") ?: return null
        val (make, model, id) = BilbasenUrls.parseListingUrl(url) ?: return null
        val offers = when (val off = o["offers"]) {
            is JsonObject -> off
            is JsonArray -> off.firstOrNull() as? JsonObject
            else -> null
        }
        val price = (offers?.str("price") ?: o.str("price"))?.let { p ->
            p.substringBefore(',').filter { it.isDigit() }.toIntOrNull()
        }
        val year = (o.str("vehicleModelDate") ?: o.str("productionDate") ?: o.str("dateVehicleFirstRegistered"))
            ?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toInt() }
        val km = when (val m = o["mileageFromOdometer"]) {
            is JsonObject -> m.str("value")
            is JsonPrimitive -> m.contentOrNull
            else -> null
        }?.let { v -> v.substringBefore(',').filter { it.isDigit() }.toIntOrNull() }
        val image = when (val img = o["image"]) {
            is JsonPrimitive -> img.contentOrNull
            is JsonArray -> (img.firstOrNull() as? JsonPrimitive)?.contentOrNull
                ?: (img.firstOrNull() as? JsonObject)?.str("url")
            is JsonObject -> img.str("url") ?: img.str("contentUrl")
            else -> null
        }
        val fuel = (o.str("fuelType") ?: (o["vehicleEngine"] as? JsonObject)?.str("fuelType"))
            ?.let { parseFuel(it) }
        val gearbox = o.str("vehicleTransmission")?.let { parseGearbox(it) }
        return Listing(
            id, url.substringBefore('?'), o.str("name").orEmpty(), make, model, price, year, km,
            fuel = fuel, gearbox = gearbox, imageUrl = image?.takeIf { it.startsWith("http") },
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
}
