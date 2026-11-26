package dk.brugtbil.browser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AggregatorTest {
    private fun l(id: Int, make: String, model: String, price: Int?, year: Int?, km: Int?) =
        Listing("$id", "https://www.bilbasen.dk/brugt/bil/$make/$model/x/$id", "", make, model, price, year, km)

    private val data = listOf(
        l(1, "vw", "golf", 150_000, 2018, 90_000),
        l(2, "vw", "golf", 220_000, 2021, 30_000),
        l(3, "vw", "golf", 90_000, 2014, 180_000),
        l(4, "vw", "polo", 110_000, 2019, 60_000),
        l(5, "toyota", "yaris", 125_000, 2020, 50_000),
        l(6, "toyota", "yaris", null, 2020, 50_000), // no price -> ignored
    )

    @Test fun oneRowPerModelWithPriceRange() {
        val groups = Aggregator.group(data, SearchFilters())
        assertEquals(3, groups.size)
        val golf = groups.first()
        assertEquals("VW Golf", golf.displayName)
        assertEquals(3, golf.count)
        assertEquals(90_000, golf.minPrice)
        assertEquals(220_000, golf.maxPrice)
        assertEquals(1, groups.single { it.modelSlug == "yaris" }.count)
    }

    @Test fun appliesAllFilters() {
        val f = SearchFilters(brand = "Volkswagen", model = "Golf", yearFrom = 2016, kmTo = 100_000)
        val groups = Aggregator.group(data, f)
        val golf = groups.single()
        assertEquals(2, golf.count)
        assertEquals(150_000, golf.minPrice)
        assertEquals(220_000, golf.maxPrice)
        assertEquals(2018, golf.minYear)
        assertEquals(30_000, golf.minKm)
    }

    @Test fun buildsSearchUrl() {
        val f = SearchFilters(brand = "VW", model = "ID.3", yearFrom = 2020, yearTo = 2023, kmTo = 80_000)
        assertEquals(
            "https://www.bilbasen.dk/brugt/bil/vw/id.3?includeengroscvr=true&includeleasing=false&yearfrom=2020&yearto=2023&mileageto=80000",
            BilbasenUrls.search(f),
        )
        assertEquals(
            "https://www.bilbasen.dk/brugt/bil?includeengroscvr=true&includeleasing=false&page=3",
            BilbasenUrls.search(SearchFilters(), page = 3),
        )
    }
}
