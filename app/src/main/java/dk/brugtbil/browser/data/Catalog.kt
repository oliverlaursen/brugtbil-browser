package dk.brugtbil.browser.data

/**
 * Static list of common brands and models on the Danish used-car market, used for
 * dropdown suggestions. Names are converted to Bilbasen URL slugs via [Slugs.slugify].
 * Brand/model fields also accept free text, so missing entries are not a blocker.
 */
object Catalog {
    data class Brand(val name: String, val slug: String, val models: List<String>)

    val brands: List<Brand> = listOf(
        Brand("Alfa Romeo", "alfa-romeo", listOf("Giulia", "Giulietta", "Stelvio", "Tonale")),
        Brand("Audi", "audi", listOf("A1", "A3", "A4", "A5", "A6", "Q2", "Q3", "Q4 e-tron", "Q5", "e-tron")),
        Brand("BMW", "bmw", listOf("1-serie", "2-serie", "3-serie", "4-serie", "5-serie", "X1", "X3", "X5", "i3", "i4", "iX", "iX3")),
        Brand("BYD", "byd", listOf("Atto 3", "Dolphin", "Seal")),
        Brand("Citroën", "citron", listOf("C1", "C3", "C4", "C5 Aircross", "Berlingo")),
        Brand("Cupra", "cupra", listOf("Born", "Formentor", "Leon")),
        Brand("Dacia", "dacia", listOf("Sandero", "Duster", "Jogger", "Spring")),
        Brand("Fiat", "fiat", listOf("500", "500e", "Panda", "Tipo")),
        Brand("Ford", "ford", listOf("Fiesta", "Focus", "Kuga", "Puma", "Mondeo", "Mustang Mach-E", "Transit Custom")),
        Brand("Honda", "honda", listOf("Civic", "CR-V", "Jazz", "HR-V")),
        Brand("Hyundai", "hyundai", listOf("i10", "i20", "i30", "Kona", "Tucson", "Ioniq 5", "Ioniq 6")),
        Brand("Kia", "kia", listOf("Picanto", "Rio", "Ceed", "Niro", "Sportage", "EV6", "EV9")),
        Brand("Land Rover", "land-rover", listOf("Range Rover", "Range Rover Evoque", "Range Rover Sport", "Discovery", "Defender")),
        Brand("Lexus", "lexus", listOf("NX", "RX", "UX")),
        Brand("Mazda", "mazda", listOf("2", "3", "6", "CX-3", "CX-30", "CX-5", "MX-5")),
        Brand("Mercedes-Benz", "mercedes", listOf("A-Klasse", "B-Klasse", "C-Klasse", "E-Klasse", "GLA", "GLC", "EQA", "EQB", "EQC")),
        Brand("MG", "mg", listOf("MG4", "ZS", "Marvel R")),
        Brand("Mini", "mini", listOf("Cooper", "Cooper SE", "Countryman", "Clubman")),
        Brand("Mitsubishi", "mitsubishi", listOf("Space Star", "Outlander", "ASX", "Eclipse Cross")),
        Brand("Nissan", "nissan", listOf("Micra", "Juke", "Qashqai", "Leaf", "X-Trail", "Ariya")),
        Brand("Opel", "opel", listOf("Corsa", "Astra", "Mokka", "Insignia", "Grandland")),
        Brand("Peugeot", "peugeot", listOf("108", "208", "2008", "308", "3008", "5008", "e-208")),
        Brand("Polestar", "polestar", listOf("2", "3")),
        Brand("Porsche", "porsche", listOf("911", "Cayenne", "Macan", "Taycan")),
        Brand("Renault", "renault", listOf("Clio", "Captur", "Megane", "Zoe", "Kadjar")),
        Brand("Seat", "seat", listOf("Ibiza", "Leon", "Arona", "Ateca", "Mii")),
        Brand("Skoda", "skoda", listOf("Citigo", "Fabia", "Octavia", "Superb", "Kamiq", "Karoq", "Kodiaq", "Enyaq")),
        Brand("Suzuki", "suzuki", listOf("Swift", "Vitara", "S-Cross", "Ignis")),
        Brand("Tesla", "tesla", listOf("Model 3", "Model Y", "Model S", "Model X")),
        Brand("Toyota", "toyota", listOf("Aygo", "Aygo X", "Yaris", "Yaris Cross", "Corolla", "C-HR", "RAV4", "bZ4X")),
        Brand("VW", "vw", listOf("up!", "Polo", "Golf", "T-Roc", "Tiguan", "Passat", "Touran", "ID.3", "ID.4", "ID.5", "ID. Buzz")),
        Brand("Volvo", "volvo", listOf("V40", "V60", "V90", "XC40", "XC60", "XC90", "EX30")),
    )

    fun brandBySlug(slug: String): Brand? = brands.firstOrNull { Slugs.loose(it.slug) == Slugs.loose(slug) }

    /** Resolves what the user typed ("VW", "Volkswagen", "mercedes-benz") into a Bilbasen brand slug. */
    fun brandSlugFor(input: String): String {
        val loose = Slugs.loose(input)
        if (loose.isEmpty()) return ""
        aliases[loose]?.let { return it }
        return brands.firstOrNull { Slugs.loose(it.name) == loose || Slugs.loose(it.slug) == loose }?.slug
            ?: Slugs.slugify(input)
    }

    private val aliases = mapOf(
        "volkswagen" to "vw",
        "citroen" to "citron", // Bilbasen's slug for Citroën
        "mercedesbenz" to "mercedes",
        "mercedes" to "mercedes",
        "landrover" to "land-rover",
        "alfaromeo" to "alfa-romeo",
    )

    fun brandDisplayName(slug: String): String = brandBySlug(slug)?.name ?: prettify(slug)

    fun modelDisplayName(makeSlug: String, modelSlug: String): String =
        brandBySlug(makeSlug)?.models?.firstOrNull { Slugs.loose(it) == Slugs.loose(modelSlug) }
            ?: prettify(modelSlug)

    fun prettify(slug: String): String =
        slug.split('-').filter { it.isNotEmpty() }.joinToString(" ") { part ->
            if (part.length <= 3 && part.any { it.isDigit() }) part.uppercase()
            else part.replaceFirstChar { it.uppercase() }
        }
}
