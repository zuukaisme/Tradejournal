package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.domain.model.NewsEventType
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY date DESC, timestamp DESC")
    fun getAllTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE date = :date ORDER BY timestamp DESC")
    fun getTradesByDate(date: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :id LIMIT 1")
    suspend fun getTradeById(id: Long): TradeEntity?

    @Query("SELECT * FROM trades WHERE strategyId = :strategyId ORDER BY date DESC")
    fun getTradesByStrategy(strategyId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE botId = :botId ORDER BY date DESC")
    fun getTradesByBot(botId: Long): Flow<List<TradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity): Long

    @Update
    suspend fun updateTrade(trade: TradeEntity)

    @Delete
    suspend fun deleteTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteTradeById(id: Long)

    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTrades(trades: List<TradeEntity>)
}

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY id ASC")
    fun getAllStrategies(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE id = :id LIMIT 1")
    suspend fun getStrategyById(id: Long): StrategyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity): Long

    @Update
    suspend fun updateStrategy(strategy: StrategyEntity)

    @Delete
    suspend fun deleteStrategy(strategy: StrategyEntity)

    @Query("DELETE FROM strategies WHERE id = :id")
    suspend fun deleteStrategyById(id: Long)

    @Query("DELETE FROM strategies")
    suspend fun deleteAllStrategies()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStrategies(strategies: List<StrategyEntity>)
}

@Dao
interface BotDao {
    @Query("SELECT * FROM bots ORDER BY updatedAt DESC")
    fun getAllBots(): Flow<List<BotEntity>>

    @Query("SELECT * FROM bots WHERE id = :id LIMIT 1")
    suspend fun getBotById(id: Long): BotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBot(bot: BotEntity): Long

    @Update
    suspend fun updateBot(bot: BotEntity)

    @Delete
    suspend fun deleteBot(bot: BotEntity)

    @Query("DELETE FROM bots WHERE id = :id")
    suspend fun deleteBotById(id: Long)

    @Query("DELETE FROM bots")
    suspend fun deleteAllBots()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBots(bots: List<BotEntity>)
}

@Dao
interface BotVersionDao {
    @Query("SELECT * FROM bot_versions WHERE botId = :botId ORDER BY timestamp DESC")
    fun getVersionsByBotId(botId: Long): Flow<List<BotVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: BotVersionEntity): Long

    @Query("DELETE FROM bot_versions WHERE botId = :botId")
    suspend fun deleteVersionsByBotId(botId: Long)
}

@Dao
interface DailyReviewDao {
    @Query("SELECT * FROM daily_reviews ORDER BY date DESC")
    fun getAllReviews(): Flow<List<DailyReviewEntity>>

    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    suspend fun getReviewByDate(date: String): DailyReviewEntity?

    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    fun observeReviewByDate(date: String): Flow<DailyReviewEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: DailyReviewEntity)

    @Query("DELETE FROM daily_reviews")
    suspend fun deleteAllReviews()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)
}

@Dao
interface OutlookDao {
    @Query("SELECT * FROM news_releases ORDER BY timestamp DESC")
    fun getAllReleases(): Flow<List<NewsReleaseEntity>>

    @Query("SELECT * FROM news_releases WHERE eventType = :type ORDER BY timestamp DESC")
    fun getReleasesByType(type: NewsEventType): Flow<List<NewsReleaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelease(release: NewsReleaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReleases(releases: List<NewsReleaseEntity>)

    @Query("SELECT * FROM outlook_predictions ORDER BY releaseTimestamp DESC")
    fun getAllPredictions(): Flow<List<OutlookPredictionEntity>>

    @Query("SELECT * FROM outlook_predictions WHERE eventType = :type ORDER BY releaseTimestamp DESC LIMIT 1")
    fun getLatestPrediction(type: NewsEventType): Flow<OutlookPredictionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: OutlookPredictionEntity): Long

    @Update
    suspend fun updatePrediction(prediction: OutlookPredictionEntity)

    @Query("DELETE FROM outlook_predictions WHERE id = :id")
    suspend fun deletePrediction(id: Long)

    @Query("DELETE FROM news_releases")
    suspend fun deleteAllReleases()

    @Query("DELETE FROM outlook_predictions")
    suspend fun deleteAllPredictions()
}

@Dao
interface PythonStrategyDao {
    @Query("SELECT * FROM python_strategies ORDER BY updatedAt DESC")
    fun getAllPythonStrategies(): Flow<List<PythonStrategyEntity>>

    @Query("SELECT * FROM python_strategies WHERE id = :id LIMIT 1")
    suspend fun getPythonStrategyById(id: Long): PythonStrategyEntity?
\n    @Query("SELECT * FROM python_strategies WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getPythonStrategyByName(name: String): PythonStrategyEntity?
\n    @Query("SELECT * FROM python_strategies WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun getPythonStrategyByNameExcludingId(name: String, excludeId: Long): PythonStrategyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPythonStrategy(strategy: PythonStrategyEntity): Long

    @Update
    suspend fun updatePythonStrategy(strategy: PythonStrategyEntity)

    @Query("DELETE FROM python_strategies WHERE id = :id")
    suspend fun deletePythonStrategyById(id: Long)

    @Query("DELETE FROM python_strategies")
    suspend fun deleteAllPythonStrategies()
}

@Dao
interface BacktestRunDao {
    @Query("SELECT * FROM backtest_runs ORDER BY createdAt DESC")
    fun getAllRuns(): Flow<List<BacktestRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: BacktestRunEntity): Long

    @Query("DELETE FROM backtest_runs WHERE id = :id")
    suspend fun deleteRunById(id: Long)

    @Query("DELETE FROM backtest_runs")
    suspend fun deleteAllRuns()
}


