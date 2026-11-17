package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WaddleLightColorScheme = lightColorScheme(
    primary = TangerinePrimary,
    onPrimary = CrispWhite,
    primaryContainer = TangerineSoft,
    onPrimaryContainer = TangerineDark,
    secondary = ForestMoss,
    onSecondary = CrispWhite,
    secondaryContainer = MintGlow,
    onSecondaryContainer = ForestMoss,
    tertiary = BerryAccent,
    onTertiary = CrispWhite,
    tertiaryContainer = BerrySoft,
    onTertiaryContainer = BerryAccent,
    background = WarmCream,
    onBackground = DeepPlumInk,
    surface = CrispWhite,
    onSurface = DeepPlumInk,
    surfaceVariant = SoftPeachSurface,
    onSurfaceVariant = MutedPlum,
    outline = SoftBorder,
    error = Color(0xFFD92D20),
    onError = CrispWhite
)

private val WaddleDarkColorScheme = darkColorScheme(
    primary = TangerineGlow,
    onPrimary = MidnightPlum,
    primaryContainer = Color(0xFF4A1D0E),
    onPrimaryContainer = TangerineSoft,
    secondary = LimeHighlight,
    onSecondary = MidnightPlum,
    secondaryContainer = Color(0xFF283E10),
    onSecondaryContainer = MintGlow,
    tertiary = Color(0xFFB794F4),
    onTertiary = MidnightPlum,
    tertiaryContainer = Color(0xFF372266),
    onTertiaryContainer = BerrySoft,
    background = MidnightPlum,
    onBackground = LightCreamInk,
    surface = DarkSurfaceCard,
    onSurface = LightCreamInk,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = MutedLavenderInk,
    outline = DarkBorder,
    error = Color(0xFFF97066),
    onError = MidnightPlum
)

val WaddleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WaddleUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) WaddleDarkColorScheme else WaddleLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaddleTypography,
        shapes = WaddleShapes,
        content = content
    )
}
