package dk.brugtbil.browser.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * Loads Bilbasen search pages in a (hidden) WebView and extracts the listings.
 *
 * Bilbasen has no public API and its pages sit behind a JavaScript bot check, so a plain
 * HTTP client is turned away. A WebView behaves like the phone's browser: it runs the
 * check once, keeps the cookie, and renders the page, which we then read with
 * [ExtractorScript]. Must be used from the main thread.
 */
class BilbasenClient(private val webView: WebView) {

    @Volatile private var pageLoaded = CompletableDeferred<Unit>()

    init { configure() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = false
        webView.settings.blockNetworkImage = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (pageLoaded.isCompleted) pageLoaded = CompletableDeferred()
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded.complete(Unit)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
        }
    }

    /**
     * Loads [maxPages] result pages for [filters] and returns all distinct listings found.
     * [onProgress] is called after each page with (page, listings so far).
     */
    suspend fun fetch(
        filters: SearchFilters,
        maxPages: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<Listing> {
        val all = LinkedHashMap<String, Listing>()
        for (page in 1..maxPages) {
            val raw = loadPage(BilbasenUrls.search(filters, page))
                ?: if (page == 1) throw BilbasenException("Bilbasen svarede ikke. Tjek din internetforbindelse.") else break
            if (raw.challenge && raw.cards.isEmpty()) {
                if (page == 1) throw BilbasenException("Bilbasen viser en robot-kontrol. Prøv igen om lidt.")
                break
            }
            if (dk.brugtbil.browser.BuildConfig.DEBUG) dumpForDebugging(page, raw)
            val before = all.size
            ListingParser.parse(raw).forEach { all.putIfAbsent(it.id, it) }
            onProgress(page, all.size)
            if (all.size == before) break // no new listings -> past the last page
        }
        return all.values.toList()
    }

    /**
     * Finds a real car photo for a model none of whose downloaded listings had one (the
     * dealer hasn't uploaded photos yet). Opens the model's own search page and takes the
     * first listing there with several photos. Only the page's JSON-LD is needed, so this
     * returns as soon as it appears. Returns null if nothing suitable shows up.
     */
    suspend fun modelPhoto(makeSlug: String, modelSlug: String): String? {
        val url = "${BilbasenUrls.BASE}/brugt/bil/$makeSlug/$modelSlug?includeengroscvr=true&includeleasing=false"
        pageLoaded = CompletableDeferred()
        webView.loadUrl(url)
        val wantedPath = "/brugt/bil/$makeSlug/$modelSlug"
        repeat(60) { // up to ~30 s
            delay(500)
            val page = runCatching { ListingParser.decodePage(evaluate(ExtractorScript.JS)) }.getOrNull()
                ?.takeIf { it.url.substringBefore('?').trimEnd('/').endsWith(wantedPath) }
                ?: return@repeat
            val listings = page.jsonld.flatMap { ListingParser.fromJsonLd(it) }
            if (listings.isNotEmpty()) {
                return listings
                    .filter { Slugs.baseModel(it.modelSlug.orEmpty()) == Slugs.baseModel(modelSlug) }
                    .firstOrNull { Aggregator.hasRealPhoto(it) }?.imageUrl
            }
            if (pageLoaded.isCompleted && !page.challenge && it > 30) return null
        }
        return null
    }

    /**
     * Debug builds only: saves what each page yielded (and page 1's full HTML) to the app's
     * cache dir, so parsing can be checked against Bilbasen's real markup with
     * `adb shell run-as dk.brugtbil.browser ls cache/bilbasen-debug`.
     */
    private suspend fun dumpForDebugging(page: Int, raw: RawPage) {
        runCatching {
            val dir = java.io.File(webView.context.cacheDir, "bilbasen-debug").apply { mkdirs() }
            java.io.File(dir, "page-$page.json").writeText(Json.encodeToString(RawPage.serializer(), raw))
            if (page <= 2) {
                java.io.File(dir, "page-$page.url").writeText(webView.url.orEmpty())
                java.io.File(dir, "page-$page.html").writeText(evaluate("document.documentElement.outerHTML"))
            }
        }
    }

    /** Loads [url] and polls the DOM until listings show up (or the page clearly has none). */
    private suspend fun loadPage(url: String): RawPage? {
        pageLoaded = CompletableDeferred()
        webView.loadUrl(url)
        var last: RawPage? = null
        var stableEmptyPolls = 0
        // Don't wait for onPageFinished: on slow connections a stalled tracker or image can
        // delay it long after the listings are on screen. Poll the DOM from the start
        // (the bot check also reloads the page and results render client-side).
        val wantedPage = pageNumber(url)
        repeat(180) { // up to ~90 s
            delay(500)
            val page = runCatching { ListingParser.decodePage(evaluate(ExtractorScript.JS)) }.getOrNull()
                // Ignore reads from the previous page that is still showing while the next loads.
                ?.takeIf { it.url.isNotEmpty() && pageNumber(it.url) == wantedPage }
            if (page != null) {
                last = page
                if (page.cards.isNotEmpty()) return collectAllCards(page)
                if (!page.challenge && pageLoaded.isCompleted) stableEmptyPolls++ else stableEmptyPolls = 0
                if (stableEmptyPolls >= 12) return page // ~6s of a loaded page with no listings
            }
        }
        return last?.takeIf { pageLoaded.isCompleted || it.cards.isNotEmpty() }
    }

    private fun pageNumber(url: String): Int =
        Regex("""[?&]page=(\d+)""").find(url)?.groupValues?.get(1)?.toInt() ?: 1

    /**
     * Bilbasen draws listing cards gradually as the page is scrolled, while its JSON-LD
     * already names every listing on the page (but without year/km/fuel). Scroll down and
     * keep reading until every listing has a drawn card, or nothing new appears.
     */
    private suspend fun collectAllCards(first: RawPage): RawPage {
        val cards = LinkedHashMap<String, RawCard>()
        fun merge(p: RawPage) = p.cards.forEach { c -> cards[c.href.substringBefore('?')] = c }
        merge(first)
        var latest = first
        var stalls = 0
        for (i in 0 until 60) {
            val expected = ListingParser.expectedListings(latest)
            if (expected > 0 && cards.size >= expected) break
            if (stalls >= 6) break // ~3 s without a new card: assume we have them all
            evaluate(ExtractorScript.SCROLL)
            delay(500)
            val p = runCatching { ListingParser.decodePage(evaluate(ExtractorScript.JS)) }.getOrNull() ?: continue
            if (p.jsonld.isNotEmpty()) latest = p
            val before = cards.size
            merge(p)
            stalls = if (cards.size > before) 0 else stalls + 1
        }
        return latest.copy(cards = cards.values.toList())
    }

    private suspend fun evaluate(js: String): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(js) { result ->
            // The callback receives a JSON-encoded value; our script returns a string.
            val decoded = runCatching { Json.decodeFromString(String.serializer(), result) }.getOrDefault("{}")
            if (cont.isActive) cont.resume(decoded)
        }
    }
}

class BilbasenException(message: String) : Exception(message)
