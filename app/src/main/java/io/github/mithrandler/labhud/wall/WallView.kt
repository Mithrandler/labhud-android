package io.github.mithrandler.labhud.wall

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.Server

/**
 * labhud's own wall page in a WebView. The device token goes in as the `labhud_device` cookie, so
 * the page loads under the VPN address too; labhud still checks Origin on its buttons. Only the
 * paired address is shown here: any other link opens in the browser. With a pinned certificate,
 * the WebView's TLS error is overridden only for exactly that certificate.
 */
@SuppressLint("SetJavaScriptEnabled")
fun wallWebView(context: Context, server: Server, token: String): WebView {
    val base = Uri.parse(server.url)
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setCookie(server.url, "labhud_device=$token; Path=/; SameSite=Strict" + if (base.scheme == "https") "; Secure" else "")
        flush()
    }
    return WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false   // labhud's keep-awake clip
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == base.scheme && u.host == base.host && u.port == base.port) return false
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val cert = error.certificate.x509Certificate
                if (server.fingerprint.isNotEmpty() && cert != null && LabApi.sha256(cert.encoded) == server.fingerprint) handler.proceed()
                else handler.cancel()
            }
        }
        loadUrl(server.url + "/")
    }
}
