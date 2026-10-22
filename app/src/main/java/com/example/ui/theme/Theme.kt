package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GroceryGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = GroceryGreenDark,
    secondary = GroceryAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = GroceryAmberContainer,
    onSecondaryContainer = Color(0xFF4E2600),
    tertiary = GroceryGold,
    onTertiary = Color.Black,
    background = GreenBackgroundLight,
    onBackground = Color(0xFF1B1D1A),
    surface = GreenSurfaceLight,
    onSurface = Color(0xFF1B1D1A),
    surfaceVariant = Color(0xFFE8ECE4),
    onSurfaceVariant = Color(0xFF3F4940),
    error = UdharRed,
    onError = Color.White,
    errorContainer = UdharRedContainer,
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = GroceryGreenPrimaryDark,
    onPrimary = Color(0xFF00390E),
    primaryContainer = GroceryGreenContainerDark,
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = GroceryAmberSecondaryDark,
    onSecondary = Color(0xFF451F00),
    secondaryContainer = Color(0xFF663300),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = GroceryGold,
    onTertiary = Color.Black,
    background = GreenBackgroundDark,
    onBackground = Color(0xFFE2E4DE),
    surface = GreenSurfaceDark,
    onSurface = Color(0xFFE2E4DE),
    surfaceVariant = GreenCardDark,
    onSurfaceVariant = Color(0xFFC2C9BD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun SmartGroceryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
