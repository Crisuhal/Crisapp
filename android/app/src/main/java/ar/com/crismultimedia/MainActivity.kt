package ar.com.crismultimedia

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        web.setBackgroundColor(android.graphics.Color.rgb(10, 10, 15))
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?) =
                request?.url?.let { assetLoader.shouldInterceptRequest(it) }
            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?) =
                url?.let { assetLoader.shouldInterceptRequest(Uri.parse(it)) }
        }
        web.webChromeClient = WebChromeClient()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.addJavascriptInterface(RadioBridge(), "AndroidRadio")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class RadioBridge {
        @JavascriptInterface
        fun startRadio(url: String, title: String, cardId: String) {
            runOnUiThread {
                val uri = Uri.parse(url)
                if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "Enlace de radio inválido", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val intent = Intent(this@MainActivity, RadioService::class.java).apply {
                    action = RadioService.ACTION_PLAY
                    putExtra(RadioService.EXTRA_URL, url)
                    putExtra(RadioService.EXTRA_TITLE, title)
                    putExtra(RadioService.EXTRA_CARD, cardId)
                }
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            }
        }

        @JavascriptInterface
        fun stopRadio() {
            runOnUiThread {
                val intent = Intent(this@MainActivity, RadioService::class.java).setAction(RadioService.ACTION_STOP)
                startService(intent)
            }
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
