package com.empowerswr.test.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import android.util.Log

private val DarkColorScheme = darkColorScheme(
    primary = Purple500,
    onPrimary = White,
    error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Purple500,
    onPrimary = White,
    error = Error
)

@Composable
fun EmpowerSWRTheme(
    darkTheme: Boolean = false, // Disable system dark theme for testing
    content: @Composable () -> Unit
) {
    Log.d("EmpowerSWR", "Applying EmpowerSWRTheme, darkTheme: $darkTheme")
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}