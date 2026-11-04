package com.group8.permissionmanagerapp_uss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Slate900,
    onPrimary = White,
    primaryContainer = Slate100,
    onPrimaryContainer = Slate900,
    secondary = Slate700,
    onSecondary = White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = Slate100,
    outline = Slate300,
    outlineVariant = Slate200,
    error = RiskHigh,
)

private val DarkColorScheme = darkColorScheme(
    primary = Slate100,
    onPrimary = Slate900,
    primaryContainer = Slate700,
    onPrimaryContainer = Slate50,
    secondary = Slate200,
    onSecondary = Slate900,
    background = Color(0xFF121417),
    onBackground = Slate50,
    surface = Color(0xFF121417),
    onSurface = Slate50,
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Color(0xFF1A1D21),
    surfaceContainerLow = Color(0xFF1A1D21),
    surfaceContainer = Color(0xFF1E2227),
    surfaceContainerHigh = Color(0xFF2A2F36),
    outline = Slate500,
    outlineVariant = Slate700,
    error = RiskHighDark,
)

/** Semantic colours for risk levels, kept out of the Material scheme so they stay consistent. */
@Immutable
data class RiskColors(
    val high: Color,
    val highContainer: Color,
    val medium: Color,
    val mediumContainer: Color,
    val low: Color,
    val lowContainer: Color,
)

private val LightRiskColors = RiskColors(
    RiskHigh, RiskHighContainer, RiskMedium, RiskMediumContainer, RiskLow, RiskLowContainer,
)
private val DarkRiskColors = RiskColors(
    RiskHighDark, RiskHighContainerDark, RiskMediumDark, RiskMediumContainerDark, RiskLowDark, RiskLowContainerDark,
)

val LocalRiskColors = staticCompositionLocalOf { LightRiskColors }

/**
 * App theme. Dynamic (wallpaper) colour is intentionally off so risk colours and the
 * lo-fi's neutral look stay the same on every device.
 */
@Composable
fun PermissionManagerAppUSSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRiskColors provides if (darkTheme) DarkRiskColors else LightRiskColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
