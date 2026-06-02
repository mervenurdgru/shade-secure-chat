package com.shade.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    primaryContainer = SoftPurple,
    onPrimaryContainer = LightPurple,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = BubbleOther,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanAccent,
    onTertiary = Color.Black,
    background = RichBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SurfaceContainer,
    outline = OutlineMuted,
    outlineVariant = DividerDark,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = Color(0xFF2D0660),
    secondary = SoftPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = DarkBlack,
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = Color(0xFFFDF8FF),
    onBackground = DarkBlack,
    surface = Color.White,
    onSurface = DarkBlack,
    surfaceVariant = Color(0xFFEDE7F3),
    onSurfaceVariant = Color(0xFF4A4458),
    surfaceContainerHigh = Color(0xFFF3EDFA),
    outline = Color(0xFFCCC2DC),
    outlineVariant = Color(0xFFE0D8EA),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun ShadeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
