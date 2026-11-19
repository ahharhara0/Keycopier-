package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppThemeMode

private val KeyCopierLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = BrassGold,
    onTertiary = Color(0xFF1E1B18),
    background = LightBackground,
    onBackground = LightMainText,
    surface = LightSurface,
    onSurface = LightMainText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusError,
    onError = Color.White
)

private val KeyCopierDarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = SecondaryTealDark,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = BrassHighlight,
    onTertiary = Color(0xFF1E1B18),
    background = DarkBackground,
    onBackground = DarkMainText,
    surface = DarkSurface,
    onSurface = DarkMainText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFEF4444),
    onError = Color.White
)

val KeyCopierShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun KeyCopierTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (isDark) KeyCopierDarkColorScheme else KeyCopierLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KeyCopierTypography,
        shapes = KeyCopierShapes,
        content = content
    )
}
