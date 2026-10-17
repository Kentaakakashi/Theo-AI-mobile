package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AnimationIntensity
import com.example.model.AssistantState
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoIceBlue
import com.example.ui.theme.TheoMagenta
import com.example.ui.theme.TheoViolet
import com.example.ui.theme.TheoWarningAmber

/**
 * Native Compose adaptation of VoiceOrbs' Glass Orb visual language.
 *
 * The sphere is built from layered translucent gradients, a refractive-looking rim,
 * a floating specular highlight and a flowing inner ribbon. State changes alter its
 * light and motion without turning the orb into a spiky HUD/reactor graphic.
 */
@Composable
fun TheoOrb(
  state: AssistantState,
  intensity: AnimationIntensity,
  modifier: Modifier = Modifier,
  sizeDp: Dp = 190.dp,
  onClick: () -> Unit = {}
) {
  val lowMotion = intensity == AnimationIntensity.BATTERY_SAVER
  val transition = rememberInfiniteTransition(label = "TheoGlassOrb")
  val rotation by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 5200
          AssistantState.LISTENING -> 7600
          AssistantState.SPEAKING -> 6400
          else -> 15000
        },
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart
    ),
    label = "GlassOrbRotation"
  )
  val breathing by transition.animateFloat(
    initialValue = 0.985f,
    targetValue = when (state) {
      AssistantState.THINKING -> 1.045f
      AssistantState.LISTENING -> 1.025f
      AssistantState.SPEAKING -> 1.04f
      else -> 1.012f
    },
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 900
          AssistantState.LISTENING -> 1250
          AssistantState.SPEAKING -> 700
          else -> 3200
        },
        easing = FastOutSlowInEasing
      ),
      repeatMode = RepeatMode.Reverse
    ),
    label = "GlassOrbBreathing"
  )
  val auraColor = when (state) {
    AssistantState.IDLE -> TheoIceBlue
    AssistantState.LISTENING -> Color(0xFF68DDFF)
    AssistantState.THINKING -> TheoViolet
    AssistantState.SPEAKING -> TheoCyan
    AssistantState.OFFLINE -> TheoWarningAmber
  }
  val ribbonColor = when (state) {
    AssistantState.IDLE -> TheoViolet
    AssistantState.LISTENING -> TheoCyan
    AssistantState.THINKING -> TheoMagenta
    AssistantState.SPEAKING -> TheoIceBlue
    AssistantState.OFFLINE -> Color(0xFF9A8390)
  }

  Box(
    modifier = modifier
      .size(sizeDp)
      .testTag("theo_central_orb")
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = sizeDp / 2),
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val outer = size.minDimension * 0.48f
      val r = size.minDimension * 0.355f
      val scale = if (lowMotion) 1f else breathing
      val sphereCenter = Offset(cx - r * 0.06f, cy - r * 0.02f)
      val sphereRadius = r * scale

      // Wide, soft aura floating behind the actual sphere.
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(auraColor.copy(alpha = if (state == AssistantState.OFFLINE) 0.13f else 0.22f), auraColor.copy(alpha = 0.06f), Color.Transparent),
          center = Offset(cx, cy),
          radius = outer * 1.25f
        ),
        radius = outer * 1.25f,
        center = Offset(cx, cy)
      )

      // Deep translucent body. The center remains almost black, like the reference.
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0xFF152D53), Color(0xFF091529), Color(0xFF030711), Color(0xFF010309)),
          center = Offset(sphereCenter.x - sphereRadius * 0.25f, sphereCenter.y - sphereRadius * 0.25f),
          radius = sphereRadius * 1.72f
        ),
        radius = sphereRadius,
        center = sphereCenter
      )

      // Colored caustic glints and transparent inner light.
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(ribbonColor.copy(alpha = 0.44f), auraColor.copy(alpha = 0.12f), Color.Transparent),
          center = Offset(cx + r * 0.28f, cy + r * 0.40f),
          radius = r * 0.95f
        ),
        radius = sphereRadius * 0.99f,
        center = sphereCenter
      )

      // Thin illuminated glass rim, with just one traveling bright segment.
      drawCircle(
        brush = Brush.sweepGradient(
          colors = listOf(
            Color.Transparent,
            auraColor.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.94f),
            auraColor.copy(alpha = 0.86f),
            ribbonColor.copy(alpha = 0.26f),
            Color.Transparent,
            Color.Transparent
          ),
          center = sphereCenter
        ),
        radius = sphereRadius,
        center = sphereCenter,
        style = Stroke(width = size.minDimension * 0.010f)
      )

      if (!lowMotion) {
        drawArc(
          brush = Brush.sweepGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.92f), auraColor.copy(alpha = 0.70f), Color.Transparent, Color.Transparent),
            center = sphereCenter
          ),
          startAngle = rotation,
          sweepAngle = 108f,
          useCenter = false,
          topLeft = Offset(sphereCenter.x - sphereRadius, sphereCenter.y - sphereRadius),
          size = Size(sphereRadius * 2f, sphereRadius * 2f),
          style = Stroke(width = size.minDimension * 0.014f)
        )
      }

      // The ribbon crossing the sphere is the signature shape of the selected Glass Orb.
      val ribbon = Path().apply {
        moveTo(cx - r * 0.82f, cy - r * 0.30f)
        cubicTo(cx - r * 0.25f, cy - r * 0.82f, cx + r * 0.48f, cy - r * 0.78f, cx + r * 0.64f, cy - r * 0.24f)
        cubicTo(cx + r * 0.81f, cy + r * 0.22f, cx + r * 0.20f, cy + r * 0.50f, cx - r * 0.42f, cy + r * 0.66f)
        cubicTo(cx + r * 0.10f, cy + r * 0.48f, cx + r * 0.70f, cy + r * 0.18f, cx + r * 0.40f, cy - r * 0.10f)
      }
      drawPath(
        path = ribbon,
        brush = Brush.linearGradient(
          colors = listOf(Color.Transparent, ribbonColor.copy(alpha = 0.18f), auraColor.copy(alpha = 0.93f), Color.White.copy(alpha = 0.96f), auraColor.copy(alpha = 0.35f), Color.Transparent),
          start = Offset(cx - r, cy - r * 0.6f),
          end = Offset(cx + r, cy + r * 0.6f)
        ),
        style = Stroke(width = size.minDimension * 0.018f)
      )
      // Fainter echo beneath the main ribbon adds depth without clutter.
      drawPath(
        path = ribbon,
        color = ribbonColor.copy(alpha = 0.13f),
        style = Stroke(width = size.minDimension * 0.046f)
      )

      // Floating softbox highlight on the upper-left quadrant.
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color.White.copy(alpha = 0.56f), Color.White.copy(alpha = 0.14f), Color.Transparent),
          center = Offset(cx - r * 0.47f, cy - r * 0.55f),
          radius = r * 0.42f
        ),
        radius = r * 0.36f,
        center = Offset(cx - r * 0.42f, cy - r * 0.50f)
      )
    }
  }
}
