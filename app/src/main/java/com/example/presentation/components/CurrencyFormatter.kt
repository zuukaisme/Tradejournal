package com.example.presentation.components

import com.example.domain.model.CurrencyCode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {

    fun format(
        amount: Double,
        currency: CurrencyCode = CurrencyCode.USD,
        includeSign: Boolean = true
    ): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#,##0.00", symbols)
        val formattedNumber = df.format(kotlin.math.abs(amount))

        val symbol = currency.symbol
        val sign = when {
            amount > 0 && includeSign -> "+"
            amount < 0 -> "-"
            else -> ""
        }

        return "$sign$symbol$formattedNumber"
    }

    fun formatCompact(
        amount: Double,
        currency: CurrencyCode = CurrencyCode.USD
    ): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        val symbol = currency.symbol

        val text = when {
            abs >= 1_000_000 -> String.format(Locale.US, "%.1fM", abs / 1_000_000)
            abs >= 1_000 -> String.format(Locale.US, "%.1fk", abs / 1_000)
            else -> String.format(Locale.US, "%.0f", abs)
        }
        return "$sign$symbol$text"
    }
}
