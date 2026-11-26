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
     * Debug builds only: saves what each page yielded (and page 1's full HTML) to the app's
     * cache dir, so parsing can be checked against Bilbasen's real markup with
     * `adb shell run-as dk.brugtbil.browser ls cache/bilbasen-debug`.
     */
    private suspend fun dumpForDebugging(page: Int, raw: RawPage) {
        runCatching {
            val dir = java.io.File(webView.context.cacheDir, "bilbasen-debug").apply { mkdirs() }
            java.io.File(dir, "page-$page.json").writeText(Json.encodeToString(RawPage.serializer(), raw))
            if (page == 1) {
                java.io.File(dir, "page-1.url").writeText(webView.url.orEmpty())
                java.io.File(dir, "page-1.html").writeText(evaluate("document.documentElement.outerHTML"))
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
        repeat(120) { // up to ~60 s
            delay(500)
            val page = runCatching { ListingParser.decodePage(evaluate(ExtractorScript.JS)) }.getOrNull()
            if (page != null) {
                last = page
                if (page.cards.isNotEmpty()) {
                    // Give lazy-rendered cards a moment, then return.
                    delay(700)
                    return runCatching { ListingParser.decodePage(evaluate(ExtractorScript.JS)) }
                        .getOrDefault(page)
                }
                if (!page.challenge && pageLoaded.isCompleted) stableEmptyPolls++ else stableEmptyPolls = 0
                if (stableEmptyPolls >= 12) return page // ~6s of a loaded page with no listings
            }
        }
        return last?.takeIf { pageLoaded.isCompleted || it.cards.isNotEmpty() }
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
