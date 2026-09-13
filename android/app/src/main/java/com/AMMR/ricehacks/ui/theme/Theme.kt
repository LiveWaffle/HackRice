package com.AMMR.ricehacks.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Obsidian,
    onPrimary = PaperWhite,
    secondary = SurgicalBlue,
    onSecondary = PaperWhite,
    tertiary = SkyTint,
    background = Obsidian,
    onBackground = PaperWhite,
    surface = Charcoal,
    onSurface = PaperWhite,
    surfaceVariant = CloudGray,
    onSurfaceVariant = PaperWhite,
    outline = Slate,
    outlineVariant = Slate
)

private val LightColorScheme = lightColorScheme(
    primary = Obsidian,
    onPrimary = PaperWhite,
    secondary = SurgicalBlue,
    onSecondary = PaperWhite,
    tertiary = SkyTint,
    background = PaperWhite,
    onBackground = Obsidian,
    surface = PaperWhite,
    onSurface = Obsidian,
    surfaceVariant = CloudGray,
    onSurfaceVariant = Charcoal,
    outline = Slate,
    outlineVariant = Slate,
    inverseOnSurface = PaperWhite,
    inverseSurface = Obsidian
)

val LettersSkyGradient = Brush.verticalGradient(
    colors = listOf(
        SkyGradientStart,
        SkyGradientMiddle,
        SkyGradientEnd
    )
)

@Composable
fun RiceHacksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LettersTypography,
        shapes = LettersShapes,
        content = content
    )
}
