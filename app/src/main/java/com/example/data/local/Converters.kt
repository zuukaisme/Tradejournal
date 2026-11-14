package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.CurrencyCode
import com.example.domain.model.Emotion
import com.example.domain.model.GoldOutcome
import com.example.domain.model.NewsEventType
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return ""
        return value.joinToString(";;;")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";;;").filter { it.isNotBlank() }
    }

    @TypeConverter
    fun fromTradeResult(value: TradeResult?): String = value?.name ?: TradeResult.WIN.name

    @TypeConverter
    fun toTradeResult(value: String?): TradeResult =
        try {
            TradeResult.valueOf(value ?: TradeResult.WIN.name)
        } catch (e: Exception) {
            TradeResult.WIN
        }

    @TypeConverter
    fun fromTradeDirection(value: TradeDirection?): String = value?.name ?: TradeDirection.BUY.name

    @TypeConverter
    fun toTradeDirection(value: String?): TradeDirection =
        try {
            TradeDirection.valueOf(value ?: TradeDirection.BUY.name)
        } catch (e: Exception) {
            TradeDirection.BUY
        }

    @TypeConverter
    fun fromEmotion(value: Emotion?): String = value?.name ?: Emotion.CALM.name

    @TypeConverter
    fun toEmotion(value: String?): Emotion =
        try {
            Emotion.valueOf(value ?: Emotion.CALM.name)
        } catch (e: Exception) {
            Emotion.CALM
        }

    @TypeConverter
    fun fromBotType(value: BotType?): String = value?.name ?: BotType.EXPERT_ADVISOR.name

    @TypeConverter
    fun toBotType(value: String?): BotType =
        try {
            BotType.valueOf(value ?: BotType.EXPERT_ADVISOR.name)
        } catch (e: Exception) {
            BotType.EXPERT_ADVISOR
        }

    @TypeConverter
    fun fromBotStatus(value: BotStatus?): String = value?.name ?: BotStatus.TESTING.name

    @TypeConverter
    fun toBotStatus(value: String?): BotStatus =
        try {
            BotStatus.valueOf(value ?: BotStatus.TESTING.name)
        } catch (e: Exception) {
            BotStatus.TESTING
        }

    @TypeConverter
    fun fromCurrencyCode(value: CurrencyCode?): String = value?.name ?: CurrencyCode.USD.name

    @TypeConverter
    fun toCurrencyCode(value: String?): CurrencyCode =
        try {
            CurrencyCode.valueOf(value ?: CurrencyCode.USD.name)
        } catch (e: Exception) {
            CurrencyCode.USD
        }

    @TypeConverter
    fun fromNewsEventType(value: NewsEventType?): String = value?.name ?: NewsEventType.NFP.name

    @TypeConverter
    fun toNewsEventType(value: String?): NewsEventType =
        try {
            NewsEventType.valueOf(value ?: NewsEventType.NFP.name)
        } catch (e: Exception) {
            NewsEventType.NFP
        }

    @TypeConverter
    fun fromConfidenceLevel(value: ConfidenceLevel?): String = value?.name ?: ConfidenceLevel.MEDIUM.name

    @TypeConverter
    fun toConfidenceLevel(value: String?): ConfidenceLevel =
        try {
            ConfidenceLevel.valueOf(value ?: ConfidenceLevel.MEDIUM.name)
        } catch (e: Exception) {
            ConfidenceLevel.MEDIUM
        }

    @TypeConverter
    fun fromGoldOutcome(value: GoldOutcome?): String = value?.name ?: GoldOutcome.UNRESOLVED.name

    @TypeConverter
    fun toGoldOutcome(value: String?): GoldOutcome =
        try {
            GoldOutcome.valueOf(value ?: GoldOutcome.UNRESOLVED.name)
        } catch (e: Exception) {
            GoldOutcome.UNRESOLVED
        }
}
