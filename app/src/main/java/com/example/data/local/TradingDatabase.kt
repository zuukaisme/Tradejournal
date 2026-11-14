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
                        calendarImportance = "high",
                        finnhubApiKey = "",
                        geminiApiKey = "",
                        claudeApiKey = "",
                        openAiApiKey = "",
                        backtestAiProvider = "CLAUDE",
                        backtestAiModel = "claude-opus-5-5",
                        isOutlookEnabled = true,
                        newsAlertEnabled = false,
                        bottomNavStyle = BottomNavStyle.LIQUID_GLASS
                    )
                )
