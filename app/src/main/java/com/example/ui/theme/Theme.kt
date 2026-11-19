package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val InkReelLightColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = StudioWhite,
    primaryContainer = StudioSurfaceMuted,
    onPrimaryContainer = InkBlack,
    secondary = StudioCrimson,
    onSecondary = StudioWhite,
    secondaryContainer = StudioCrimsonSoft,
    onSecondaryContainer = StudioCrimson,
    tertiary = InkCharcoal,
    onTertiary = StudioWhite,
    background = StudioPaper,
    onBackground = InkBlack,
    surface = StudioWhite,
    onSurface = InkBlack,
    surfaceVariant = StudioSurfaceMuted,
    onSurfaceVariant = SlateMuted,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = StudioCrimson,
    onError = StudioWhite,
    errorContainer = StudioCrimsonSoft,
    onErrorContainer = StudioCrimson
)

val InkReelShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun InkReelStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = InkReelLightColorScheme,
        typography = InkReelTypography,
        shapes = InkReelShapes,
        content = content
    )
}
