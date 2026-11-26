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

    @Test fun picksCarImageNotLogo() {
        val card = RawCard(
            href = "https://www.bilbasen.dk/brugt/bil/vw/golf/x/6123456",
            text = "VW Golf\n2020\n50.000 km\n199.900 kr.",
            images = listOf(
                "https://cdn.example.dk/dealer-logo.png",
                "https://billeder.bilbasen.dk/bilinfo/abc.jpg?class=S640X640",
            ),
        )
        assertEquals("https://billeder.bilbasen.dk/bilinfo/abc.jpg?class=S640X640", ListingParser.fromCard(card)!!.imageUrl)
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
