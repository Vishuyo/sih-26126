package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WaynestWhite,
    onPrimary = WaynestBlack,
    primaryContainer = WaynestDarkGray,
    onPrimaryContainer = WaynestWhite,
    secondary = WaynestLightGray,
    onSecondary = WaynestBlack,
    background = WaynestBlack,
    surface = Color(0xFF161A20),
    onBackground = WaynestWhite,
    onSurface = WaynestWhite
)

private val LightColorScheme = lightColorScheme(
    primary = WaynestBlack,
    onPrimary = WaynestWhite,
    primaryContainer = WaynestLightGray,
    onPrimaryContainer = WaynestBlack,
    secondary = WaynestDarkGray,
    onSecondary = WaynestWhite,
    background = WaynestSurface,
    surface = WaynestWhite,
    onBackground = WaynestBlack,
    onSurface = WaynestBlack
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent Waynest visual styling
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
