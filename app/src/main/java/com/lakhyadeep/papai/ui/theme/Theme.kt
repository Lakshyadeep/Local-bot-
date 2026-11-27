package com.lakhyadeep.papai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = OnTealPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = ColorTokens.LightSurfaceLow,
    surfaceContainer = ColorTokens.LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = ColorTokens.DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private object ColorTokens {
    val LightSurfaceLow = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
    val LightSurfaceContainer = androidx.compose.ui.graphics.Color(0xFFF1F5F9)
    val DarkSurfaceContainer = androidx.compose.ui.graphics.Color(0xFF162033)
}

val PapiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun PapiAiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PapiTypography,
        shapes = PapiShapes,
        content = content
    )
}
