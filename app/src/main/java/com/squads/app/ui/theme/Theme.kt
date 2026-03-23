package com.squads.app.ui.theme

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

private val SquadsPurple = Color(0xFF6264A7) // Teams-inspired accent

private val DarkColorScheme = darkColorScheme(
    primary = SquadsPurple,
    secondary = Color(0xFF8B8CC7),
    tertiary = Color(0xFF4DB8FF),
)

private val LightColorScheme = lightColorScheme(
    primary = SquadsPurple,
    secondary = Color(0xFF5B5FC7),
    tertiary = Color(0xFF0078D4),
)

@Composable
fun SquadsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You on Android 12+
    content: @Composable () -> Unit,
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
        typography = SquadsTypography,
        content = content,
    )
}
