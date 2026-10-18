package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val TheoShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(34.dp)
)

private val TheoColorScheme = darkColorScheme(
  primary = TheoCyan,
  onPrimary = TheoDarkBackground,
  primaryContainer = Color(0xFF29292C),
  onPrimaryContainer = TheoTextPrimary,
  secondary = TheoViolet,
  onSecondary = TheoDarkBackground,
  secondaryContainer = Color(0xFF222225),
  onSecondaryContainer = TheoTextPrimary,
  tertiary = TheoMagenta,
  onTertiary = TheoDarkBackground,
  background = TheoDarkBackground,
  onBackground = TheoTextPrimary,
  surface = TheoSurfaceDeep,
  onSurface = TheoTextPrimary,
  surfaceVariant = TheoSurfaceCard,
  onSurfaceVariant = TheoTextSecondary,
  outline = TheoSurfaceBorder
)

@Composable
fun TheoTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = TheoColorScheme,
    typography = TheoTypography,
    shapes = TheoShapes,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  TheoTheme(content = content)
}
