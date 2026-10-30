package com.fanwu.studyplanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, mono: Boolean = false) =
    TextStyle(
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
        fontWeight = weight, fontSize = size.sp, lineHeight = height.sp, letterSpacing = 0.sp
    )

/** 七列课表总览的紧凑排版；完整内容由课程详情及按天列表承载。 */
object TimetableTypography {
    val courseName = text(12, 16, FontWeight.Bold)
    val detail = text(10, 14, FontWeight.Medium, true)
}

val Typography = Typography(
    displayLarge = text(48, 56, FontWeight.Black, true),
    displayMedium = text(40, 48, FontWeight.Black, true),
    displaySmall = text(32, 40, FontWeight.Black, true),
    headlineLarge = text(36, 44, FontWeight.Black),
    headlineMedium = text(28, 36, FontWeight.Black),
    headlineSmall = text(24, 32, FontWeight.Black),
    titleLarge = text(20, 28, FontWeight.Black),
    titleMedium = text(18, 26, FontWeight.Bold),
    titleSmall = text(16, 24, FontWeight.Bold),
    bodyLarge = text(16, 24),
    bodyMedium = text(16, 24),
    bodySmall = text(14, 20),
    labelLarge = text(16, 24, FontWeight.ExtraBold),
    labelMedium = text(14, 20, FontWeight.Medium, true),
    labelSmall = text(12, 18, FontWeight.Medium, true)
)
