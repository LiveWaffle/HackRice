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
    primary = Verdigris,
    onPrimary = PaperWhite,
    primaryContainer = IronGrey,
    onPrimaryContainer = PaperWhite,
    secondary = VibrantCoral,
    onSecondary = DeepMocha,
    secondaryContainer = DeepMocha,
    onSecondaryContainer = PaperWhite,
    tertiary = SoftVerdigris,
    onTertiary = DeepMocha,
    background = DeepMocha,
    onBackground = PaperWhite,
    surface = IronGrey,
    onSurface = PaperWhite,
    surfaceContainer = Color(0xFF5C6666),
    surfaceContainerHigh = Color(0xFF667070),
    surfaceVariant = IronGrey,
    onSurfaceVariant = PaperWhite,
    outline = SoftVerdigris,
    outlineVariant = Color(0xFF708080),
    error = VibrantCoral,
    onError = DeepMocha,
    errorContainer = DeepMocha,
    onErrorContainer = SoftCoral
)

private val LightColorScheme = lightColorScheme(
    primary = Verdigris,
    onPrimary = PaperWhite,
    primaryContainer = SoftVerdigris,
    onPrimaryContainer = DeepMocha,
    secondary = VibrantCoral,
    onSecondary = DeepMocha,
    secondaryContainer = SoftCoral,
    onSecondaryContainer = DeepMocha,
    tertiary = IronGrey,
    onTertiary = PaperWhite,
    background = PaperWhite,
    onBackground = DeepMocha,
    surface = PaperWhite,
    onSurface = DeepMocha,
    surfaceContainer = PaleGrey,
    surfaceContainerHigh = SoftVerdigris,
    surfaceVariant = PaleGrey,
    onSurfaceVariant = IronGrey,
    outline = IronGrey,
    outlineVariant = SoftVerdigris,
    error = VibrantCoral,
    onError = DeepMocha,
    errorContainer = SoftCoral,
    onErrorContainer = DeepMocha,
    inverseOnSurface = PaperWhite,
    inverseSurface = DeepMocha
)

val RelayGradient = Brush.verticalGradient(
    colors = listOf(
        RelayGradientStart,
        RelayGradientMiddle,
        RelayGradientEnd
    )
)

val LettersSkyGradient = RelayGradient

@Composable
fun RelayTheme(
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

@Composable
fun RiceHacksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    RelayTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
