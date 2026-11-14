package com.example.domain.usecase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object Mql5SyntaxHighlighter {

    private val KEYWORDS = setOf(
        "input", "group", "int", "double", "string", "bool", "datetime",
        "ulong", "long", "short", "char", "uchar", "float", "void",
        "return", "if", "else", "for", "while", "do", "break", "continue",
        "switch", "case", "default", "class", "struct", "public", "private",
        "protected", "const", "static", "virtual", "override", "true", "false",
        "NULL", "EMPTY_VALUE", "INVALID_HANDLE", "INIT_SUCCEEDED", "INIT_FAILED",
        "MODE_EMA", "MODE_SMA", "PRICE_CLOSE", "PRICE_OPEN", "PRICE_HIGH", "PRICE_LOW",
        "SYMBOL_ASK", "SYMBOL_BID", "SYMBOL_POINT", "DRAW_ARROW", "INDICATOR_DATA"
    )

    private val PREPROCESSORS = setOf(
        "#property", "#include", "#define", "#import", "#ifdef", "#ifndef", "#endif"
    )

    private val BUILTINS = setOf(
        "OnInit", "OnDeinit", "OnTick", "OnCalculate", "OnTimer", "OnTrade", "OnChartEvent",
        "CTrade", "CPositionInfo", "COrderInfo", "CSymbolInfo", "MqlTick", "MqlRates",
        "MqlTradeRequest", "MqlTradeResult", "Print", "Alert", "iMA", "iRSI", "iMACD", "iATR",
        "CopyBuffer", "ArraySetAsSeries", "SymbolInfoDouble", "SymbolInfoInteger",
        "PositionsTotal", "OrdersTotal", "NormalizeDouble", "StringFormat",
        "IndicatorRelease", "SetIndexBuffer", "PlotIndexSetInteger", "iTime"
    )

    fun highlight(
        code: String,
        isDark: Boolean = true,
        searchQuery: String = ""
    ): AnnotatedString {
        val keywordColor = if (isDark) Color(0xFFC678DD) else Color(0xFF7C3AED)
        val preprocessorColor = if (isDark) Color(0xFFE5C07B) else Color(0xFFD97706)
        val builtinColor = if (isDark) Color(0xFF61AFEF) else Color(0xFF2563EB)
        val stringColor = if (isDark) Color(0xFF98C379) else Color(0xFF16A34A)
        val commentColor = if (isDark) Color(0xFF7F848E) else Color(0xFF6B7280)
        val numberColor = if (isDark) Color(0xFFE06C75) else Color(0xFFDC2626)
        val defaultColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)

        val builder = AnnotatedString.Builder()
        builder.append(code)

        // 1. Default style
        builder.addStyle(
            SpanStyle(color = defaultColor),
            0,
            code.length
        )

        // 2. Multi-line and single-line comments
        val commentRegex = Regex("(//.*$)|(/\\*[\\s\\S]*?\\*/)", RegexOption.MULTILINE)
        commentRegex.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. String literals ("...")
        val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
        stringRegex.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = stringColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 4. Words (preprocessors, keywords, builtins, numbers)
        val wordRegex = Regex("(#?[a-zA-Z_][a-zA-Z0-9_]*)|(\\b\\d+(\\.\\d+)?f?\\b)")
        wordRegex.findAll(code).forEach { match ->
            val word = match.value
            val start = match.range.first
            val end = match.range.last + 1

            // Skip if this word is inside a comment or string
            val isInsideCommentOrString = commentRegex.findAll(code).any { it.range.contains(start) } ||
                    stringRegex.findAll(code).any { it.range.contains(start) }

            if (!isInsideCommentOrString) {
                when {
                    PREPROCESSORS.contains(word) -> {
                        builder.addStyle(SpanStyle(color = preprocessorColor, fontWeight = FontWeight.SemiBold), start, end)
                    }
                    KEYWORDS.contains(word) -> {
                        builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), start, end)
                    }
                    BUILTINS.contains(word) -> {
                        builder.addStyle(SpanStyle(color = builtinColor, fontWeight = FontWeight.SemiBold), start, end)
                    }
                    word.firstOrNull()?.isDigit() == true -> {
                        builder.addStyle(SpanStyle(color = numberColor), start, end)
                    }
                }
            }
        }

        // 5. Highlight search matches
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            var searchIndex = 0
            while (searchIndex < code.length) {
                val found = code.indexOf(searchQuery, searchIndex, ignoreCase = true)
                if (found == -1) break
                builder.addStyle(
                    SpanStyle(
                        background = Color(0xFFFFD54F),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    found,
                    found + searchQuery.length
                )
                searchIndex = found + searchQuery.length
            }
        }

        return builder.toAnnotatedString()
    }
}
