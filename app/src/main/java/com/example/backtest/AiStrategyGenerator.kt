package com.example.backtest

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeneratedStrategyOutput(
    val code: String,
    val detectedClassName: String,
    val suggestedDisplayName: String
)

object AiStrategyGenerator {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val SYSTEM_PROMPT: String = """
        You are an expert quantitative trading systems developer. Write a single Python strategy file for an embedded Chaquopy Python 3.11 backtester engine (`backtest.py`).

        ENVIRONMENT & PYTHON PACKAGES:
        - Allowed packages: Python 3.11 standard library ONLY (e.g., `math`, `statistics`, `collections`, `datetime`, `dataclasses`, `typing`).
        - FORBIDDEN packages: Do NOT import `pandas`, `numpy`, `scipy`, `talib`, `yfinance`, or any third-party pip library — they are NOT installed in the APK and will crash with ModuleNotFoundError.

        REQUIRED IMPORTS & ENGINE OBJECTS (`backtest` module):
        - `from backtest import Strategy, Signal` (also pre-injected: `Strategy`, `Signal`, `Candle`, `Trade`, `List`, `Optional`).
        - Input object `Candle` fields:
          - `c.time: str`
          - `c.open: float`
          - `c.high: float`
          - `c.low: float`
          - `c.close: float`
          - `c.volume: float` (default 0.0)
        - Returned object `Signal(side, sl, tp)`:
          - `side: str` — MUST be lowercase `"buy"` or `"sell"`.
          - `sl: float` — Stop-loss price level.
          - `tp: float` — Take-profit price level.
        - `on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]` MUST return either a `Signal(side=..., sl=..., tp=...)` object or `None`.
        - Do not implement `manage_trade`; the current engine does not call it.

        EXECUTION CONTRACT (11 RULES):
        1. Subclass `Strategy` (e.g. `class CustomStrategy(Strategy):`).
        2. Implement `def on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]:` only.
        3. Strict no-lookahead: ONLY inspect `candles[0:i + 1]` (`candles[0..i]`). NEVER access `candles[i + 1:]` or future values.
        4. Warmup & bounds: Engine starts evaluating signals at `i >= 50` and permits only one open trade at a time. Explicitly guard every required lookback; do not silently shorten it.
        5. MT5 time parsing: MT5 exports commonly provide `Candle.time` as `YYYY.MM.DD HH:MM:SS` (for example `2024.03.05 10:15:00`), not only `YYYY-MM-DD`. Parse both dotted and dashed dates, with and without seconds, and safely return no signal only when a timestamp truly cannot be parsed. If the strategy uses sessions, parse the timestamp before applying the session filter. Define a clearly named `SERVER_GMT_OFFSET` setting near the top of the strategy (default `2`, configurable to `0` for GMT data) and apply it consistently; do not guess the data timezone.
        6. Guard zero division / flat windows: Verify ATR/range/denominator > 0 before dividing or computing stops.
        7. Next-open execution & price ordering: A signal returned on candle `i` fills at `candles[i + 1].open`. For `"buy"`, keep `sl < candles[i].close < tp`; for `"sell"`, keep `tp < candles[i].close < sl`. Ensure `abs(candles[i].close - sl) > 0`.
        8. If SL and TP are both touched inside one candle, the engine assumes SL hits first.
        9. Engine callback gap handling: `on_candle` is called only when no position is open. While a position is open, candles pass without calling the strategy. Therefore, do not assume consecutive callback indices. Keep `last_processed_index` and, on every callback, process every unseen candle from `last_processed_index + 1` through `i` in chronological order to update all indicator/session/range/previous-day state. Never update an EMA, Asian range, previous-day high/low, or other state only once per callback; catch up over every missed candle first. Use the same per-candle update formula as a sequential full-history calculation.
        10. Performance without changing trading logic: never recalculate an EMA by rescanning the entire history on every callback. Maintain incremental/rolling state and bounded lookbacks only when mathematically equivalent to the requested indicator and exact lookback. Never shorten or cap required historical lookbacks, indicator periods, entry/exit conditions, stops, targets, or cooldowns just to make a backtest faster. Preserve the same signal on every candle; optimize calculations, not strategy rules. Reset all state in `__init__` and make repeated runs deterministic.
        11. Output ONLY valid, runnable raw Python code with docstrings and comments explaining every rule. Do not include prose outside Python comments/docstrings.
    """.trimIndent()

