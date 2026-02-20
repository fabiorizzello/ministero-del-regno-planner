package org.example.project.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val BASE_FONT_SP = 14f
private const val GLOBAL_TEXT_SCALE = 1.1f
private const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.35f

private fun fontSize(multiplier: Float) = (BASE_FONT_SP * GLOBAL_TEXT_SCALE * multiplier).sp

private fun lineHeight(multiplier: Float) =
    (BASE_FONT_SP * GLOBAL_TEXT_SCALE * multiplier * DEFAULT_LINE_HEIGHT_MULTIPLIER).sp

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
        fontSize = fontSize(2.3f),
        lineHeight = lineHeight(2.3f),
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontSize = fontSize(2.0f),
        lineHeight = lineHeight(2.0f),
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = fontSize(1.7f),
        lineHeight = lineHeight(1.7f),
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = fontSize(1.57f),
        lineHeight = lineHeight(1.57f),
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = fontSize(0.92f),
        lineHeight = lineHeight(0.92f),
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = fontSize(0.84f),
        lineHeight = lineHeight(0.84f),
        fontWeight = FontWeight.Medium,
    ),
)
