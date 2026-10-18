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
 * Static ambient scene used by the shared glass controls. The live navigation dock uses the
 * BuildItCode capture layer; repeating content rows deliberately use the inexpensive surface tier.
 */
val LocalTheoGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Shared glass material.
 *
 * opticalGlass should be reserved for a small number of high-value chrome surfaces (header and
 * composer). Rows, bubbles and settings options use the lighter Compose tier so a long chat or
 * settings list does not stack a GPU blur/lens effect on every single item.
 */
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
  // Existing screens used opaque tints. Reduce tint density centrally so all screens share a
  // translucent material rather than a collection of dark, boxed-in cards.
  val glassTint = tint.copy(alpha = (tint.alpha * 0.58f).coerceAtMost(0.36f))

  if (backdrop != null && opticalGlass) {
    drawBackdrop(
      backdrop = backdrop,
      shape = { shape },
      effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(
          refractionHeight = refraction.toPx(),
          refractionAmount = (refraction * 1.15f).toPx(),
          depthEffect = true,
          chromaticAberration = false
        )
      },
      highlight = {
        Highlight.Default.copy(alpha = (gloss.coerceIn(0f, 0.28f) + 0.28f).coerceAtMost(0.48f))
      },
      shadow = if (showShadow) ({ Shadow.Default }) else null,
      onDrawSurface = {
        drawRect(glassTint)
        drawRect(
          Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = (gloss * 0.62f).coerceAtMost(0.10f)),
              Color.Transparent,
              Color(0xFF020610).copy(alpha = 0.08f)
            )
          )
        )
      }
    )
  } else {
    // Fast translucent tier for repeating bubbles, rows and cards.
    drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      val path = when (outline) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
      }
      val fill = Brush.verticalGradient(
        0f to Color.White.copy(alpha = (gloss * 0.42f).coerceAtMost(0.09f)),
        0.18f to glassTint.copy(alpha = (glassTint.alpha * 0.72f).coerceAtMost(0.28f)),
        0.76f to glassTint,
        1f to Color(0xFF050A13).copy(alpha = 0.20f)
      )
      val rim = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.18f),
          TheoCyan.copy(alpha = 0.10f),
          Color.White.copy(alpha = 0.035f),
          Color.White.copy(alpha = 0.075f)
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
      )
      onDrawWithContent {
        drawPath(path, brush = fill)
        drawContent()
        drawPath(path, brush = rim, style = Stroke(width = 0.65.dp.toPx()))
      }
    }
  }
}
