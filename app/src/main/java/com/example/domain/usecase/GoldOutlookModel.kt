package com.example.domain.usecase

import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.GoldOutcome
import com.example.domain.model.MacroIndicator
import com.example.domain.model.ModelCalibrationStats
import com.example.domain.model.NewsEventType
import com.example.domain.model.NewsRelease
import com.example.domain.model.OutlookPrediction
import com.example.domain.model.TrendDirection
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp

object GoldOutlookModel {

    /**
     * Deterministic statistical logistic regression model.
     * Computes P(Gold Rises) vs P(Gold Falls) following NFP or CPI.
     */
    fun computePrediction(
        eventType: NewsEventType,
        releaseDate: String,
        releaseTimestamp: Long,
        forecast: Double,
        previous: Double,
        claimsTrend: Double, // % change in 4-week moving average claims (e.g. +3.5%)
        unemploymentChange: Double, // change in UNRATE (e.g. +0.1%)
        wageGrowthYoY: Double, // Average Hourly Earnings YoY (e.g. 3.8%)
        historicalReleases: List<NewsRelease>
    ): OutlookPrediction {
        val sampleSize = historicalReleases.size

        // Feature normalization & z-score
        // For NFP:
        // - Higher forecast vs previous = USD bullish -> Gold bearish (weight -0.85)
        // - Higher claims = USD bearish -> Gold bullish (weight +0.70)
        // - Higher unemployment = USD bearish -> Gold bullish (weight +0.65)
        // - Higher wage growth = sticky inflation -> Fed hawkish -> Gold bearish (weight -0.55)
        val z = if (eventType == NewsEventType.NFP) {
            val forecastDiffRatio = if (previous != 0.0) (forecast - previous) / previous else 0.0
            (-0.85 * forecastDiffRatio) +
                    (0.70 * (claimsTrend / 5.0)) +
                    (0.65 * (unemploymentChange / 0.2)) +
                    (-0.55 * ((wageGrowthYoY - 3.5) / 1.0))
        } else {
            // CPI:
            // - Higher forecast CPI vs previous = Gold bearish (-0.90)
            // - Higher wage growth = Gold bearish (-0.60)
            val cpiDiff = forecast - previous
            (-1.10 * cpiDiff) + (-0.50 * ((wageGrowthYoY - 3.5) / 1.0))
        }

        // Logistic sigmoid function: P(Gold Rises) = 1 / (1 + e^-z)
        val rawProbRise = 1.0 / (1.0 + exp(-z))
        // Bound probabilities realistically between 25% and 75% for macro events
        val probRise = (0.25 + (rawProbRise * 0.50)).coerceIn(0.28, 0.72)
        val probFall = 1.0 - probRise

        // Confidence determination based on deviation from coin-flip 50% and sample size
        val edge = abs(probRise - 0.50)
        val confidence = when {
            edge >= 0.12 && sampleSize >= 20 -> ConfidenceLevel.HIGH
            edge >= 0.05 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        // Generate plain English reasoning
        val reasoning = buildReasoning(
            eventType = eventType,
            forecast = forecast,
            previous = previous,
            claimsTrend = claimsTrend,
            unemploymentChange = unemploymentChange,
            wageGrowth = wageGrowthYoY,
            probRise = probRise
        )

        return OutlookPrediction(
            eventType = eventType,
            releaseDate = releaseDate,
            releaseTimestamp = releaseTimestamp,
            probGoldRise = probRise,
            probGoldFall = probFall,
            confidence = confidence,
            timeWindow = "First 15-60 Minutes",
            sampleSize = sampleSize,
            reasoning = reasoning,
            actualOutcome = GoldOutcome.UNRESOLVED
        )
    }

    private fun buildReasoning(
        eventType: NewsEventType,
        forecast: Double,
        previous: Double,
        claimsTrend: Double,
        unemploymentChange: Double,
        wageGrowth: Double,
        probRise: Double
    ): String {
        val biasWord = if (probRise >= 0.50) "Bullish (Buy Gold)" else "Bearish (Sell Gold)"
        val probPercent = if (probRise >= 0.50) (probRise * 100).toInt() else ((1.0 - probRise) * 100).toInt()

        val factors = mutableListOf<String>()
        if (claimsTrend > 0) {
            factors.add("Weekly jobless claims trended upward (+${String.format("%.1f", claimsTrend)}%), indicating slight cooling in hiring.")
        } else {
            factors.add("Weekly jobless claims remain historically tight, pointing to labor resilience.")
        }

        if (unemploymentChange > 0) {
            factors.add("Unemployment rate ticked higher (+${String.format("%.1f", unemploymentChange)}%), easing pressure on Federal Reserve rate expectations.")
        } else if (unemploymentChange < 0) {
            factors.add("Unemployment rate compressed, reinforcing strong economic capacity.")
        }

        if (wageGrowth > 3.8) {
            factors.add("Average Hourly Earnings remain elevated at ${String.format("%.1f", wageGrowth)}% YoY, sustaining hawkish rate risk.")
        } else {
            factors.add("Wage inflation has moderated to ${String.format("%.1f", wageGrowth)}% YoY, decreasing sticky inflation pressure.")
        }

        return "Statistical Model indicates a $probPercent% bias toward $biasWord.\n" +
                factors.joinToString(" ") +
                " Consensus projects ${if (eventType == NewsEventType.NFP) "${forecast.toInt()}k" else "${String.format("%.1f", forecast)}%"} versus ${if (eventType == NewsEventType.NFP) "${previous.toInt()}k" else "${String.format("%.1f", previous)}%"} previous."
    }

    /**
     * Calculates Brier score and hit rate across historical resolved predictions.
     * Brier score = 1/N * sum((p - o)^2). Perfect = 0.0, Coin flip = 0.25.
     */
    fun calculateCalibration(predictions: List<OutlookPrediction>): ModelCalibrationStats {
        val resolved = predictions.filter { it.actualOutcome != GoldOutcome.UNRESOLVED }
        if (resolved.isEmpty()) {
            return ModelCalibrationStats(
                totalPredictions = predictions.size,
                resolvedPredictions = 0,
                correctPredictions = 0,
                hitRate = 0.0,
                brierScore = 0.0
            )
        }

        var correctCount = 0
        var brierSum = 0.0

        for (pred in resolved) {
            val actualOutcomeBinary = if (pred.actualOutcome == GoldOutcome.RISE) 1.0 else 0.0
            val predictedProb = pred.probGoldRise
            val error = predictedProb - actualOutcomeBinary
            brierSum += (error * error)

            val predictedDirection = if (pred.probGoldRise >= 0.50) GoldOutcome.RISE else GoldOutcome.FALL
            if (predictedDirection == pred.actualOutcome) {
                correctCount++
            }
        }

        val hitRate = (correctCount.toDouble() / resolved.size) * 100.0
        val brierScore = brierSum / resolved.size

        return ModelCalibrationStats(
            totalPredictions = predictions.size,
            resolvedPredictions = resolved.size,
            correctPredictions = correctCount,
            hitRate = hitRate,
            brierScore = brierScore
        )
    }
}
