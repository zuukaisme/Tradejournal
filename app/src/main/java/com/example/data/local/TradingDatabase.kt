package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.AppThemePreset
import com.example.domain.model.BottomNavStyle
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
        OutlookPredictionEntity::class,
        PythonStrategyEntity::class,
        BacktestRunEntity::class,
        AccountProfileEntity::class
    ],
    version = 7,
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
    abstract fun pythonStrategyDao(): PythonStrategyDao
    abstract fun backtestRunDao(): BacktestRunDao
    abstract fun accountProfileDao(): AccountProfileDao

    companion object {
        @Volatile
        private var INSTANCE: TradingDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `accountCategory` TEXT NOT NULL,
                        `startingBalance` REAL NOT NULL,
                        `defaultLotSize` REAL NOT NULL,
                        `leverage` INTEGER NOT NULL,
                        `commissionPerLot` REAL NOT NULL,
                        `valuePerPointPerLot` REAL NOT NULL,
                        `contractSizePerLot` REAL NOT NULL,
                        `stopOutLevelPct` REAL NOT NULL,
                        `instrumentLabel` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `activeAccountProfileId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `testerLotSize` REAL NOT NULL DEFAULT 0.1")
                db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `testerLeverage` INTEGER NOT NULL DEFAULT 100")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): TradingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TradingDatabase::class.java,
                    "tradepulse_v2.db"
                )
                    .addMigrations(MIGRATION_6_7)
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
                        maxTrailingDrawdown = 2500.0,
                        pinLockEnabled = false,
                        pinCode = "",
                        eveningReminderEnabled = false,
                        reminderHour = 20,
                        reminderMinute = 0,
                        isDarkTheme = true,
                        themePreset = AppThemePreset.EMERALD_OBSIDIAN,
                        fredApiKey = "",
                        finnhubApiKey = "",
                        geminiApiKey = "",
                        claudeApiKey = "",
                        openAiApiKey = "",
                        backtestAiProvider = "CLAUDE",
                        backtestAiModel = "claude-opus-5-5",
                        isOutlookEnabled = true,
                        newsAlertEnabled = false,
                        bottomNavStyle = BottomNavStyle.LIQUID_GLASS,
                        activeAccountProfileId = 0L,
                        testerLotSize = 0.10,
                        testerLeverage = 100
                    )
                )
            }
        }
    }
}

