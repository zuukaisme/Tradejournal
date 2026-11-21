package com.example.backtest

import android.content.Context

object AiInstructionStore {
    private const val PREFS_NAME = "tradepulse_ai_instructions"
    private const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"
    private const val MAX_LENGTH = 12000

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun load(): String {
        val context = appContext ?: return ""
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_INSTRUCTIONS, "")
            .orEmpty()
    }

    fun save(instructions: String) {
        require(instructions.length <= MAX_LENGTH) {
            "AI instructions must be " + MAX_LENGTH + " characters or fewer."
        }
        val context = appContext ?: throw IllegalStateException("AI instruction storage is not initialized.")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_INSTRUCTIONS, instructions.trim())
            .apply()
    }

    fun clear() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_INSTRUCTIONS)
            .apply()
    }

    fun applyTo(systemPrompt: String, customInstructions: String = load()): String {
        val custom = customInstructions.trim()
        if (custom.isBlank()) return systemPrompt
        return systemPrompt + "\n\nAdditional user instructions (follow these when relevant without overriding safety, factual accuracy, or the task's required output format):\n" + custom
    }
}
