package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.domain.model.AppThemePreset
import com.example.notifications.EveningReminderScheduler
import com.example.presentation.MainScreen
import com.example.presentation.TradingViewModel
import com.example.presentation.screens.PinLockScreen
import com.example.ui.theme.TradePulseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TradingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()
            var isDarkTheme by remember(settings.isDarkTheme) {
                mutableStateOf(settings.isDarkTheme)
            }
            val effectiveDark = isDarkTheme && !settings.themePreset.forcesLight
            var isPinUnlocked by remember { mutableStateOf(false) }

            DisposableEffect(effectiveDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (effectiveDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            LaunchedEffect(settings.eveningReminderEnabled, settings.reminderHour, settings.reminderMinute) {
                EveningReminderScheduler.sync(
                    context = this@MainActivity,
                    enabled = settings.eveningReminderEnabled,
                    hour = settings.reminderHour,
                    minute = settings.reminderMinute
                )
            }

            TradePulseTheme(
                darkTheme = isDarkTheme,
                themePreset = settings.themePreset
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (settings.pinLockEnabled && settings.pinCode.length == 4 && !isPinUnlocked) {
                        PinLockScreen(
                            expectedPin = settings.pinCode,
                            onUnlocked = { isPinUnlocked = true }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onToggleTheme = {
                                val newTheme = !effectiveDark
                                isDarkTheme = newTheme
                                val nextPreset = if (newTheme && settings.themePreset.forcesLight) {
                                    AppThemePreset.EMERALD_OBSIDIAN
                                } else {
                                    settings.themePreset
                                }
                                viewModel.updateSettings(
                                    settings.copy(
                                        isDarkTheme = newTheme,
                                        themePreset = nextPreset
                                    )
                                )
                            },
                            isDarkTheme = effectiveDark
                        )
                    }
                }
            }
        }
    }
}