    internal fun buildDetailedUserPrompt(userDescription: String): String = """
        Build a complete, self-contained Python `.py` strategy file implementing these trading rules:

        USER STRATEGY SPECIFICATION:
        $userDescription

        CHECKLIST FOR YOUR OUTPUT:
        - Start with `from backtest import Strategy, Signal` and standard library imports only (no numpy/pandas).
        - Define one `Strategy` subclass with `def on_candle(self, i, candles):`.
        - Return a `Signal(side="buy" | "sell", sl=float, tp=float)` object when triggered, otherwise `None`.
        - Parse both MT5 dotted timestamps (`YYYY.MM.DD HH:MM:SS`) and dashed dates; never reject every candle because the timestamp format differs.
        - If session logic is requested, include a configurable `SERVER_GMT_OFFSET` and apply it before checking the session window.
        - Track the last processed candle index and catch up all missed candles before evaluating a new signal, because callbacks are skipped while a trade is open.
        - Update EMAs and other rolling indicators incrementally per candle; never rescan 1,000+ candles on every callback.
        - Do not add `manage_trade`; the engine does not call it. Use the specified fixed SL/TP signal contract.
        - Verify all slice indices stay within `candles[0..i]` and return `None` when insufficient bars exist or risk <= 0.
    """.trimIndent()

