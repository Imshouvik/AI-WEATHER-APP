package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom dark scheme representing Sleek Interface theme
private val SleekDarkColorScheme = darkColorScheme(
    primary = SleekAccent,
    onPrimary = Color(0xFF001D4D),
    primaryContainer = SleekCardBg,
    onPrimaryContainer = SleekTextPrimary,
    secondary = SleekTextSecondary,
    onSecondary = Color(0xFF0F1113),
    tertiary = SleekTextMuted,
    background = SleekBackground,
    surface = SleekCardBg,
    onBackground = SleekTextPrimary,
    onSurface = SleekTextPrimary,
    surfaceVariant = SleekCardBgEnd,
    onSurfaceVariant = SleekTextSecondary,
    outline = SleekBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for Sleek Interface UI
    dynamicColor: Boolean = false, // Disable dynamic colors so our Sleek color scheme is consistently displayed
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SleekDarkColorScheme
        else -> SleekDarkColorScheme // Fallback to Sleek dark theme as it represents the main aesthetic
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
