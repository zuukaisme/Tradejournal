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
        - Optional `manage_trade(self, i: int, candles: List[Candle], trade: Trade) -> Optional[str]` hook (called after each completed bar while a trade is open): may adjust `trade.sl` (e.g., move `trade.sl = trade.entry` for break-even or trail stop) or return `"be"` / `"exit"` to close at bar close.

        EXECUTION CONTRACT (9 RULES):
        1. Subclass `Strategy` (e.g. `class CustomStrategy(Strategy):`).
        2. Implement `def on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]:` (and optionally `def manage_trade(self, i: int, candles: List[Candle], trade: Trade) -> Optional[str]:`).
        3. Strict no-lookahead: ONLY inspect `candles[0:i + 1]` (`candles[0..i]`). NEVER access `candles[i + 1:]` or future values.
        4. Warmup & bounds: Engine starts evaluating signals at `i >= 50` (one open trade at a time). Still explicitly guard index bounds for every lookback window (e.g., `if i < 50: return None`).
        5. Guard zero division / flat windows: Verify ATR/range/denominator > 0 before dividing or computing stops.
        6. Next-open execution & price ordering: A signal returned on candle `i` fills at `candles[i + 1].open`. For `"buy"`, keep `sl < candles[i].close < tp`; for `"sell"`, keep `tp < candles[i].close < sl`. Ensure `abs(candles[i].close - sl) > 0`.
        7. If SL and TP are both touched inside one candle, the engine assumes SL hits first.
        8. PERFORMANCE WITHOUT STRATEGY CHANGES: Keep per-candle work efficient. Prefer precomputed values or rolling state updated once per candle when they produce exactly the same values as the specified indicator/lookback. Use bounded slices for genuinely fixed lookbacks. Never shorten, cap, or silently replace a strategy's required historical lookback, indicator period, entry condition, exit condition, stop, target, or cooldown just to improve speed. If a rule genuinely needs the full available history, preserve that rule and optimize its calculation without changing its result.
        9. Avoid unnecessary instance mutation that breaks re-runs; reset helper state cleanly in `__init__` or when the engine begins a new run. Any optimization must be deterministic and preserve the same signal on each candle as the unoptimized rule.
        10. Output ONLY valid, runnable raw Python code with docstrings and comments explaining every rule. Do not include prose outside Python comments/docstrings.
    """.trimIndent()

    internal fun buildDetailedUserPrompt(userDescription: String): String = """
        Build a complete, self-contained Python `.py` strategy file implementing these trading rules:

        USER STRATEGY SPECIFICATION:
        $userDescription

        CHECKLIST FOR YOUR OUTPUT:
        - Start with `from backtest import Strategy, Signal` and standard library imports only (no numpy/pandas).
        - Define one `Strategy` subclass with `def on_candle(self, i, candles):`.
        - Return a `Signal(side="buy" | "sell", sl=float, tp=float)` object when triggered, otherwise `None`.
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
            val rawResponse = when (provider) {
                AiProvider.CLAUDE -> callAnthropic(trimmedKey, trimmedModel, systemPrompt, trimmedPrompt)
                AiProvider.GEMINI -> callGemini(trimmedKey, trimmedModel, systemPrompt, trimmedPrompt, temperature)
                AiProvider.OPENAI -> callOpenAi(trimmedKey, trimmedModel, systemPrompt, trimmedPrompt)
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

    private fun formatDisplayName(className: String): String {
        if (className.isBlank()) return "AI Strategy"
        return className
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .trim()
    }
}
