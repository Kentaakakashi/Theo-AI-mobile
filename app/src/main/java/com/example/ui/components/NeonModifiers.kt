package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoViolet

/**
 * Compatibility name retained for existing call sites.
 *
 * The former multi-pass neon halo made glass surfaces look like outlined sci-fi cards.
 * This now draws one quiet, cool hairline with a restrained white/cyan highlight.
 */
fun Modifier.neonBorder(
  shape: Shape,
  colors: List<Color> = listOf(TheoCyan, Color(0x99FFFFFF), TheoViolet),
  strokeWidth: Dp = 0.8.dp
): Modifier = drawWithCache {
  val outline = shape.createOutline(size, layoutDirection, this)
  val borderPath = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
  }
  val palette = colors.ifEmpty { listOf(Color.White.copy(alpha = 0.25f), TheoCyan.copy(alpha = 0.4f)) }
  val brush = Brush.sweepGradient(palette.map { it.copy(alpha = it.alpha * 0.58f) })
  val widthPx = strokeWidth.toPx().coerceAtLeast(0.5f)

  onDrawWithContent {
    drawContent()
    // A subtle top-lit edge rather than a three-pass glow.
    drawPath(
      borderPath,
      brush = brush,
      style = Stroke(width = widthPx)
    )
    drawLine(
      color = Color.White.copy(alpha = 0.10f),
      start = Offset(size.width * 0.22f, widthPx / 2f),
      end = Offset(size.width * 0.78f, widthPx / 2f),
      strokeWidth = widthPx
    )
  }
}

/**
 * The old repeating scanline overlay fought the glossy material and reduced text clarity.
 * Keep the API for existing screens, but deliberately remove the texture in the glass redesign.
 */
fun Modifier.hudScanlines(
  spacing: Dp = 5.dp,
  color: Color = TheoCyan.copy(alpha = 0.035f)
): Modifier = this
