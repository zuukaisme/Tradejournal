package com.example.presentation.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.LocalIsDarkTheme

private data class ChartInstrument(
    val name: String,
    val symbol: String
)

private data class ChartTimeframe(
    val label: String,
    val value: String
)

private enum class ChartEngineMode(val label: String) {
    DIRECT("Direct"),
    WIDGET("TV.js")
}

private val marketCatalog: Map<String, List<ChartInstrument>> = linkedMapOf(
    "Forex" to listOf(
        ChartInstrument("EUR/USD", "FX:EURUSD"),
        ChartInstrument("GBP/USD", "FX:GBPUSD"),
        ChartInstrument("USD/JPY", "FX:USDJPY"),
        ChartInstrument("AUD/USD", "FX:AUDUSD"),
        ChartInstrument("USD/CAD", "FX:USDCAD"),
        ChartInstrument("USD/CHF", "FX:USDCHF"),
        ChartInstrument("NZD/USD", "FX:NZDUSD")
    ),
    "Deriv Synthetics" to listOf(
        ChartInstrument("Volatility 100 (1s)", "DERIV:VOLATILITY_100_1S_INDEX"),
        ChartInstrument("Volatility 100", "DERIV:VOLATILITY_100_INDEX"),
        ChartInstrument("Volatility 75", "DERIV:VOLATILITY_75_INDEX"),
        ChartInstrument("Volatility 50", "DERIV:VOLATILITY_50_INDEX"),
        ChartInstrument("Volatility 25", "DERIV:VOLATILITY_25_INDEX"),
        ChartInstrument("Volatility 10 (1s)", "DERIV:VOLATILITY_10_1S_INDEX"),
        ChartInstrument("Volatility 10", "DERIV:VOLATILITY_10_INDEX"),
        ChartInstrument("Boom 1000", "DERIV:BOOM_1000_INDEX"),
        ChartInstrument("Boom 500", "DERIV:BOOM_500_INDEX"),
        ChartInstrument("Crash 1000", "DERIV:CRASH_1000_INDEX"),
        ChartInstrument("Crash 500", "DERIV:CRASH_500_INDEX"),
        ChartInstrument("Jump 100", "DERIV:JUMP_100_INDEX"),
        ChartInstrument("Step Index", "DERIV:STEP_INDEX")
    ),
    "Metals" to listOf(
        ChartInstrument("Gold (XAU/USD)", "OANDA:XAUUSD"),
        ChartInstrument("Silver (XAG/USD)", "OANDA:XAGUSD")
    ),
    "Oil" to listOf(
        ChartInstrument("WTI Crude Oil", "TVC:USOIL"),
        ChartInstrument("Brent Crude Oil", "TVC:UKOIL")
    )
)

private val chartTimeframes = listOf(
    ChartTimeframe("1m", "1"),
    ChartTimeframe("5m", "5"),
    ChartTimeframe("15m", "15"),
    ChartTimeframe("1H", "60"),
    ChartTimeframe("4H", "240"),
    ChartTimeframe("1D", "D"),
    ChartTimeframe("1W", "W")
)

