package com.sohan.diutransportschedule.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = White,
    secondary = AccentGreen,
    onSecondary = White,

    background = AppBackgroundLight,
    onBackground = TextPrimaryLight,

    surface = CardSurfaceLight,
    onSurface = TextPrimaryLight,

    surfaceVariant = SoftBlue,
    onSurfaceVariant = TextSecondaryLight,

    outline = DeepBlue.copy(alpha = 0.18f),
    outlineVariant = DeepBlue.copy(alpha = 0.12f)
)

private val DarkColors = darkColorScheme(
    primary = DeepBlue,
    onPrimary = White,
    secondary = AccentGreen,
    onSecondary = White,

    background = AppBackgroundDark,
    onBackground = TextPrimaryDark,

    surface = CardSurfaceDark,
    onSurface = TextPrimaryDark,

    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,

    outline = White.copy(alpha = 0.22f),
    outlineVariant = White.copy(alpha = 0.14f)
)

@Composable
fun DIUTransportScheduleTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}