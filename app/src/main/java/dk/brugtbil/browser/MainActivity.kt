package dk.brugtbil.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.brugtbil.browser.data.BilbasenClient
import dk.brugtbil.browser.data.BilbasenUrls
import dk.brugtbil.browser.ui.MainScreen
import dk.brugtbil.browser.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var webView: WebView
    private lateinit var client: BilbasenClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            @Suppress("ClickableViewAccessibility")
            setOnTouchListener { _, _ -> true } // never react to the user's taps
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        client = BilbasenClient(webView)
        // Debug builds: `adb shell am start -n dk.brugtbil.browser/.MainActivity --ei maxPages 2`
        // limits how many Bilbasen result pages a search loads (handy for quick tests).
        val maxPages = intent.getIntExtra("maxPages", 0)
            .takeIf { BuildConfig.DEBUG && it > 0 } ?: MainViewModel.MAX_PAGES

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onFilters = vm::updateFilters,
                    onSearch = { vm.search(client, maxPages) },
                    onCancel = vm::cancel,
                    onOpenGroup = { g ->
                        val f = state.filters.copy(brand = g.makeSlug, model = g.modelSlug)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BilbasenUrls.search(f))))
                    },
                    // The WebView needs a real, phone-sized viewport for Bilbasen to draw
                    // its listing cards, so it fills the screen *behind* the app's own
                    // (opaque) content and ignores touches. Between searches it is made
                    // invisible so it isn't redrawn behind every frame of the result list
                    // (photo look-ups after a search only read the page's data, not its cards).
                    hiddenWebView = {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier.fillMaxSize(),
                            update = { it.visibility = if (state.loading) View.VISIBLE else View.INVISIBLE },
                        )
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
