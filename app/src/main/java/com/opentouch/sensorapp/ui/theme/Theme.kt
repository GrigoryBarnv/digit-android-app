package com.opentouch.sensorapp.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    secondary = NeutralGreyDark,
    tertiary = MutedGreyDark,
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF2C2D33),
    surfaceVariant = Color(0xFF3D3D3D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    secondary = NeutralGreyLight,
    tertiary = MutedGreyLight
)

@Composable
fun DigitappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You) pulls its palette from the device wallpaper on
    // Android 12+. Disabled by default so unstyled components (switches, default
    // sliders, ripples) stay on our neutral+violet palette instead of drifting
    // with whatever color the user's wallpaper happens to be.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}