    fun generateTextCompletion(
        provider: AiProvider,
        modelId: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2
    ): Result<String> {
        val trimmedKey = apiKey.trim()
        val trimmedModel = modelId.trim()
        val trimmedPrompt = userPrompt.trim()

        if (trimmedKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your ${provider.keyLabel} first."))
        }
        if (trimmedModel.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select or enter a valid model ID."))
        }
        if (trimmedPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Prompt cannot be empty."))
        }

        return try {
            val effectiveSystemPrompt = AiInstructionStore.applyTo(systemPrompt)
            val rawResponse = when (provider) {
                AiProvider.CLAUDE -> callAnthropic(trimmedKey, trimmedModel, effectiveSystemPrompt, trimmedPrompt)
                AiProvider.GEMINI -> callGemini(trimmedKey, trimmedModel, effectiveSystemPrompt, trimmedPrompt, temperature)
                AiProvider.OPENAI -> callOpenAi(trimmedKey, trimmedModel, effectiveSystemPrompt, trimmedPrompt)
            }
            val cleaned = rawResponse.trim()
            if (cleaned.isEmpty()) {
                Result.failure(IllegalStateException("Model returned an empty response."))
            } else {
                Result.success(cleaned)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateStrategy(
        provider: AiProvider,
        modelId: String,
        apiKey: String,
        userDescription: String
    ): Result<GeneratedStrategyOutput> {
        val trimmedPrompt = userDescription.trim()
        if (trimmedPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Describe the trading strategy you want AI to build."))
        }

        val detailedUserPrompt = buildDetailedUserPrompt(trimmedPrompt)
        val rawResult = generateTextCompletion(
            provider = provider,
            modelId = modelId,
            apiKey = apiKey,
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = detailedUserPrompt,
            temperature = 0.2
        )

        return rawResult.fold(
            onSuccess = { rawResponse ->
                val cleanedCode = extractPythonCode(rawResponse)
                if (cleanedCode.isBlank()) {
                    Result.failure(IllegalStateException("Model returned empty code."))
                } else {
                    val detectedClass = detectStrategyClassName(cleanedCode)
                    val displayName = formatDisplayName(detectedClass)
                    Result.success(
                        GeneratedStrategyOutput(
                            code = cleanedCode,
                            detectedClassName = detectedClass,
                            suggestedDisplayName = displayName
                        )
                    )
                }
            },
            onFailure = { err -> Result.failure(err) }
        )
    }

    fun fetchAvailableModels(
        provider: AiProvider,
        apiKey: String
    ): Result<List<AiModelOption>> {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your ${provider.keyLabel} to fetch live models."))
        }
        return try {
            val models = when (provider) {
                AiProvider.CLAUDE -> fetchAnthropicModels(trimmedKey)
                AiProvider.GEMINI -> fetchGeminiModels(trimmedKey)
                AiProvider.OPENAI -> fetchOpenAiModels(trimmedKey)
            }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callAnthropic(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val payload = buildAnthropicRequestJson(modelId, systemPrompt, userPrompt)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val body = executeJsonRequest(req, "Claude")
        val contentArr = JSONObject(body).optJSONArray("content")
            ?: throw IllegalStateException("Claude response missing content array.")
        val sb = StringBuilder()
        for (idx in 0 until contentArr.length()) {
            val block = contentArr.getJSONObject(idx)
            if (block.optString("type") == "text") {
                sb.append(block.optString("text"))
            }
        }
        return sb.toString()
    }

    private fun callGemini(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2
    ): String {
        val normalizedId = modelId.removePrefix("models/")
        val payload = buildGeminiRequestJson(systemPrompt, userPrompt, temperature)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$normalizedId:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val body = executeJsonRequest(req, "Gemini")
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Gemini returned no candidates.")
        if (candidates.length() == 0) throw IllegalStateException("Gemini returned 0 candidates.")
        val parts = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        val sb = StringBuilder()
        for (idx in 0 until parts.length()) {
            sb.append(parts.getJSONObject(idx).optString("text"))
        }
        return sb.toString()
    }

    private fun callOpenAi(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val payload = buildOpenAiRequestJson(modelId, systemPrompt, userPrompt)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val body = executeJsonRequest(req, "OpenAI")
        val choices = JSONObject(body).optJSONArray("choices")
            ?: throw IllegalStateException("OpenAI returned no choices.")
        if (choices.length() == 0) throw IllegalStateException("OpenAI returned 0 choices.")
        return choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun fetchAnthropicModels(apiKey: String): List<AiModelOption> {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/models?limit=50")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        val body = executeJsonRequest(req, "Claude")
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<AiModelOption>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val id = item.optString("id")
            val display = item.optString("display_name").ifBlank { id }
            if (id.isNotBlank()) {
                out.add(AiModelOption(id = id, displayName = display, tierSummary = "Live API model"))
            }
        }
        return out
    }

    private fun fetchGeminiModels(apiKey: String): List<AiModelOption> {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100")
            .get()
            .build()
        val body = executeJsonRequest(req, "Gemini")
        val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
        val out = mutableListOf<AiModelOption>()
        for (i in 0 until models.length()) {
            val item = models.getJSONObject(i)
            val methods = item.optJSONArray("supportedGenerationMethods")
            var supportsGenerate = false
            if (methods != null) {
                for (m in 0 until methods.length()) {
                    if (methods.optString(m) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
            }
            if (!supportsGenerate) continue
            val rawName = item.optString("name")
            val id = rawName.removePrefix("models/")
            val display = item.optString("displayName").ifBlank { id }
            if (id.isNotBlank() && id.contains("gemini", ignoreCase = true)) {
                out.add(AiModelOption(id = id, displayName = display, tierSummary = "Live API model"))
            }
        }
        return out
    }

    private fun fetchOpenAiModels(apiKey: String): List<AiModelOption> {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val body = executeJsonRequest(req, "OpenAI")
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<AiModelOption>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val id = item.optString("id")
            if (id.isNotBlank() && (id.startsWith("gpt-") || id.startsWith("o"))) {
                out.add(AiModelOption(id = id, displayName = id, tierSummary = "Live API model"))
            }
        }
        return out.sortedByDescending { it.id }
    }

    private fun executeJsonRequest(req: Request, providerLabel: String): String {
        return client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val parsedError = extractApiErrorMessage(text)
                throw IllegalStateException(
                    if (parsedError.isNotBlank()) "$providerLabel API (${resp.code}): $parsedError"
                    else "$providerLabel API HTTP ${resp.code}"
                )
            }
            if (text.isBlank()) throw IllegalStateException("Empty response from $providerLabel API.")
            text
        }
    }

    internal fun buildAnthropicRequestJson(
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        put("max_tokens", 4096)
        put("system", systemPrompt)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        })
    }

    internal fun buildGeminiRequestJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2
    ): JSONObject = JSONObject().apply {
        put("systemInstruction", JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            })
        })
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", userPrompt))
                })
            })
        })
        put("generationConfig", JSONObject().apply {
            put("temperature", temperature)
        })
    }

    internal fun buildOpenAiRequestJson(
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        })
    }

    internal fun extractApiErrorMessage(responseBody: String): String {
        return try {
            val obj = JSONObject(responseBody)
            when {
                obj.has("error") && obj.get("error") is JSONObject ->
                    obj.getJSONObject("error").optString("message", "")
                obj.has("message") -> obj.optString("message", "")
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun extractPythonCode(rawText: String): String {
        val trimmed = rawText.trim()
        val pythonFenceRegex = Regex("```(?:python|py)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = pythonFenceRegex.find(trimmed)
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            trimmed
                .removePrefix("```python")
                .removePrefix("```py")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
    }

    fun detectStrategyClassName(code: String): String {
        val regex = Regex("""class\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*Strategy[^)]*\)\s*:""")
        return regex.find(code)?.groupValues?.getOrNull(1).orEmpty()
    }

    /**
     * Audits Python strategy source against the backtest.py contract so missing objects,
     * forbidden imports, or known engine pitfalls are flagged immediately in the UI.
     */
    fun validateStrategyContract(code: String): List<String> {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return listOf("Strategy code is empty.")
        }
        val warnings = mutableListOf<String>()

        if (detectStrategyClassName(trimmed).isBlank()) {
            warnings.add("Missing Strategy subclass (expected `class MyStrategy(Strategy):`).")
        }
        if (!Regex("""def\s+on_candle\s*\(""").containsMatchIn(trimmed)) {
            warnings.add("Missing `def on_candle(self, i, candles):` method.")
        }
        if (!trimmed.contains("Signal(")) {
            warnings.add("Missing `Signal(side, sl, tp)` return object.")
        }
        val forbiddenLibs = listOf("pandas", "numpy", "scipy", "talib", "yfinance")
        for (lib in forbiddenLibs) {
            val libRegex = Regex("""(?m)^\s*(?:import|from)\s+$lib\b""")
            if (libRegex.containsMatchIn(trimmed)) {
                warnings.add("Uses forbidden library `$lib`. Only Python 3.11 standard library is bundled.")
            }
        }
        if (Regex("""candles\s*\[\s*i\s*\+""").containsMatchIn(trimmed)) {
            warnings.add("Possible lookahead indexing (`candles[i + ...]`). Only read `candles[0..i]`.")
        }
        if (Regex("""def\s+manage_trade\s*\(""").containsMatchIn(trimmed)) {
            warnings.add("Defines `manage_trade`, which is not called by the current engine.")
        }
        val usesTime = trimmed.contains(".time")
        val handlesDottedDate = trimmed.contains("%Y.%m.%d") ||
            trimmed.contains("""replace("." """) ||
            trimmed.contains("""replace(".", "-")""") ||
            trimmed.contains("""replace('.', '-')""") ||
            trimmed.contains("""split(".")""") ||
            trimmed.contains("""split('.')""") ||
            trimmed.contains("""'%Y.%m.%d'""")
        if (usesTime && !handlesDottedDate) {
            warnings.add("Reads `candle.time` without apparent MT5 dotted-date (`YYYY.MM.DD HH:MM:SS`) parsing.")
        }
        val usesStatefulIndicators = Regex(
            """\b(ema[a-z0-9_]*|asian_range[a-z0-9_]*|prev_day[a-z0-9_]*|previous_day[a-z0-9_]*)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(trimmed)
        val hasCatchUpTracker = trimmed.contains("last_processed", ignoreCase = true) ||
            trimmed.contains("last_idx", ignoreCase = true) ||
            trimmed.contains("last_i", ignoreCase = true)
        if (usesStatefulIndicators && !hasCatchUpTracker) {
            warnings.add("Uses EMA/session/range state without apparent skipped-candle catch-up tracking (`last_processed_index`).")
        }
        return warnings
    }

    private fun formatDisplayName(className: String): String {
        if (className.isBlank()) return "AI Strategy"
        return className
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .trim()
    }
}
