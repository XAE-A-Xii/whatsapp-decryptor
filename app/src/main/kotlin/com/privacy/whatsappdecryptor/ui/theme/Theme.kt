package com.privacy.whatsappdecryptor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF25D366), // WhatsApp vibrant green
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFF86F89B),
    secondary = Color(0xFF128C7E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0B141A), // Sleek WhatsApp dark background
    surface = Color(0xFF111B21),
    onSurface = Color(0xFFE9EDEF),
    surfaceVariant = Color(0xFF202C33),
    onSurfaceVariant = Color(0xFF8696A0),
    outline = Color(0xFF2A3942),
    error = Color(0xFFEF5350)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF008069),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8FDD2),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF025C4C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFEFEAE2),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111B21),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF667781),
    outline = Color(0xFFD1D7DB),
    error = Color(0xFFBA1A1A)
)

@Composable
fun WhatsAppDecryptorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
