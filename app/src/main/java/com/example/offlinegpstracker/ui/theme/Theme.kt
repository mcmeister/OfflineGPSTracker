package com.example.offlinegpstracker.ui.theme

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

private val TransparentDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,

    // Force transparent backgrounds:
    background = Color.Transparent,
    surface = Color.Transparent
)

private val TransparentLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    // Force transparent backgrounds:
    background = Color.Transparent,
    surface = Color.Transparent
)

@Composable
fun OfflineGPSTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // If dynamic color is desired and available, it may override these defaults,
    // so be aware you might lose transparency if the system picks non-transparent tones.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> TransparentDarkColorScheme
        else -> TransparentLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Type,
        content = content
    )
}