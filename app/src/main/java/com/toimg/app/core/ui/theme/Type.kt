package com.toimg.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val Face = FontFamily.Default

// 中文排版：行高约 1.5 倍、字距略放开；标题半粗、正文常规、辅助文字偏小但不低于 11sp
private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0.2f) = TextStyle(
    fontFamily = Face,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

val ToImgTypography = Typography(
    headlineLarge = style(30, 40, FontWeight.Bold, 0f),
    headlineMedium = style(26, 36, FontWeight.Bold, 0f),
    headlineSmall = style(22, 30, FontWeight.SemiBold, 0f),
    titleLarge = style(20, 28, FontWeight.SemiBold, 0f),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1f),
    titleSmall = style(14, 21, FontWeight.SemiBold, 0.1f),
    bodyLarge = style(16, 25, FontWeight.Normal, 0.2f),
    bodyMedium = style(14, 22, FontWeight.Normal, 0.2f),
    bodySmall = style(12, 18, FontWeight.Normal, 0.2f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.2f),
    labelMedium = style(12, 17, FontWeight.Medium, 0.3f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.3f),
)
