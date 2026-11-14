@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val currency: CurrencyCode,
    val monthlyProfitTarget: Double,
    val maxDailyLossLimit: Double,
    val maxTrailingDrawdown: Double = 2000.0,
    val pinLockEnabled: Boolean,
    val pinCode: String,
    val eveningReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val isDarkTheme: Boolean,
    val themePreset: AppThemePreset = AppThemePreset.EMERALD_OBSIDIAN,
    val fredApiKey: String,
    val calendarImportance: String = "high",
    val finnhubApiKey: String,
    val geminiApiKey: String,
    val claudeApiKey: String = "",
    val openAiApiKey: String = "",
    val backtestAiProvider: String = "CLAUDE",
    val backtestAiModel: String = "claude-opus-5-5",
    val isOutlookEnabled: Boolean,
    val newsAlertEnabled: Boolean,
    val bottomNavStyle: BottomNavStyle = BottomNavStyle.LIQUID_GLASS
)
