package dk.brugtbil.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingParserTest {

    @Test fun parsesListingUrl() {
        val t = BilbasenUrls.parseListingUrl("https://www.bilbasen.dk/brugt/bil/vw/golf/15-tsi-150-life-dsg-5d/6123456")
        assertEquals(Triple("vw", "golf", "6123456"), t)
        assertNull(BilbasenUrls.parseListingUrl("https://www.bilbasen.dk/brugt/bil/vw/golf"))
    }

    @Test fun parsesCardText() {
        val card = RawCard(
            href = "https://www.bilbasen.dk/brugt/bil/vw/golf/15-tsi-150-life-dsg-5d/6123456?ref=srp",
            title = "VW Golf VIII 1,5 TSi 150 Life DSG",
            text = """
                VW Golf VIII 1,5 TSi 150 Life DSG
                3/2021
                45.000 km
                18,9 km/l
                Benzin
                Aarhus
                239.900 kr.
            """.trimIndent(),
        )
        val l = ListingParser.fromCard(card)!!
        assertEquals("6123456", l.id)
        assertEquals("https://www.bilbasen.dk/brugt/bil/vw/golf/15-tsi-150-life-dsg-5d/6123456", l.url)
        assertEquals(239_900, l.priceDkk)
        assertEquals(2021, l.year)
        assertEquals(45_000, l.km)
    }

    @Test fun ignoresMonthlyPriceAndRange() {
        val text = "Tesla Model 3 · 2020 · 450 km rækkevidde · 3.499 kr./md. · 112.000 km · 189.000 kr."
        assertEquals(189_000, ListingParser.parsePrice(text))
        assertEquals(112_000, ListingParser.parseKm(text))
        assertEquals(2020, ListingParser.parseYear(text))
    }

    @Test fun yearNotConfusedWithPriceOrKm() {
        val text = "Skoda Octavia · 201.900 km · 2.019 kr. ekstra · 2015 · 79.900 kr."
        assertEquals(2015, ListingParser.parseYear(text))
        assertEquals(201_900, ListingParser.parseKm(text))
    }

    @Test fun parsesJsonLdItemList() {
        val ld = """
            {"@context":"https://schema.org","@type":"ItemList","itemListElement":[
              {"@type":"ListItem","position":1,"item":{"@type":"Car","name":"Toyota Yaris 1,5 Hybrid",
                "url":"https://www.bilbasen.dk/brugt/bil/toyota/yaris/15-hybrid/6000001",
                "vehicleModelDate":"2019","mileageFromOdometer":{"@type":"QuantitativeValue","value":"85000","unitCode":"KMT"},
                "offers":{"@type":"Offer","price":"129900","priceCurrency":"DKK"}}}
            ]}
        """.trimIndent()
        val l = ListingParser.fromJsonLd(ld).single()
        assertEquals("toyota", l.makeSlug)
        assertEquals("yaris", l.modelSlug)
        assertEquals(129_900, l.priceDkk)
        assertEquals(2019, l.year)
        assertEquals(85_000, l.km)
    }

    @Test fun decodesExtractorOutputAndMergesSources() {
        val raw = """
            {"cards":[{"href":"https://www.bilbasen.dk/brugt/bil/toyota/yaris/15-hybrid/6000001","title":"Toyota Yaris","text":"Toyota Yaris\nUkendt pris"}],
             "jsonld":["{\"@type\":\"Car\",\"url\":\"https://www.bilbasen.dk/brugt/bil/toyota/yaris/15-hybrid/6000001\",\"offers\":{\"price\":99000}}"],
             "challenge":false,"title":"Bilbasen"}
        """.trimIndent()
        val listings = ListingParser.parse(ListingParser.decodePage(raw))
        assertEquals(1, listings.size)
        assertEquals(99_000, listings[0].priceDkk)
    }
}
