package com.example.domain.model

enum class SyntheticFamily(val displayName: String, val description: String) {
    FX_VOL("FX Volatility", "Constant volatility at fixed annual percentage (FX Vol 10-100)"),
    SFX_VOL("SFX Volatility", "Simulated spike events every 30 mins mimicking high-impact news"),
    PAIN_X("PainX", "Algorithmic upward march with periodic sharp drop ticks (400-1200)"),
    CRASH_BOOM("Crash & Boom", "Simulated market crashes and explosions at regular intervals"),
    STEP_JUMP("Step & Jump", "Discrete equal step shifts and sudden jump volatility"),
    DERIV_VOL("Volatility (V-Series)", "Standard continuous algorithmic volatility indices (V10-V100)")
}

data class SyntheticIndex(
    val symbol: String,
    val name: String,
    val family: SyntheticFamily,
    val description: String,
    val typicalPipOrPoint: String = "0.01 / 1.0",
    val isWlTradeSyntX: Boolean = true
)

object SyntheticIndexCatalog {
    val ALL_SYNTHETICS: List<SyntheticIndex> = listOf(
        // Weltrade SyntX FX Vol Family
        SyntheticIndex("FX Vol 10", "FX Vol 10 (SyntX)", SyntheticFamily.FX_VOL, "Calm 10% annual volatility simulated market"),
        SyntheticIndex("FX Vol 20", "FX Vol 20 (SyntX)", SyntheticFamily.FX_VOL, "Smooth 20% volatility, ideal for trend scalping"),
        SyntheticIndex("FX Vol 40", "FX Vol 40 (SyntX)", SyntheticFamily.FX_VOL, "Dynamic 40% volatility, institutional liquidity flow"),
        SyntheticIndex("FX Vol 60", "FX Vol 60 (SyntX)", SyntheticFamily.FX_VOL, "High 60% volatility with rapid swings"),
        SyntheticIndex("FX Vol 80", "FX Vol 80 (SyntX)", SyntheticFamily.FX_VOL, "Intense 80% annual volatility for breakout traders"),
        SyntheticIndex("FX Vol 100", "FX Vol 100 (SyntX)", SyntheticFamily.FX_VOL, "Maximum 100% continuous simulated volatility"),

        // Weltrade SyntX SFX Vol (Spike Indices)
        SyntheticIndex("SFX Vol 20", "SFX Vol 20 (Spike)", SyntheticFamily.SFX_VOL, "20% volatility with scheduled simulated news spikes"),
        SyntheticIndex("SFX Vol 40", "SFX Vol 40 (Spike)", SyntheticFamily.SFX_VOL, "40% volatility with sudden algorithmic gaps"),
        SyntheticIndex("SFX Vol 80", "SFX Vol 80 (Spike)", SyntheticFamily.SFX_VOL, "80% volatility with aggressive candle spikes"),
        SyntheticIndex("SFX Vol 100", "SFX Vol 100 (Spike)", SyntheticFamily.SFX_VOL, "Extreme spike volatility every ~30 mins"),

        // Weltrade SyntX PainX Family
        SyntheticIndex("PainX 400", "PainX 400 (SyntX)", SyntheticFamily.PAIN_X, "Upward drift with sharp drop every ~400 ticks"),
        SyntheticIndex("PainX 600", "PainX 600 (SyntX)", SyntheticFamily.PAIN_X, "Upward trend with drop interval ~600 ticks"),
        SyntheticIndex("PainX 800", "PainX 800 (SyntX)", SyntheticFamily.PAIN_X, "Consistent rally with drop interval ~800 ticks"),
        SyntheticIndex("PainX 999", "PainX 999 (SyntX)", SyntheticFamily.PAIN_X, "Longer buy runs punctuated by deep drop ticks"),
        SyntheticIndex("PainX 1200", "PainX 1200 (SyntX)", SyntheticFamily.PAIN_X, "High reward-to-risk buy trend with rare drops"),

        // Crash & Boom Family
        SyntheticIndex("Crash 300", "Crash 300 Index", SyntheticFamily.CRASH_BOOM, "Fast crash drop on average every 300 ticks"),
        SyntheticIndex("Crash 500", "Crash 500 Index", SyntheticFamily.CRASH_BOOM, "Sudden drop every ~500 ticks; steady climb between"),
        SyntheticIndex("Crash 1000", "Crash 1000 Index", SyntheticFamily.CRASH_BOOM, "Longer upward grind punctuated by 1000-tick crash"),
        SyntheticIndex("Boom 300", "Boom 300 Index", SyntheticFamily.CRASH_BOOM, "Sudden upward spike on average every 300 ticks"),
        SyntheticIndex("Boom 500", "Boom 500 Index", SyntheticFamily.CRASH_BOOM, "Steady decline with sudden upward spike every ~500 ticks"),
        SyntheticIndex("Boom 1000", "Boom 1000 Index", SyntheticFamily.CRASH_BOOM, "Deep sell trend broken by powerful 1000-tick explosion"),

        // Step & Jump
        SyntheticIndex("Step Index", "Step Index (100)", SyntheticFamily.STEP_JUMP, "Equal discrete steps up or down with 0.1 size"),
        SyntheticIndex("Jump 10", "Jump 10 Index", SyntheticFamily.STEP_JUMP, "10% volatility with regular jump spikes"),
        SyntheticIndex("Jump 25", "Jump 25 Index", SyntheticFamily.STEP_JUMP, "25% volatility with jump price shifts"),
        SyntheticIndex("Jump 50", "Jump 50 Index", SyntheticFamily.STEP_JUMP, "50% volatility with sudden tick gaps"),
        SyntheticIndex("Jump 75", "Jump 75 Index", SyntheticFamily.STEP_JUMP, "75% high frequency jump volatility"),
        SyntheticIndex("Jump 100", "Jump 100 Index", SyntheticFamily.STEP_JUMP, "100% extreme jump price jumps"),

        // Volatility V-Series
        SyntheticIndex("V75", "Volatility 75 (1s)", SyntheticFamily.DERIV_VOL, "Continuous 75% market volatility; favorite among traders"),
        SyntheticIndex("V100", "Volatility 100 (1s)", SyntheticFamily.DERIV_VOL, "Continuous 100% heavy volatility"),
        SyntheticIndex("V50", "Volatility 50 (1s)", SyntheticFamily.DERIV_VOL, "Medium 50% continuous algorithmic volatility"),
        SyntheticIndex("V25", "Volatility 25 (1s)", SyntheticFamily.DERIV_VOL, "Gentle 25% continuous volatility"),
        SyntheticIndex("V10", "Volatility 10 (1s)", SyntheticFamily.DERIV_VOL, "Calm 10% annual volatility")
    )

    fun isSyntheticIndex(symbol: String): Boolean {
        val s = symbol.trim().uppercase()
        return s.startsWith("FX VOL") ||
                s.startsWith("FXVOL") ||
                s.startsWith("SFX VOL") ||
                s.startsWith("SFXVOL") ||
                s.startsWith("PAINX") ||
                s.startsWith("CRASH") ||
                s.startsWith("BOOM") ||
                s.startsWith("STEP") ||
                s.startsWith("JUMP") ||
                s.startsWith("V75") ||
                s.startsWith("V100") ||
                s.startsWith("V50") ||
                s.startsWith("V25") ||
                s.startsWith("V10") ||
                s.startsWith("VOLATILITY") ||
                s.startsWith("SYNTX") ||
                s.startsWith("RANGE BREAK")
    }

    fun getIndexInfo(symbol: String): SyntheticIndex? {
        val normalized = symbol.trim().replace(" ", "").uppercase()
        return ALL_SYNTHETICS.firstOrNull { it.symbol.replace(" ", "").uppercase() == normalized }
    }
}
