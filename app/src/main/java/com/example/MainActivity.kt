package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.backtest.BinaryCandleCache
import com.example.notifications.EveningReminderScheduler
import com.example.presentation.MainScreen
import com.example.presentation.TradingViewModel
import com.example.presentation.screens.PinLockScreen
import com.example.ui.theme.TradePulseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TradingViewModel by viewModels()
    private var hasCacheStorageAccess by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        hasCacheStorageAccess = BinaryCandleCache.hasCacheStorageAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasCacheStorageAccess = BinaryCandleCache.hasCacheStorageAccess()

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
                    if (!hasCacheStorageAccess) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Enable fast backtest cache") },
                            text = {
                                Text("Tradepulse stores reusable candle indexes in Downloads/Tradepulse/cache so backtests and replay workers can share them. Android requires All files access for this exact folder. You can continue without it, but the shared disk cache will stay unavailable.")
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                        startActivity(intent)
                                    }
                                ) {
                                    Text("Allow access")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { hasCacheStorageAccess = true }) {
                                    Text("Not now")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
