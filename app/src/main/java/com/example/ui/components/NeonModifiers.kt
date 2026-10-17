package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoViolet

/**
 * Compatibility API for existing screens. Glass surfaces now paint their own optical rim,
 * so this modifier intentionally adds no second border.
 */
fun Modifier.neonBorder(
  shape: Shape,
  colors: List<Color> = listOf(TheoCyan, TheoViolet),
  strokeWidth: Dp = 0.8.dp
): Modifier = this

/** The former HUD scanline texture is intentionally removed from the new visual language. */
fun Modifier.hudScanlines(
  spacing: Dp = 5.dp,
  color: Color = TheoCyan.copy(alpha = 0.035f)
): Modifier = this
