package com.example.backtest

enum class AiProvider(
    val displayName: String,
    val companyName: String,
    val keyLabel: String,
    val keyPlaceholder: String
) {
    CLAUDE(
        displayName = "Claude",
        companyName = "Anthropic",
        keyLabel = "Anthropic API Key",
        keyPlaceholder = "sk-ant-..."
    ),
    GEMINI(
        displayName = "Gemini",
        companyName = "Google",
        keyLabel = "Gemini API Key",
        keyPlaceholder = "AIza..."
    ),
    OPENAI(
        displayName = "OpenAI",
        companyName = "OpenAI",
        keyLabel = "OpenAI API Key",
        keyPlaceholder = "sk-..."
    )
}

data class AiModelOption(
    val id: String,
    val displayName: String,
    val tierSummary: String
)

object AiModelCatalog {

    val claudeModels: List<AiModelOption> = listOf(
        AiModelOption("claude-opus-5-5", "Claude Opus 5.5", "Smarter & most effective coding — higher cost"),
        AiModelOption("claude-fable-5-2", "Claude Fable 5.2", "Deep frontier reasoning — higher cost"),
        AiModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5", "Balanced effectiveness & moderate cost"),
        AiModelOption("claude-haiku-5-5", "Claude Haiku 5.5", "Fast & lowest cost (simpler rules)"),
        AiModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6", "4.6 generation balanced cost"),
        AiModelOption("claude-opus-4-6", "Claude Opus 4.6", "4.6 generation deep reasoning — higher cost"),
        AiModelOption("claude-haiku-4-5", "Claude Haiku 4.5", "4.5 generation fast & low cost")
    )

    val geminiModels: List<AiModelOption> = listOf(
        AiModelOption("gemini-4-argon", "Gemini 4 Argon", "Smarter flagship coding & math — higher cost"),
        AiModelOption("gemini-4-carbon", "Gemini 4 Carbon", "Deepest frontier reasoning — highest cost"),
        AiModelOption("gemini-4-hydrogen", "Gemini 4 Hydrogen", "Balanced coding speed & moderate cost"),
        AiModelOption("gemini-3-pro-preview", "Gemini 3 Pro Preview", "Advanced reasoning — higher cost"),
        AiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "Fast & budget-friendly generation"),
        AiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Fastest & lowest API cost"),
        AiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Stable low-cost baseline")
    )

    val openAiModels: List<AiModelOption> = listOf(
        AiModelOption("gpt-6-astra", "GPT-6 Astra", "Smarter flagship reasoning & code — higher cost"),
        AiModelOption("gpt-6.1-sol", "GPT-6.1 Sol", "Balanced high accuracy & moderate cost"),
        AiModelOption("gpt-6.1-terra", "GPT-6.1 Terra", "Fast mid-tier & cost efficient"),
        AiModelOption("gpt-6-luna", "GPT-6 Luna", "Fastest & lowest cost (simpler strategies)"),
        AiModelOption("gpt-oss-120b", "GPT-OSS 120B", "Open-weight reasoning tier")
    )

    fun modelsFor(provider: AiProvider): List<AiModelOption> = when (provider) {
        AiProvider.CLAUDE -> claudeModels
        AiProvider.GEMINI -> geminiModels
        AiProvider.OPENAI -> openAiModels
    }

    fun defaultModelFor(provider: AiProvider): AiModelOption = modelsFor(provider).first()
}
