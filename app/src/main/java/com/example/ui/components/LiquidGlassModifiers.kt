package com.example.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import com.example.ui.theme.TheoSurfaceGlass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

val LocalTheoGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Remove hue from any caller-provided tint, so old screens cannot accidentally restore blue/purple glass. */
private fun Color.asNeutralGlass(): Color {
  val luminance = (red * 0.2126f + green * 0.7152f + blue * 0.0722f).coerceIn(0f, 1f)
  return Color(luminance, luminance, luminance, alpha)
}

/** Shared achromatic glass. High-value chrome uses a live backdrop; repeating rows use a lighter treatment. */
fun Modifier.liquidGlassSurface(
  shape: Shape,
  tint: Color = TheoSurfaceGlass,
  gloss: Float = 0.12f,
  refraction: Dp = 10.dp,
  blurRadius: Dp = 7.dp,
  showShadow: Boolean = true,
  opticalGlass: Boolean = false
): Modifier = composed {
  val backdrop = LocalTheoGlassBackdrop.current
  val neutralTint = tint.asNeutralGlass()
  val glassTint = neutralTint.copy(alpha = (neutralTint.alpha * 0.38f).coerceAtMost(0.16f))

  if (backdrop != null && opticalGlass) {
    drawBackdrop(
      backdrop = backdrop,
      shape = { shape },
      effects = {
        vibrancy()
        if (blurRadius > 0.dp) blur(blurRadius.toPx())
        lens(
          refractionHeight = refraction.toPx(),
          refractionAmount = (refraction * 1.35f).toPx(),
          depthEffect = true,
          chromaticAberration = false
        )
      },
      highlight = {
        // Keep the material optical rather than glossy. The refracted scene provides the visual
        // detail; this highlight is only the faint rim that defines the glass edge.
        Highlight.Default.copy(
          alpha = (0.055f + gloss.coerceIn(0f, 0.20f) * 0.20f).coerceAtMost(0.095f)
        )
      },
      shadow = if (showShadow) ({ Shadow(alpha = 0.055f) }) else null,
      onDrawSurface = {
        drawRect(glassTint)
        drawRect(
          Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = (0.012f + gloss * 0.075f).coerceAtMost(0.03f)),
              Color.Transparent,
              Color.Black.copy(alpha = 0.065f)
            )
          )
        )
      }
    )
  } else {
    drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      val path = when (outline) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
      }
      val fill = Brush.verticalGradient(
        0f to Color.White.copy(alpha = (0.012f + gloss * 0.09f).coerceAtMost(0.032f)),
        0.20f to glassTint,
        0.78f to glassTint.copy(alpha = glassTint.alpha * 0.62f),
        1f to Color.Black.copy(alpha = 0.08f)
      )
      val rim = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.105f),
          Color.White.copy(alpha = 0.045f),
          Color.White.copy(alpha = 0.018f),
          Color.White.copy(alpha = 0.050f)
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
      )
      onDrawWithContent {
        drawPath(path, brush = fill)
        drawContent()
        drawPath(path, brush = rim, style = Stroke(width = 0.72.dp.toPx()))
      }
    }
  }
}
