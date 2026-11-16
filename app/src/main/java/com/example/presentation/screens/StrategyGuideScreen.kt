package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.LiquidGlassCard
import kotlinx.coroutines.delay

private const val EXAMPLE_STRATEGY_CODE = """from backtest import Strategy, Signal


class ThreeBullishCandlesStrategy(Strategy):
    '''Placeholder rule: three bullish candles in a row, then buy.
    Stop is one 14-candle average range below close, target is 2R.
    '''

    def on_candle(self, i, candles):
        if i < 13:
            return None
        last3 = candles[i - 2:i + 1]
        if not all(c.close > c.open for c in last3):
            return None
        window = candles[i - 13:i + 1]
        risk = sum(c.high - c.low for c in window) / len(window)
        if risk <= 0:
            return None
        price = candles[i].close
        return Signal("buy", sl=price - risk, tp=price + 2.0 * risk)
"""

private const val AI_STRATEGY_PROMPT = """Write a single self-contained Python 3.11 trading strategy file for my embedded Chaquopy backtester (`backtest.py`).

PACKAGES & ENVIRONMENT:
- Allowed packages: Python 3.11 standard library ONLY (`math`, `statistics`, `collections`, `datetime`, `dataclasses`, `typing`).
- FORBIDDEN: Do NOT import `pandas`, `numpy`, `scipy`, `talib`, or any external pip package (they are not bundled in the APK).

OBJECTS & RETURN TYPE (`from backtest import Strategy, Signal`):
- Input `Candle` object fields: `.time` (str), `.open` (float), `.high` (float), `.low` (float), `.close` (float), `.volume` (float).
- Returned `Signal` object: `Signal(side, sl, tp)` where `side` is `"buy"` or `"sell"` (lowercase), `sl` is stop-loss price (float), and `tp` is take-profit price (float).
- `on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]` MUST return a `Signal(...)` object or `None`.
- Do not implement `manage_trade`; the current engine does not call it.

ENGINE CONTRACT (11 RULES):
1. Start with `from backtest import Strategy, Signal` and subclass `Strategy`.
2. Implement `def on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]:` only.
3. Only read `candles[0..i]`. NEVER look ahead at `candles[i + 1:]`.
4. Assume default `warmup = 50` candles before signals are taken; also guard custom lookback indices and zero risk/ATR before dividing.
5. MT5 time parsing: Candle times may use MT5 dotted format `YYYY.MM.DD HH:MM:SS` (e.g. `2024.03.05 10:15:00`) as well as dashed dates. Parse both and define `SERVER_GMT_OFFSET` near the top (default `2`, or `0` for GMT).
6. Return `Signal(side, sl, tp)` or `None` (`side` must be `"buy"` or `"sell"`).
7. Entry happens at `candles[i + 1].open`. Ensure for `"buy"`: `sl < entry < tp`, and for `"sell"`: `tp < entry < sl`. (If both SL and TP hit in one candle, SL hits first.)
8. Skipped candles while trade open: the engine only calls `on_candle` when no trade is open. Keep `last_processed_index` and catch up all missed candles in chronological order on each callback before evaluating signals.
9. Performance without changing logic: update EMAs, sessions, and ranges incrementally per candle; never rescan thousands of bars on every callback. Preserve exact strategy rules and lookbacks.
10. Output ONLY valid raw Python code with docstrings and comments explaining every entry, stop loss, and take profit rule.

My strategy idea:
[DESCRIBE YOUR ENTRY RULES, STOP LOSS, AND TAKE PROFIT HERE]"""

@Composable
fun StrategyGuideScreen(
    onOpenStrategies: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedExample by remember { mutableStateOf(false) }
    var copiedPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(copiedExample) {
        if (copiedExample) {
            delay(2000)
            copiedExample = false
        }
    }

    LaunchedEffect(copiedPrompt) {
        if (copiedPrompt) {
            delay(2000)
            copiedPrompt = false
        }
    }

    val contractRules = remember {
        listOf(
            "Subclass `Strategy` (`class MyStrategy(Strategy):`). `Strategy`, `Signal`, `Candle`, `Trade`, `List`, and `Optional` are pre-injected.",
            "Implement `def on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]:` (do not implement `manage_trade`; the engine does not call it).",
            "Strict no-lookahead: read only `candles[0..i]` — never inspect `candles[i + 1:]`.",
            "Warmup: the engine skips the first 50 candles (`i >= 50`), and opens one trade at a time.",
            "MT5 timestamps: `Candle.time` uses `YYYY.MM.DD HH:MM:SS` (e.g. `2024.03.05 10:15:00`) as well as dashed dates. Parse both and expose `SERVER_GMT_OFFSET` near the top.",
            "Candle attributes: `.time` (str), `.open` (float), `.high` (float), `.low` (float), `.close` (float), `.volume` (float).",
            "Return `Signal(side, sl, tp)` (`side` lowercase `\"buy\"` or `\"sell\"`) or `None`.",
            "Next-open fill: a signal on candle `i` enters at `candles[i + 1].open`. For `\"buy\"`, ensure `sl < entry < tp`; for `\"sell\"`, ensure `tp < entry < sl`. If SL and TP both fall inside one candle, SL hits first.",
            "Skipped-candle catch-up: `on_candle` is only called when no trade is open. Track `last_processed_index` and update all missed candles first.",
            "Incremental speed without changing rules: maintain incremental EMAs and bounded state instead of rescanning history; never shorten lookbacks.",
            "Python 3.11 standard library only (no `pandas`, `numpy`, or pip installs) so execution stays 100% offline inside the APK."
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Part A: Overview
        item {
            LiquidGlassCard(testTag = "guide_section_overview") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "A. Embedded Python Backtester",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Strategies are plain `.py` files executed offline inside the app via Chaquopy against the bundled `backtest.py` engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Upload any `.py` file in Strategies, give it a display name (Class Name is optional — leave blank to auto-detect the first Strategy subclass), then pick your MT5 or CSV candle export on the Backtest screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Part B: 9-Rule Contract
        item {
            LiquidGlassCard(testTag = "guide_section_contract") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Rule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "B. 11-Rule Strategy Contract",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                contractRules.forEachIndexed { index, rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = rule,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Part C: Example my_strategy.py
        item {
            LiquidGlassCard(testTag = "guide_section_example") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "C. Example: my_strategy.py",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(EXAMPLE_STRATEGY_CODE))
                            copiedExample = true
                        },
                        modifier = Modifier.testTag("copy_example_strategy_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedExample) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy example code",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedExample) "Copied" else "Copy", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = EXAMPLE_STRATEGY_CODE.trimEnd(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Part D: Copy AI Prompt
        item {
            LiquidGlassCard(testTag = "guide_section_prompt") {
                Text(
                    text = "D. AI Strategy Prompt Template",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paste this prompt into any coding assistant along with your setup rules to generate a compatible `.py` strategy file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = AI_STRATEGY_PROMPT,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(AI_STRATEGY_PROMPT))
                            copiedPrompt = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_ai_prompt_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedPrompt) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy AI prompt",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedPrompt) "Prompt Copied" else "Copy AI prompt",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenStrategies,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("guide_upload_strategy_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Go to Strategies",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Strategies")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
