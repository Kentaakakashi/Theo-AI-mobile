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
import com.example.ui.theme.TheoSurfaceGlass

/**
 * Native Compose liquid-glass-like surface for app panels and controls.
 *
 * The material combines a dark translucent body, a soft top reflection, a quiet chromatic rim
 * and a lower edge shadow. It deliberately avoids expensive blur-on-every-card effects so long
 * lists and automation screens remain smooth on mid-range phones.
 *
 * This is a native fallback material: it does not claim true backdrop refraction.
 */
fun Modifier.liquidGlassSurface(
  shape: Shape,
  tint: Color = TheoSurfaceGlass,
  rimWidth: Dp = 0.8.dp,
  gloss: Float = 0.10f
): Modifier = drawWithCache {
  val outline = shape.createOutline(size, layoutDirection, this)
  val path = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
  }
  val fill = Brush.verticalGradient(
    0f to Color.White.copy(alpha = gloss * 0.48f),
    0.18f to tint.copy(alpha = tint.alpha.coerceAtMost(0.72f)),
    0.68f to tint.copy(alpha = tint.alpha),
    1f to Color(0xFF050A13).copy(alpha = 0.86f)
  )
  val rim = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.34f),
      TheoCyan.copy(alpha = 0.22f),
      Color.White.copy(alpha = 0.06f),
      Color.White.copy(alpha = 0.14f)
    ),
    start = Offset(0f, 0f),
    end = Offset(size.width, size.height)
  )
  val widthPx = rimWidth.toPx().coerceAtLeast(0.5f)

  onDrawWithContent {
    drawPath(path, brush = fill)
    drawContent()
    drawPath(path, brush = rim, style = Stroke(width = widthPx))
    // A short softbox reflection near the top edge gives the panel a glass catchlight.
    if (size.width > widthPx * 4f && size.height > widthPx * 4f) {
      drawLine(
        color = Color.White.copy(alpha = gloss * 0.8f),
        start = Offset(size.width * 0.22f, widthPx),
        end = Offset(size.width * 0.78f, widthPx),
        strokeWidth = widthPx
      )
    }
  }
}
