package com.example.presentation.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChartsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.rgb(10, 15, 25))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            val chartWebView = this
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val detail = "${message.message()} -- ${message.sourceId()}:${message.lineNumber()}"
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> {
                            Log.e("TradingViewWebView", detail)
                            chartWebView.evaluateJavascript(
                                "window.showNativeError(${JSONObject.quote(detail)})",
                                null
                            )
                        }
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("TradingViewWebView", detail)
                        else -> Log.d("TradingViewWebView", detail)
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    val detail = "WebView error ${error.errorCode}: ${error.description} (${request.url.host ?: request.url})"
                    Log.e("TradingViewWebView", detail)
                    view.evaluateJavascript(
                        "window.showNativeError(${JSONObject.quote(detail)})",
                        null
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    if (errorResponse.statusCode >= 400) {
                        val detail = "HTTP ${errorResponse.statusCode} ${errorResponse.reasonPhrase} from ${request.url.host ?: request.url}"
                        Log.e("TradingViewWebView", detail)
                        view.evaluateJavascript(
                            "window.showNativeError(${JSONObject.quote(detail)})",
                            null
                        )
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: android.webkit.SslErrorHandler,
                    error: android.net.http.SslError
                ) {
                    handler.cancel()
                    val detail = "SSL error ${error.primaryError} from ${error.url}"
                    Log.e("TradingViewWebView", detail)
                    view.evaluateJavascript(
                        "window.showNativeError(${JSONObject.quote(detail)})",
                        null
                    )
                }

                override fun onPageFinished(view: WebView, url: String) {
                    Log.d("TradingViewWebView", "Page finished: $url")
                }
            }
            val html = context.assets.open("tradingview_chart.html")
                .bufferedReader()
                .use { it.readText() }
            loadDataWithBaseURL(
                "https://localhost/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
