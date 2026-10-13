package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TheoColorScheme = darkColorScheme(
  primary = TheoCyan,
  onPrimary = TheoDarkBackground,
  primaryContainer = TheoDeepCyan,
  onPrimaryContainer = TheoTextPrimary,
  secondary = TheoViolet,
  onSecondary = TheoDarkBackground,
  secondaryContainer = TheoDeepViolet,
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
