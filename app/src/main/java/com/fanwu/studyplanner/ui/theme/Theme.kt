package com.fanwu.studyplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val BrutalColors = lightColorScheme(
    primary = BrutalPink, onPrimary = BrutalBlack,
    primaryContainer = BrutalPink, onPrimaryContainer = BrutalBlack,
    secondary = BrutalCyan, onSecondary = BrutalBlack,
    secondaryContainer = BrutalCyan, onSecondaryContainer = BrutalBlack,
    tertiary = BrutalLime, onTertiary = BrutalBlack,
    tertiaryContainer = BrutalLime, onTertiaryContainer = BrutalBlack,
    background = BrutalPaper, onBackground = BrutalBlack,
    surface = BrutalWhite, onSurface = BrutalBlack,
    surfaceVariant = BrutalDisabled, onSurfaceVariant = BrutalMutedText,
    outline = BrutalBlack, outlineVariant = BrutalBlack,
    error = BrutalDanger, onError = BrutalWhite,
    errorContainer = BrutalWhite, onErrorContainer = BrutalDanger,
    inverseSurface = BrutalBlack, inverseOnSurface = BrutalWhite,
    surfaceTint = BrutalWhite
)

/** 固定浅色，避免系统取色覆盖新野兽派的语义色。 */
@Composable
fun StudyPlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrutalColors,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp), large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp)
        ),
        content = content
    )
}
