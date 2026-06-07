package com.vvad.vp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
        primary = DarkColorSchemePrimary,
        onPrimary = Color.White,
        primaryContainer = DarkColorSchemePrimary,
        onPrimaryContainer = Color.White,
        secondary = DarkColorSchemeSecondary,
        onSecondary = Color.White,
        secondaryContainer = DarkColorSchemeSecondary.copy(alpha = 0.3f),
        onSecondaryContainer = Color.White,
        tertiary = DarkColorSchemeTertiary,
        onTertiary = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB0B0B0),
        error = Color(0xFFCF6679),
        onError = Color.Black,
        outline = Color(0xFF8A8A8A)
)

private val LightColorScheme = lightColorScheme(
        primary = DarkColorSchemePrimary,
        onPrimary = Color.White,
        primaryContainer = DarkColorSchemePrimary,
        onPrimaryContainer = Color.White,
        secondary = DarkColorSchemeSecondary,
        onSecondary = Color.White,
        tertiary = DarkColorSchemeTertiary,
        onTertiary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE0E0E0),
        onSurfaceVariant = Color(0xFF616161),
        error = Color(0xFFB00020),
        onError = Color.White,
        outline = Color(0xFFBDBDBD)
)

@Composable
fun VVADPlayerTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        // Dynamic color is available on Android 12+
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.BLACK
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }


    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
    )
}