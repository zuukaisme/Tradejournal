package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.domain.model.AppThemePreset

val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val EmeraldObsidianScheme = darkColorScheme(
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

private val TrueAmoledScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003819),
    onPrimaryContainer = EmeraldLight,
    secondary = EmeraldLight,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF002A12),
    onSecondaryContainer = Color(0xFFB9F6CA),
    tertiary = AmberGold,
    onTertiary = Color.Black,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = Color(0xFF2B050B),
    onErrorContainer = CrimsonLight,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF101318),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF05070A),
    surfaceContainer = Color(0xFF090C10),
    surfaceContainerHigh = Color(0xFF121720),
    outline = Color(0x1CFFFFFF),
    outlineVariant = Color(0x0FFFFFFF)
)

private val TerminalAmberScheme = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF1C1300),
    primaryContainer = Color(0xFF3A2800),
    onPrimaryContainer = Color(0xFFFFDF8E),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF1C1300),
    secondaryContainer = Color(0xFF2C2108),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = NeonEmerald,
    onTertiary = Color.Black,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonLight,
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFF3EFE6),
    surface = Color(0xFF111419),
    onSurface = Color(0xFFF3EFE6),
    surfaceVariant = Color(0xFF1A1E25),
    onSurfaceVariant = Color(0xFFA8A29E),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF111419),
    surfaceContainer = Color(0xFF151920),
    surfaceContainerHigh = Color(0xFF1E242E),
    outline = Color(0x1EFFFFFF),
    outlineVariant = Color(0x12FFFFFF)
)

private val PhosphorMatrixScheme = darkColorScheme(
    primary = Color(0xFF00FF88),
    onPrimary = Color(0xFF002412),
    primaryContainer = Color(0xFF003A1E),
    onPrimaryContainer = Color(0xFF8CFFC5),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF002412),
    secondaryContainer = Color(0xFF062C1B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberGold,
    onTertiary = Color.Black,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonLight,
    background = Color(0xFF030B06),
    onBackground = Color(0xFFE2FBE9),
    surface = Color(0xFF06120B),
    onSurface = Color(0xFFE2FBE9),
    surfaceVariant = Color(0xFF0D2116),
    onSurfaceVariant = Color(0xFF7FA98E),
    surfaceContainerLowest = Color(0xFF030B06),
    surfaceContainerLow = Color(0xFF06120B),
    surfaceContainer = Color(0xFF091910),
    surfaceContainerHigh = Color(0xFF10281B),
    outline = Color(0x2400FF88),
    outlineVariant = Color(0x1200FF88)
)

private val MidnightIceScheme = darkColorScheme(
    primary = Color(0xFF00D4AA),
    onPrimary = Color(0xFF00201B),
    primaryContainer = Color(0xFF003830),
    onPrimaryContainer = Color(0xFF84F7E0),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0D2847),
    onSecondaryContainer = Color(0xFFBBDEFB),
    tertiary = AmberGold,
    onTertiary = Color.Black,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonLight,
    background = Color(0xFF08101C),
    onBackground = Color(0xFFEFF6FF),
    surface = Color(0xFF0D1726),
    onSurface = Color(0xFFEFF6FF),
    surfaceVariant = Color(0xFF152338),
    onSurfaceVariant = Color(0xFF93A8C3),
    surfaceContainerLowest = Color(0xFF08101C),
    surfaceContainerLow = Color(0xFF0D1726),
    surfaceContainer = Color(0xFF111E30),
    surfaceContainerHigh = Color(0xFF192B42),
    outline = Color(0x1C93C5FD),
    outlineVariant = Color(0x0F93C5FD)
)

private val DefaultLightColorScheme = lightColorScheme(
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

private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF006E33),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F2D8),
    onPrimaryContainer = Color(0xFF003919),
    secondary = Color(0xFF0F4C81),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBFF),
    onSecondaryContainer = Color(0xFF082D4F),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    error = Color(0xFFB71C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF7F0000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF05080C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF05080C),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF1E293B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = Color(0x330F172A),
    outlineVariant = Color(0x1F0F172A)
)

fun colorSchemeForPreset(
    preset: AppThemePreset,
    darkTheme: Boolean
): ColorScheme {
    if (!darkTheme || preset == AppThemePreset.HIGH_CONTRAST_LIGHT) {
        return when (preset) {
            AppThemePreset.HIGH_CONTRAST_LIGHT -> HighContrastLightColorScheme
            AppThemePreset.TERMINAL_AMBER -> DefaultLightColorScheme.copy(
                primary = Color(0xFFB45309),
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F)
            )
            AppThemePreset.MIDNIGHT_ICE -> DefaultLightColorScheme.copy(
                primary = Color(0xFF00796B),
                primaryContainer = Color(0xFFE0F2F1),
                onPrimaryContainer = Color(0xFF004D40)
            )
            else -> DefaultLightColorScheme
        }
    }
    return when (preset) {
        AppThemePreset.EMERALD_OBSIDIAN -> EmeraldObsidianScheme
        AppThemePreset.TRUE_AMOLED -> TrueAmoledScheme
        AppThemePreset.TERMINAL_AMBER -> TerminalAmberScheme
        AppThemePreset.PHOSPHOR_MATRIX -> PhosphorMatrixScheme
        AppThemePreset.MIDNIGHT_ICE -> MidnightIceScheme
        AppThemePreset.HIGH_CONTRAST_LIGHT -> HighContrastLightColorScheme
    }
}

@Composable
fun TradePulseTheme(
    darkTheme: Boolean = true,
    themePreset: AppThemePreset = AppThemePreset.EMERALD_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val effectiveDark = darkTheme && !themePreset.forcesLight
    val colorScheme = colorSchemeForPreset(themePreset, effectiveDark)

    CompositionLocalProvider(LocalIsDarkTheme provides effectiveDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
