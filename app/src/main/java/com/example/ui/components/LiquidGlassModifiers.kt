package com.example.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.composed
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Screens draw a dedicated ambient backdrop behind their content. Panels sample this layer,
 * never their own subtree, so the lens can refract without feedback loops or recursive blur.
 */
val LocalTheoGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Shared liquid-glass treatment used by chat bubbles, settings cards, memory rows, controls,
 * status pills and other interface chrome. Android 13+ receives Backdrop's shader lens;
 * older versions retain the translucent tint and edge-light fallback.
 */
fun Modifier.liquidGlassSurface(
  shape: Shape,
  tint: Color = TheoSurfaceGlass,
  gloss: Float = 0.12f,
  refraction: Dp = 10.dp,
  blurRadius: Dp = 7.dp,
  showShadow: Boolean = true
): Modifier = composed {
  val backdrop = LocalTheoGlassBackdrop.current
  if (backdrop != null) {
    this.drawBackdrop(
      backdrop = backdrop,
      shape = { shape },
      effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(
          refractionHeight = refraction.toPx(),
          refractionAmount = (refraction * 1.25f).toPx(),
          depthEffect = true,
          chromaticAberration = false
        )
      },
      highlight = { Highlight.Default.copy(alpha = gloss.coerceIn(0f, 0.35f) + 0.55f) },
      shadow = if (showShadow) ({ Shadow.Default }) else null,
      onDrawSurface = {
        drawRect(tint)
        // A slim cool wash keeps glass surfaces legible over the deep ambient ground.
        drawRect(
          Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = gloss * 0.46f),
              Color.Transparent,
              Color(0xFF020610).copy(alpha = 0.18f)
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
        0f to Color.White.copy(alpha = gloss * 0.48f),
        0.20f to tint.copy(alpha = tint.alpha.coerceAtMost(0.82f)),
        0.78f to tint.copy(alpha = tint.alpha),
        1f to Color(0xFF050A13).copy(alpha = 0.82f)
      )
      val rim = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.30f),
          TheoCyan.copy(alpha = 0.18f),
          Color.White.copy(alpha = 0.05f),
          Color.White.copy(alpha = 0.12f)
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
      )
      onDrawWithContent {
        drawPath(path, brush = fill)
        drawContent()
        drawPath(path, brush = rim, style = Stroke(width = 0.8.dp.toPx()))
      }
    }
  }
}
