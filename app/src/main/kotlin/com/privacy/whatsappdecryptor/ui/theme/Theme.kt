package com.privacy.whatsappdecryptor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 8-Point Grid System tokens according to mobile-app-ui-design specifications.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val huge = 64.dp
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF25D366), // 10% intentional vibrant green accent
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFF86F89B),
    secondary = Color(0xFF128C7E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1B3830),
    onSecondaryContainer = Color(0xFFA2E8D2),
    background = Color(0xFF0B141A), // 60% neutral deep base
    onBackground = Color(0xFFE9EDEF),
    surface = Color(0xFF111B21),
    onSurface = Color(0xFFE9EDEF),
    surfaceVariant = Color(0xFF202C33), // 30% structural cards/borders
    onSurfaceVariant = Color(0xFF8696A0),
    surfaceContainerLowest = Color(0xFF070D10),
    surfaceContainerLow = Color(0xFF0E171C),
    surfaceContainer = Color(0xFF142026),
    surfaceContainerHigh = Color(0xFF1D2A32),
    surfaceContainerHighest = Color(0xFF263740),
    outline = Color(0xFF2A3942),
    outlineVariant = Color(0xFF1A2830),
    error = Color(0xFFEF5350)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF008069), // 10% intentional emerald accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8FDD2),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF025C4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0F0E7),
    onSecondaryContainer = Color(0xFF002019),
    background = Color(0xFFF7F8F7), // 60% clean neutral base
    onBackground = Color(0xFF111B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111B21),
    surfaceVariant = Color(0xFFE9ECE8), // 30% structural cards/dividers
    onSurfaceVariant = Color(0xFF536471),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5F3),
    surfaceContainer = Color(0xFFECF0EB),
    surfaceContainerHigh = Color(0xFFE5EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DE),
    outline = Color(0xFFC4CDC7),
    outlineVariant = Color(0xFFD9E2DC),
    error = Color(0xFFBA1A1A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WhatsAppDecryptorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}
