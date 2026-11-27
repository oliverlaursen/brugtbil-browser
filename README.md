# Brugtbil Browser

An Android app (Kotlin + Jetpack Compose) for browsing used cars on [Bilbasen](https://www.bilbasen.dk).
Instead of showing every listing, it shows **one row per car model** with a picture, the **price range**
and the count, year range, mileage range and fuel types of the listings that match your filters.
The picture is the first car photo of the first matching listing with several photos (the dealer's logo is skipped). If no listing of a model has real photos yet (Bilbasen shows a "missing" icon or a dealer placeholder), the app looks up a photo on that model's own Bilbasen page after the search.

## Filters
- Brand (Mærke) – dropdown with common brands, or free text
- Model – dropdown with that brand's common models, or free text
- Model year (Årgang) from / to
- Kilometers driven from / to
- Price from / to
- Fuel type (Drivmiddel): petrol, diesel, electric, hybrid, plug-in hybrid (pick several)
- Gearbox (Gearkasse): automatic / manual
- Electric range (Rækkevidde fra): minimum driving range in km; cards show the range span for EVs and plug-ins

Fuel type and gearbox are worked out from the listing text (e.g. "Benzin", "TDi", "kWh",
"DSG", "Manuel") and filtered on the phone only, so changing them re-groups the results
instantly without a new download.

Tapping a model opens the matching Bilbasen search in the browser.

## How the Bilbasen integration works
Bilbasen has no public API, and its pages are behind an AWS WAF JavaScript bot check,
so a plain HTTP client gets turned away. The app instead loads the search pages in a hidden
`WebView` on the phone, like a normal browser:

1. `BilbasenUrls` builds the search URL: brand/model in the path
   (`/brugt/bil/{brand}/{model}`), and `yearfrom`, `yearto`, `mileagefrom`, `mileageto`,
   `pricefrom`, `priceto` and `page` in the query.
2. `BilbasenClient` loads up to 10 result pages and runs `ExtractorScript` on each one.
3. `ListingParser` turns each listing card into price, year and km. It reads schema.org
   JSON-LD where it exists and otherwise parses the card text. It doesn't depend on CSS
   class names.
4. `Aggregator` applies every filter on the device too (so results stay correct even if
   Bilbasen ignores a query parameter) and groups the listings by make + model, taken from
   the listing URL.

## Build
```
./gradlew testDebugUnitTest assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/android.yml`)
builds it on every push and uploads it as an artifact.

## Known limitations
- Scraping depends on Bilbasen's page structure and URL parameters. It may need adjusting
  if they change, and automated access may conflict with Bilbasen's terms of use.
- Price ranges are based on the first ~10 result pages. Fuel/gearbox filters are applied to
  those pages only, so narrow searches on those may show fewer cars than Bilbasen has.
- Listings whose fuel type or gearbox can't be read are left out when that filter is on.
