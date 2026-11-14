package com.example.domain.usecase

data class InstrumentRiskSpec(
    val symbol: String,
    val category: String,
    val defaultValuePerPointPerLot: Double,
    val minLot: Double = 0.01,
    val pointHint: String
)

data class PositionSizeResult(
    val riskAmount: Double,
    val rawLotSize: Double,
    val roundedLotSize: Double,
    val oneRReward: Double,
    val twoRReward: Double,
    val threeRReward: Double
)

object RiskCalculator {

    val presets: List<InstrumentRiskSpec> = listOf(
        InstrumentRiskSpec("FX Vol 20", "WLTrade SyntX", 1.0, 0.01, "1.00 = 1 index point per 1.0 lot"),
        InstrumentRiskSpec("FX Vol 40", "WLTrade SyntX", 1.0, 0.01, "1.00 = 1 index point per 1.0 lot"),
        InstrumentRiskSpec("PainX 400", "WLTrade SyntX", 1.0, 0.01, "1.00 = $1 per point at 1.0 lot"),
        InstrumentRiskSpec("PainX 999", "WLTrade SyntX", 1.0, 0.01, "1.00 = $1 per point at 1.0 lot"),
        InstrumentRiskSpec("SFX Vol 40", "WLTrade SyntX", 1.0, 0.01, "1.00 = $1 per point at 1.0 lot"),
        InstrumentRiskSpec("Boom 500", "WLTrade SyntX", 1.0, 0.20, "Min 0.20 lots • $1 per point at 1.0 lot"),
        InstrumentRiskSpec("Crash 500", "WLTrade SyntX", 1.0, 0.20, "Min 0.20 lots • $1 per point at 1.0 lot"),
        InstrumentRiskSpec("Step Index", "WLTrade SyntX", 10.0, 0.10, "0.10 step = $1 per point at 0.10 lot"),
        InstrumentRiskSpec("V75", "WLTrade SyntX", 1.0, 0.001, "High-volatility index • adjust per broker spec"),
        InstrumentRiskSpec("XAUUSD", "Metals", 1.0, 0.01, "Gold: $1.00 per 0.01 move (100 oz contract = $1/pt at 0.01 lot)"),
        InstrumentRiskSpec("EURUSD", "Forex", 10.0, 0.01, "Standard FX: $10 per pip (1.0 lot)"),
        InstrumentRiskSpec("US30", "Indices", 1.0, 0.01, "$1 per index point at 1.0 lot")
    )

    fun calculate(
        accountBalance: Double,
        riskPercent: Double,
        stopDistancePoints: Double,
        valuePerPointPerLot: Double
    ): PositionSizeResult {
        val safeBalance = accountBalance.coerceAtLeast(0.0)
        val safeRiskPct = riskPercent.coerceIn(0.0, 100.0)
        val riskAmount = safeBalance * (safeRiskPct / 100.0)

        if (stopDistancePoints <= 0.0 || valuePerPointPerLot <= 0.0) {
            return PositionSizeResult(
                riskAmount = riskAmount,
                rawLotSize = 0.0,
                roundedLotSize = 0.0,
                oneRReward = riskAmount,
                twoRReward = riskAmount * 2.0,
                threeRReward = riskAmount * 3.0
            )
        }

        val rawLots = riskAmount / (stopDistancePoints * valuePerPointPerLot)
        val roundedLots = kotlin.math.floor(rawLots * 100.0) / 100.0
        return PositionSizeResult(
            riskAmount = riskAmount,
            rawLotSize = rawLots,
            roundedLotSize = roundedLots.coerceAtLeast(0.0),
            oneRReward = riskAmount,
            twoRReward = riskAmount * 2.0,
            threeRReward = riskAmount * 3.0
        )
    }
}
