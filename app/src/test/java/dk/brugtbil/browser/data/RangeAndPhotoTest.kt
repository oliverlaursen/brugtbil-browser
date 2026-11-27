package dk.brugtbil.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeAndPhotoTest {

    @Test fun parsesDrivingRange() {
        assertEquals(490, ListingParser.parseRange("133.000 km\n490 km rækkevidde\nEl"))
        assertEquals(420, ListingParser.parseRange("Rækkevidde: 420 km"))
        assertEquals(55, ListingParser.parseRange("55 km (WLTP) rækkevidde"))
        assertNull(ListingParser.parseRange("VW Golf\n133.000 km\nBenzin"))
    }

    @Test fun rangeIsNotMistakenForMileage() {
        val l = ListingParser.fromCard(
            RawCard("https://www.bilbasen.dk/brugt/bil/vw/id4/x/7048024", "VW ID.4",
                "204.900 kr.\n6/2021\n133.000 km\n490 km rækkevidde\nEl"),
        )!!
        assertEquals(133_000, l.km)
        assertEquals(490, l.rangeKm)
    }

    private fun ev(id: String, model: String, range: Int?, photos: Int = 5, img: String? = "https://img/$id.jpg") =
        Listing(id, "u$id", "t", "vw", model, 200_000, 2021, 50_000, FuelType.ELECTRIC,
            imageUrl = img, imageCount = photos, rangeKm = range)

    @Test fun filtersOnRangeAndShowsRangeOnGroup() {
        val ls = listOf(ev("1", "id4", 350), ev("2", "id4", 490), ev("3", "id4", null), ev("4", "e-up", 250))
        val all = Aggregator.group(ls, SearchFilters())
        val id4 = all.single { it.modelSlug == "id4" }
        assertEquals(350, id4.minRange)
        assertEquals(490, id4.maxRange)

        val far = Aggregator.group(ls, SearchFilters(rangeFrom = 400))
        assertEquals(1, far.size)
        assertEquals(1, far.single().count) // listings without a range are excluded
        assertEquals(SearchFilters(), SearchFilters(rangeFrom = 400).withoutLocalOnly())
    }

    @Test fun modelWithoutRealPhotoUsesOverride() {
        val ls = listOf(
            ev("1", "3008", null, photos = 1, img = "https://www.bilbasen.dk/_next/static/media/missing.c2ef8341.svg"),
            ev("2", "golf", null, photos = 1, img = "https://img/placeholder.jpg"),
        )
        val plain = Aggregator.group(ls, SearchFilters()).associateBy { it.modelSlug }
        assertFalse(plain.getValue("3008").hasGoodPhoto)
        assertNull(plain.getValue("3008").imageUrl) // the "missing" icon is never shown
        assertFalse(plain.getValue("golf").hasGoodPhoto)

        val fixed = Aggregator.group(ls, SearchFilters(), mapOf("vw/3008" to "https://img/real.jpg"))
            .associateBy { it.modelSlug }
        assertTrue(fixed.getValue("3008").hasGoodPhoto)
        assertEquals("https://img/real.jpg", fixed.getValue("3008").imageUrl)
    }

    @Test fun citroenUsesBilbasenSlug() {
        assertEquals("citron", Catalog.brandSlugFor("Citroën"))
        assertEquals("citron", Catalog.brandSlugFor("citroen"))
        assertEquals("Citroën", Catalog.brandDisplayName("citron"))
    }
}
