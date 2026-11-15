package com.example

import com.example.backtest.AiProvider
import com.example.backtest.AiStrategyGenerator
import com.example.domain.model.EconomicCalendarEvent
import com.example.domain.model.NewsEventType
import com.example.domain.usecase.GoldNewsFeedMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutlookAiUnitTest {

    @Test
    fun generateTextCompletion_validatesBlankKeyModelAndPromptForAllProviders() {
        for (provider in AiProvider.entries) {
            val missingKey = AiStrategyGenerator.generateTextCompletion(
                provider = provider,
                modelId = "test-model",
                apiKey = "   ",
                systemPrompt = "System",
                userPrompt = "Prompt"
            )
            assertTrue(missingKey.isFailure)
            assertTrue(missingKey.exceptionOrNull()?.message?.contains(provider.keyLabel) == true)

            val missingModel = AiStrategyGenerator.generateTextCompletion(
                provider = provider,
                modelId = "",
                apiKey = "sk-test-123",
                systemPrompt = "System",
                userPrompt = "Prompt"
            )
            assertTrue(missingModel.isFailure)

            val missingPrompt = AiStrategyGenerator.generateTextCompletion(
                provider = provider,
                modelId = "test-model",
                apiKey = "sk-test-123",
                systemPrompt = "System",
                userPrompt = "   "
            )
            assertTrue(missingPrompt.isFailure)
        }
    }

    @Test
    fun requestPayloads_includeCustomGoldOutlookSystemAndUserPrompts() {
        val sys = "Gold quantitative macro analyst"
        val usr = "NFP 62% Buy Gold / 38% Sell Gold"

        val anthropic = AiStrategyGenerator.buildAnthropicRequestJson("claude-opus-5-5", sys, usr)
        assertEquals("claude-opus-5-5", anthropic.getString("model"))
        assertEquals(sys, anthropic.getString("system"))
        assertEquals(usr, anthropic.getJSONArray("messages").getJSONObject(0).getString("content"))

        val gemini = AiStrategyGenerator.buildGeminiRequestJson(sys, usr, temperature = 0.25)
        assertEquals(
            sys,
            gemini.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )
        assertEquals(
            usr,
            gemini.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        )
        assertEquals(0.25, gemini.getJSONObject("generationConfig").getDouble("temperature"), 1e-6)

        val openAi = AiStrategyGenerator.buildOpenAiRequestJson("gpt-6-astra", sys, usr)
        assertEquals("gpt-6-astra", openAi.getString("model"))
        val msgs = openAi.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        assertEquals(sys, msgs.getJSONObject(0).getString("content"))
        assertEquals("user", msgs.getJSONObject(1).getString("role"))
        assertEquals(usr, msgs.getJSONObject(1).getString("content"))
    }

    @Test
    fun goldNewsFeedMapper_filtersUsdCatalystsAndExtractsNfpCpiReleases() {
        val now = 1_773_600_000_000L
        val events = listOf(
            EconomicCalendarEvent(
                id = "1",
                title = "Non-Farm Employment Change",
                currency = "USD",
                timestamp = now + 7_200_000L,
                impact = "High",
                actual = null,
                forecast = "185K",
                previous = "151K"
            ),
            EconomicCalendarEvent(
                id = "2",
                title = "Core CPI m/m",
                currency = "USD",
                timestamp = now + 3_600_000L,
                impact = "High",
                actual = null,
                forecast = "0.3%",
                previous = "0.4%"
            ),
            EconomicCalendarEvent(
                id = "3",
                title = "CPI y/y",
                currency = "USD",
                timestamp = now - 86_400_000L,
                impact = "High",
                actual = "2.9%",
                forecast = "3.0%",
                previous = "3.1%"
            ),
            EconomicCalendarEvent(
                id = "4",
                title = "Visitor Arrivals m/m",
                currency = "NZD",
                timestamp = now + 1_800_000L,
                impact = "Low",
                actual = null,
                forecast = null,
                previous = "1.2%"
            )
        )

        val filtered = GoldNewsFeedMapper.filterGoldMovingEvents(events, now)
        assertEquals(3, filtered.size)
        // Upcoming events sorted earliest first, then past events
        assertEquals("Core CPI m/m", filtered[0].title)
        assertEquals("Non-Farm Employment Change", filtered[1].title)
        assertEquals("CPI y/y", filtered[2].title)

        val releases = GoldNewsFeedMapper.toNewsReleases(events, now)
        assertEquals(2, releases.size)
        val nfp = releases.first { it.eventType == NewsEventType.NFP }
        assertTrue(nfp.isUpcoming)
        assertEquals(151.0, nfp.previous, 1e-6)
        assertEquals(185.0, nfp.forecast, 1e-6)
        assertEquals("K", nfp.unit)

        val cpi = releases.first { it.eventType == NewsEventType.CPI }
        assertFalse(cpi.isUpcoming)
        assertEquals(2.9, cpi.actual!!, 1e-6)
        assertEquals(3.1, cpi.previous, 1e-6)
        assertEquals(-0.2, cpi.surprise!!, 1e-6)
        assertEquals("%", cpi.unit)
    }
}
