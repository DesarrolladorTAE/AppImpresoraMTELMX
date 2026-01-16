package com.tae.printbridge

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Levanta el servicio (como ya lo hacías)
        val i = Intent(this, PrintBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

        // 2) WebView para abrir tu POS (en internet) dentro de la app
        val webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // 3) Bridge JS -> Android
        webView.addJavascriptInterface(
            AndroidPrintBridge(this),
            "AndroidPrintBridge"
        )

        // 4) Carga tu POS (AJUSTA ESTA URL a la pantalla exacta)
        webView.loadUrl("https://mitiendaenlineamx.com.mx/")

        setContentView(webView)

        // ❌ Ya NO hacemos finish()
    }

    // Opcional: botón atrás navega en web
    override fun onBackPressed() {
        val root = window.decorView.rootView
        val webView = root as? WebView
        if (webView != null && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