private fun buildChartUrl(
    symbol: String,
    interval: String,
    isDark: Boolean,
    mode: ChartEngineMode,
    toolbarBgHex: String
): String {
    val theme = if (isDark) "dark" else "light"
    val encodedSymbol = Uri.encode(symbol)
    return when (mode) {
        ChartEngineMode.DIRECT -> {
            "https://s.tradingview.com/widgetembed/?" +
                "frameElementId=tradingview_chart" +
                "&symbol=$encodedSymbol" +
                "&interval=${Uri.encode(interval)}" +
                "&hidesidetoolbar=1" +
                "&hidetoptoolbar=0" +
                "&symboledit=1" +
                "&saveimage=0" +
                "&toolbarbg=$toolbarBgHex" +
                "&studies=%5B%5D" +
                "&theme=$theme" +
                "&style=1" +
                "&timezone=Etc%2FUTC" +
                "&withdateranges=1" +
                "&hidevolume=${if (symbol.startsWith("DERIV:")) "1" else "0"}" +
                "&locale=en"
        }
        ChartEngineMode.WIDGET -> {
            "file:///android_asset/tradingview_chart.html" +
                "?symbol=$encodedSymbol&interval=${Uri.encode(interval)}&theme=$theme"
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChartsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val bgColor = MaterialTheme.colorScheme.background
    val bgArgb = bgColor.toArgb()
    val toolbarHex = remember(bgArgb) {
        String.format("%06X", 0xFFFFFF and bgArgb)
    }

    var activeCategory by remember { mutableStateOf("Forex") }
    var activeInstrument by remember { mutableStateOf(marketCatalog["Forex"]!!.first()) }
    var activeTimeframe by remember { mutableStateOf(chartTimeframes[2]) } // 15m
    var engineMode by remember { mutableStateOf(ChartEngineMode.DIRECT) }
    var symbolMenuExpanded by remember { mutableStateOf(false) }

    var loadProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }
    var mainFrameError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    val targetUrl = remember(activeInstrument.symbol, activeTimeframe.value, isDark, engineMode, toolbarHex, reloadNonce) {
        buildChartUrl(
            symbol = activeInstrument.symbol,
            interval = activeTimeframe.value,
            isDark = isDark,
            mode = engineMode,
            toolbarBgHex = toolbarHex
        )
    }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(bgArgb)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                // Remove WebView "; wv" token so TradingView CDN serves full charts
                userAgentString = userAgentString
                    .replace("; wv)", ")")
                    .replace("Version/4.0 ", "")
            }

            val chartWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(chartWebView, true)
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onChartError(message: String) {
                        post {
                            mainFrameError = message
                            isPageLoading = false
                        }
                    }
                },
                "ChartBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                    if (newProgress >= 100) {
                        isPageLoading = false
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "file" || uri.scheme == "about" || uri.scheme == "blob") {
                        return false
                    }
                    val host = uri.host.orEmpty()
                    val path = uri.path.orEmpty()
                    val isChartEmbedHost = host.endsWith("tradingview.com") ||
                        host.endsWith("tradingview-widget.com")
                    val isWidgetFrame = path.contains("widgetembed") || path.contains("embed-widget")

                    // Allow all automatic/iframe chart loads; only open external links tapped by user
                    if (request.isForMainFrame == false || isWidgetFrame || !request.hasGesture()) {
                        return false
                    }
                    if (isChartEmbedHost && path.startsWith("/chart")) {
                        return false
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    return true
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    isPageLoading = true
                    mainFrameError = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isPageLoading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val msg = error?.description?.toString()?.ifBlank { "Unable to load chart" }
                            ?: "Unable to load chart"
                        Log.e("TradingViewWebView", "Main frame error ${error?.errorCode}: $msg")
                        mainFrameError = "$msg. Check your internet connection or try switching mode."
                        isPageLoading = false
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Native Control Header (Material 3, synced to active app theme)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Market category chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(marketCatalog.keys.toList()) { category ->
                    val selected = category == activeCategory
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeCategory = category
                            activeInstrument = marketCatalog[category]!!.first()
                            mainFrameError = null
                        },
                        label = {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primary)
                    )
                }
            }

            // Symbol picker + Reload + Browser fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { symbolMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("chart_symbol_dropdown")
                    ) {
                        Text(
                            text = "${activeInstrument.name} (${activeInstrument.symbol})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select instrument")
                    }

                    DropdownMenu(
                        expanded = symbolMenuExpanded,
                        onDismissRequest = { symbolMenuExpanded = false }
                    ) {
                        marketCatalog[activeCategory].orEmpty().forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            item.symbol,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    activeInstrument = item
                                    symbolMenuExpanded = false
                                    mainFrameError = null
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        mainFrameError = null
                        isPageLoading = true
                        reloadNonce++
                        webView.loadUrl(targetUrl)
                    },
                    modifier = Modifier.testTag("chart_reload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload chart",
                        tint = primary
                    )
                }

                IconButton(
                    onClick = {
                        val tvUri = Uri.parse(
                            "https://www.tradingview.com/chart/?symbol=${Uri.encode(activeInstrument.symbol)}"
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, tvUri)) }
                    },
                    modifier = Modifier.testTag("chart_open_browser_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in browser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Timeframe chips + Engine Mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(chartTimeframes) { tf ->
                        val selected = tf == activeTimeframe
                        FilterChip(
                            selected = selected,
                            onClick = {
                                activeTimeframe = tf
                                mainFrameError = null
                            },
                            label = {
                                Text(
                                    text = tf.label,
                                    fontSize = 11.sp,
                                    color = if (selected) onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                FilterChip(
                    selected = engineMode == ChartEngineMode.WIDGET,
                    onClick = {
                        engineMode = if (engineMode == ChartEngineMode.DIRECT) {
                            ChartEngineMode.WIDGET
                        } else {
                            ChartEngineMode.DIRECT
                        }
                        mainFrameError = null
                    },
                    label = { Text(engineMode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("chart_engine_toggle")
                )
            }
        }

        if (isPageLoading) {
            LinearProgressIndicator(
                progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Full-height Chart Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bgColor)
        ) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    view.setBackgroundColor(bgArgb)
                    val tagUrl = view.tag as? String
                    if (tagUrl != targetUrl) {
                        view.tag = targetUrl
                        isPageLoading = true
                        mainFrameError = null
                        view.loadUrl(targetUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tradingview_webview")
            )

            mainFrameError?.let { errorMessage ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Chart Load Issue",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    engineMode = if (engineMode == ChartEngineMode.DIRECT) {
                                        ChartEngineMode.WIDGET
                                    } else {
                                        ChartEngineMode.DIRECT
                                    }
                                    mainFrameError = null
                                }
                            ) {
                                Text("Switch Mode", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    mainFrameError = null
                                    reloadNonce++
                                    webView.loadUrl(targetUrl)
                                }
                            ) {
                                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
