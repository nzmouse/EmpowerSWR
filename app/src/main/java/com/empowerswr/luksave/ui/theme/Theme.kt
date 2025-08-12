package com.empowerswr.luksave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE), // Customize as needed
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun EmpowerSWRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = MaterialTheme.typography, // Customize if needed
        content = content
    )
}