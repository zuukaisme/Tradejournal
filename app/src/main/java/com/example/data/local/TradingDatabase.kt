package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.CurrencyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TradeEntity::class,
        StrategyEntity::class,
        BotEntity::class,
        BotVersionEntity::class,
        DailyReviewEntity::class,
        UserSettingsEntity::class,
        NewsReleaseEntity::class,
        OutlookPredictionEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TradingDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun strategyDao(): StrategyDao
    abstract fun botDao(): BotDao
    abstract fun botVersionDao(): BotVersionDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun outlookDao(): OutlookDao

    companion object {
        @Volatile
        private var INSTANCE: TradingDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): TradingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TradingDatabase::class.java,
                    "tradepulse_v2.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: TradingDatabase) {
            val settingsDao = database.userSettingsDao()

            // Strictly NO dummy trades, strategies, bots, or mock data.
            // Only seed baseline user preferences if empty.
            if (settingsDao.getSettingsOnce() == null) {
                settingsDao.saveSettings(
                    UserSettingsEntity(
                        id = 1,
                        currency = CurrencyCode.USD,
                        monthlyProfitTarget = 5000.0,
                        maxDailyLossLimit = 1000.0,
                        pinLockEnabled = false,
                        pinCode = "",
                        eveningReminderEnabled = false,
                        reminderHour = 20,
                        reminderMinute = 0,
                        isDarkTheme = true,
                        fredApiKey = "",
                        finnhubApiKey = "",
                        geminiApiKey = "",
                        isOutlookEnabled = true,
                        newsAlertEnabled = false
                    )
                )
            }
        }
    }
}
