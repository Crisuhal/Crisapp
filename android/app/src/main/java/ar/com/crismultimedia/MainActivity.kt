package ar.com.crismultimedia

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.net.URI

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(android.graphics.Color.rgb(10, 10, 15))
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (url.startsWith("file:///android_asset/")) return false
                return false
            }
        }
        web.webChromeClient = WebChromeClient()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.addJavascriptInterface(RadioBridge(), "AndroidRadio")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class RadioBridge {
        @JavascriptInterface
        fun startRadio(url: String, title: String, cardId: String) {
            runOnUiThread {
                val uri = try { URI(url) } catch (_: Exception) { null }
                if (uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host == null) {
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
