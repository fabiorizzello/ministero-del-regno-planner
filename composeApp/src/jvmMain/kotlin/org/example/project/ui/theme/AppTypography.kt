package org.example.project.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val BASE_FONT_SP = 13f
private const val GLOBAL_TEXT_SCALE = 1.0f
private const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.24f

private fun fontSize(multiplier: Float) = (BASE_FONT_SP * GLOBAL_TEXT_SCALE * multiplier).sp

private fun lineHeight(multiplier: Float) =
    (BASE_FONT_SP * GLOBAL_TEXT_SCALE * multiplier * DEFAULT_LINE_HEIGHT_MULTIPLIER).sp

private fun tracking(value: Float) = value.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = fontSize(4.0f),
        lineHeight = lineHeight(4.0f),
        fontWeight = FontWeight.Normal,
    ),
    displayMedium = TextStyle(
        fontSize = fontSize(3.6f),
        lineHeight = lineHeight(3.6f),
        fontWeight = FontWeight.Normal,
    ),
    displaySmall = TextStyle(
        fontSize = fontSize(3.2f),
        lineHeight = lineHeight(3.2f),
        fontWeight = FontWeight.Normal,
    ),
    headlineLarge = TextStyle(
        fontSize = fontSize(2.4f),
        lineHeight = lineHeight(2.4f),
        fontWeight = FontWeight.Bold,
        letterSpacing = tracking(-0.2f),
    ),
    headlineMedium = TextStyle(
        fontSize = fontSize(2.05f),
        lineHeight = lineHeight(2.05f),
        fontWeight = FontWeight.Bold,
        letterSpacing = tracking(-0.15f),
    ),
    headlineSmall = TextStyle(
        fontSize = fontSize(1.72f),
        lineHeight = lineHeight(1.72f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(-0.1f),
    ),
    titleLarge = TextStyle(
        fontSize = fontSize(1.62f),
        lineHeight = lineHeight(1.62f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(-0.05f),
    ),
    titleMedium = TextStyle(
        fontSize = fontSize(1.28f),
        lineHeight = lineHeight(1.28f),
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = fontSize(1.14f),
        lineHeight = lineHeight(1.14f),
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = fontSize(1.14f),
        lineHeight = lineHeight(1.14f),
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = fontSize(1.0f),
        lineHeight = lineHeight(1.0f),
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = fontSize(0.92f),
        lineHeight = lineHeight(0.92f),
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = fontSize(1.0f),
        lineHeight = lineHeight(1.0f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(0.05f),
    ),
    labelMedium = TextStyle(
        fontSize = fontSize(0.92f),
        lineHeight = lineHeight(0.92f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(0.12f),
    ),
    labelSmall = TextStyle(
        fontSize = fontSize(0.84f),
        lineHeight = lineHeight(0.84f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(0.18f),
    ),
)
