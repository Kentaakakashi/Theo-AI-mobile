package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoViolet

/**
 * Draws a lightweight multi-pass gradient border with a subtle neon halo.
 *
 * This is deliberately implemented with Compose drawing primitives only, so it
 * doesn't need platform Canvas effects or any additional dependencies.
 */
fun Modifier.neonBorder(
  shape: Shape,
  colors: List<Color> = listOf(TheoCyan, TheoViolet, TheoCyan),
  strokeWidth: Dp = 1.dp
): Modifier = drawWithCache {
  val outline = shape.createOutline(size, layoutDirection, this)
  val gradientColors = colors.ifEmpty { listOf(TheoCyan, TheoViolet) }
  val widthPx = strokeWidth.toPx().coerceAtLeast(0.5f)
  val haloBrush = Brush.sweepGradient(gradientColors.map { it.copy(alpha = it.alpha * 0.10f) })
  val glowBrush = Brush.sweepGradient(gradientColors.map { it.copy(alpha = it.alpha * 0.24f) })
  val borderBrush = Brush.sweepGradient(gradientColors)

  onDrawWithContent {
    drawContent()
    drawOutline(outline, brush = haloBrush, style = Stroke(width = widthPx * 4.0f))
    drawOutline(outline, brush = glowBrush, style = Stroke(width = widthPx * 2.1f))
    drawOutline(outline, brush = borderBrush, style = Stroke(width = widthPx))
  }
}

/**
 * Adds a faint, static HUD scanline texture over a component's content.
 * Lines are spaced to keep the effect inexpensive on lower-end devices.
 */
fun Modifier.hudScanlines(
  spacing: Dp = 5.dp,
  color: Color = TheoCyan.copy(alpha = 0.035f)
): Modifier = drawWithCache {
  val stepPx = spacing.toPx().coerceAtLeast(2f)
  val lineWidthPx = 0.5.dp.toPx()

  onDrawWithContent {
    drawContent()
    var y = 0f
    while (y < size.height) {
      drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = lineWidthPx
      )
      y += stepPx
    }
  }
}
