package dk.brugtbil.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
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
        webView = WebView(this)
        client = BilbasenClient(webView)

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onFilters = vm::updateFilters,
                    onSearch = { vm.search(client) },
                    onCancel = vm::cancel,
                    onOpenGroup = { g ->
                        val f = state.filters.copy(brand = g.makeSlug, model = g.modelSlug)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BilbasenUrls.search(f))))
                    },
                    // The WebView must be attached to the window for pages to render
                    // reliably, so it lives in the layout at 1dp and fully transparent.
                    hiddenWebView = {
                        AndroidView(factory = { webView }, modifier = Modifier.size(1.dp).alpha(0f))
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
