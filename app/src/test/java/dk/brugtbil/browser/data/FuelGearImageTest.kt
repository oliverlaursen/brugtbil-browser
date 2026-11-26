package dk.brugtbil.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelGearImageTest {

    @Test fun parsesFuelTypes() {
        assertEquals(FuelType.PETROL, ListingParser.parseFuel("VW Golf 1,5 TSi 150 Life\nBenzin"))
        assertEquals(FuelType.DIESEL, ListingParser.parseFuel("Skoda Octavia 2,0 TDi 150 Style\nDiesel"))
        assertEquals(FuelType.ELECTRIC, ListingParser.parseFuel("VW ID.3 Pro 58 kWh\nEl"))
        assertEquals(FuelType.HYBRID, ListingParser.parseFuel("Toyota Yaris 1,5 Hybrid\nHybrid (Benzin/El)"))
        assertEquals(FuelType.PLUGIN_HYBRID, ListingParser.parseFuel("Kia Niro 1,6 PHEV\nPlug-in hybrid Benzin"))
        assertNull(ListingParser.parseFuel("Elegance edition"))
    }

    @Test fun parsesGearbox() {
        assertEquals(Gearbox.AUTOMATIC, ListingParser.parseGearbox("VW Golf 1,5 TSi 150 Life DSG"))
        assertEquals(Gearbox.AUTOMATIC, ListingParser.parseGearbox("Peugeot 208 1,2 PureTech 100 Allure aut."))
        assertEquals(Gearbox.MANUAL, ListingParser.parseGearbox("Ford Fiesta 1,0 EcoBoost\nManuel"))
        assertEquals(Gearbox.AUTOMATIC, ListingParser.parseGearbox("Tesla Model 3 Long Range\nEl"))
        assertNull(ListingParser.parseGearbox("Ford Fiesta 1,0 EcoBoost"))
    }

    /** Card text exactly as captured from bilbasen.dk (innerText of a listing card). */
    @Test fun parsesRealBilbasenCard() {
        val card = RawCard(
            href = "https://www.bilbasen.dk/brugt/bil/vw/id4/77-1st-max-5d/7048024",
            title = "VW ID.4",
            text = "VW ID.4\n77 1ST Max 5d\n204.900\u00a0kr.\n6/2021\n133.000 km\n490 km rækkevidde\nEl\nEsbjerg, Syd- og Sønderjylland",
            // The extractor has already dropped the dealer logo (alt="Forhandlerlogo").
            images = listOf("https://billeder.bilbasen.dk/bilinfo/99f2.jpeg?class=S960X960", "https://billeder.bilbasen.dk/bilinfo/f240.jpeg"),
        )
        val l = ListingParser.fromCard(card)!!
        assertEquals(204_900, l.price())
        assertEquals(2021, l.year)
        assertEquals(133_000, l.km)
        assertEquals(FuelType.ELECTRIC, l.fuel)
        assertEquals("https://billeder.bilbasen.dk/bilinfo/99f2.jpeg?class=S960X960", l.imageUrl)
    }

    private fun Listing.price() = priceDkk

    @Test fun jsonLdPhotoWinsOverCardAndCountsPhotos() {
        val page = RawPage(
            cards = listOf(RawCard("https://www.bilbasen.dk/brugt/bil/vw/id4/x/7048024", "VW ID.4", "204.900 kr.\n6/2021\n133.000 km", listOf("https://img/card.jpg"))),
            jsonld = listOf("""{"@type":"ItemList","numberOfItems":44045,"itemListElement":[
                {"@type":"Product","image":["https://img/ld1.jpg","https://img/ld2.jpg","https://img/ld3.jpg"],"name":"VW ID.4","url":"https://www.bilbasen.dk/brugt/bil/vw/id4/x/7048024","offers":{"@type":"Offer","price":204900}},
                {"@type":"Product","image":["https://img/only.jpg"],"name":"VW Golf","url":"https://www.bilbasen.dk/brugt/bil/vw/golf/y/7000001","offers":{"@type":"Offer","price":99000}}]}"""),
        )
        assertEquals(2, ListingParser.expectedListings(page))
        val ls = ListingParser.parse(page).associateBy { it.id }
        assertEquals("https://img/ld1.jpg", ls["7048024"]!!.imageUrl)
        assertEquals(3, ls["7048024"]!!.imageCount)
        assertEquals(2021, ls["7048024"]!!.year) // card data kept
        assertEquals(1, ls["7000001"]!!.imageCount)
    }

    @Test fun groupPrefersListingWithSeveralPhotos() {
        fun l(id: Int, img: String, n: Int) = Listing("$id", "u", "", "kia", "ev4", 300_000, 2025, 1000, imageUrl = img, imageCount = n)
        val g = Aggregator.group(listOf(l(1, "https://img/placeholder.jpg", 1), l(2, "https://img/car.jpg", 12)), SearchFilters()).single()
        assertEquals("https://img/car.jpg", g.imageUrl)
    }

    @Test fun pickImageSkipsSvgAndTakesFirstCarPhoto() {
        assertEquals("https://x/a.jpg", ListingParser.pickImage(listOf("https://x/icon.svg", "https://x/a.jpg", "https://x/b.jpg")))
        assertNull(ListingParser.pickImage(emptyList()))
    }

    @Test fun filtersOnFuelGearboxAndPriceAndKeepsOneImage() {
        fun l(id: Int, price: Int, fuel: FuelType, gb: Gearbox, img: String?) = Listing(
            "$id", "u$id", "", "vw", "golf", price, 2020, 50_000, fuel, gb, img,
        )
        val data = listOf(
            l(1, 150_000, FuelType.PETROL, Gearbox.MANUAL, null),
            l(2, 200_000, FuelType.PETROL, Gearbox.AUTOMATIC, "https://img/2.jpg"),
            l(3, 260_000, FuelType.PLUGIN_HYBRID, Gearbox.AUTOMATIC, "https://img/3.jpg"),
            l(4, 400_000, FuelType.PETROL, Gearbox.AUTOMATIC, "https://img/4.jpg"),
        )
        val f = SearchFilters(fuels = setOf(FuelType.PETROL, FuelType.PLUGIN_HYBRID), gearbox = Gearbox.AUTOMATIC, priceTo = 300_000)
        val g = Aggregator.group(data, f).single()
        assertEquals(2, g.count)
        assertEquals(200_000, g.minPrice)
        assertEquals(260_000, g.maxPrice)
        assertEquals("https://img/2.jpg", g.imageUrl)
        assertEquals(setOf(FuelType.PETROL, FuelType.PLUGIN_HYBRID), g.fuels)
    }
}
