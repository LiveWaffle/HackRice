package com.AMMR.ricehacks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Open Runde / Inter are web-first fonts; Android doesn't include them by default.
// No font files exist in res/font, so we use the available Android system stack here.
// To match the spec exactly on-device, add bundled .ttf/.otf files under
// app/src/main/res/font and swap the font families below to those resources.
private val LettersSans = FontFamily.SansSerif

private val DisplayText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.8).sp
)

private val HeadingText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp
)

private val TitleText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.3).sp
)

private val BodyText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp
)

private val BodySmallText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.1).sp
)

private val LabelText = TextStyle(
    fontFamily = LettersSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

val LettersTypography = Typography(
    displayLarge = DisplayText,
    displayMedium = DisplayText,
    headlineLarge = HeadingText,
    headlineMedium = HeadingText,
    headlineSmall = TitleText,
    titleLarge = TitleText,
    titleMedium = TitleText,
    titleSmall = TitleText,
    bodyLarge = BodyText,
    bodyMedium = BodyText,
    bodySmall = BodySmallText,
    labelLarge = LabelText,
    labelMedium = LabelText,
    labelSmall = LabelText
)