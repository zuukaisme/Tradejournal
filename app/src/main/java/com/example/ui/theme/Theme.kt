package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color(0xFF002410),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldLight,
    secondary = EmeraldLight,
    onSecondary = Color(0xFF002410),
    secondaryContainer = Color(0xFF00381B),
    onSecondaryContainer = Color(0xFFB9F6CA),
    tertiary = AmberGold,
    onTertiary = Color.Black,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceCard,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkBorder,
    outlineVariant = Color(0x0AFFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF5E6),
    onPrimaryContainer = Color(0xFF004D20),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = AmberGold,
    onTertiary = Color.White,
    error = CrimsonDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = LightSurfaceCard,
    surfaceContainerHigh = LightSurfaceElevated,
    outline = LightBorder,
    outlineVariant = Color(0x0F0F172A)
)

@Composable
fun TradePulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
