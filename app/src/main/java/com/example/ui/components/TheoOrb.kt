package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AnimationIntensity
import com.example.model.AssistantState
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoIceBlue
import com.example.ui.theme.TheoMagenta
import com.example.ui.theme.TheoOnlineGreen
import com.example.ui.theme.TheoViolet
import com.example.ui.theme.TheoWarningAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TheoOrb(
  state: AssistantState,
  intensity: AnimationIntensity,
  modifier: Modifier = Modifier,
  sizeDp: Dp = 190.dp,
  onClick: () -> Unit = {}
) {
  val isFullAnim = intensity != AnimationIntensity.BATTERY_SAVER
  val isHighAnim = intensity == AnimationIntensity.HIGH

  val infiniteTransition = rememberInfiniteTransition(label = "TheoOrbTransition")

  // Ambient breathing scale
  val breathScale by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = if (state == AssistantState.THINKING) 1.08f else if (state == AssistantState.SPEAKING) 1.06f else 1.02f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 700
          AssistantState.SPEAKING -> 550
          AssistantState.LISTENING -> 1000
          else -> 2400
        },
        easing = FastOutSlowInEasing
      ),
      repeatMode = RepeatMode.Reverse
    ),
    label = "BreathScale"
  )

  // Outer orbital rotation angle
  val ringRotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 3500
          AssistantState.SPEAKING -> 5000
          else -> 12000
        },
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart
    ),
    label = "RingRotation"
  )

  // Reverse secondary gyro rotation
  val innerRingRotation by infiniteTransition.animateFloat(
    initialValue = 360f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 4000
          else -> 16000
        },
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart
    ),
    label = "InnerRingRotation"
  )

  // Speaking sound-wave ripple radius
  val wavePulse by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "WavePulse"
  )

  // Determine state-based glow colors
  val primaryColor = when (state) {
    AssistantState.IDLE -> TheoCyan
    AssistantState.LISTENING -> TheoIceBlue
    AssistantState.THINKING -> TheoMagenta
    AssistantState.SPEAKING -> TheoCyan
    AssistantState.OFFLINE -> TheoWarningAmber
  }

  val secondaryColor = when (state) {
    AssistantState.IDLE -> TheoViolet
    AssistantState.LISTENING -> TheoCyan
    AssistantState.THINKING -> TheoViolet
    AssistantState.SPEAKING -> TheoMagenta
    AssistantState.OFFLINE -> Color(0xFF6B7280)
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
    // Background dynamic aura glow
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasCenter = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = size.width / 2f

      // Solar flare spokes: varied lengths create the radial reactor silhouette.
      if (isFullAnim) {
        for (i in 0 until 32) {
          val angle = (i * (2.0 * PI / 32.0) + ringRotation * PI / 180.0).toFloat()
          val innerRadius = maxRadius * (0.19f + (i % 4) * 0.025f)
          val outerRadius = maxRadius * (0.69f + ((i * 7) % 6) * 0.045f)
          val start = Offset(
            canvasCenter.x + cos(angle) * innerRadius,
            canvasCenter.y + sin(angle) * innerRadius
          )
          val end = Offset(
            canvasCenter.x + cos(angle) * outerRadius,
            canvasCenter.y + sin(angle) * outerRadius
          )
          drawLine(
            color = primaryColor.copy(alpha = if (state == AssistantState.THINKING) 0.72f else 0.42f),
            start = start,
            end = end,
            strokeWidth = if (i % 4 == 0) 1.8.dp.toPx() else 1.dp.toPx()
          )
        }

        // Stable orbit particles (deterministic positions, no per-frame random allocations).
        for (i in 0 until 96) {
          val angle = (i * 2.39996f + ringRotation * PI.toFloat() / 180f).toFloat()
          val radial = maxRadius * (0.56f + ((i * 13) % 41) / 100f)
          val dot = Offset(
            canvasCenter.x + cos(angle) * radial,
            canvasCenter.y + sin(angle) * radial
          )
          drawCircle(
            color = primaryColor.copy(alpha = 0.18f + (i % 5) * 0.07f),
            radius = if (i % 9 == 0) 1.6.dp.toPx() else 0.8.dp.toPx(),
            center = dot
          )
        }
      }

      // Multi-stop radial atmospheric aura
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            primaryColor.copy(alpha = if (state == AssistantState.THINKING) 0.45f else 0.28f),
            secondaryColor.copy(alpha = if (state == AssistantState.SPEAKING) 0.30f else 0.15f),
            Color.Transparent
          ),
          center = canvasCenter,
          radius = maxRadius * breathScale
        ),
        radius = maxRadius * breathScale,
        center = canvasCenter
      )

      // Audio wave ripple when speaking
      if (state == AssistantState.SPEAKING && isFullAnim) {
        val rippleRadius = (maxRadius * 0.65f) + (maxRadius * 0.35f * wavePulse)
        val rippleAlpha = (1f - wavePulse).coerceIn(0f, 1f) * 0.6f
        drawCircle(
          color = primaryColor.copy(alpha = rippleAlpha),
          radius = rippleRadius,
          center = canvasCenter,
          style = Stroke(width = 2.dp.toPx())
        )
      }

      // Orbital outer dashed ring
      if (isFullAnim) {
        drawCircle(
          color = primaryColor.copy(alpha = 0.4f),
          radius = maxRadius * 0.94f,
          center = canvasCenter,
          style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), ringRotation * 2f)
          )
        )

        // Gyro satellite nodes
        if (isHighAnim) {
          val angleRad = (ringRotation * PI / 180f).toFloat()
          val nodeDistance = maxRadius * 0.94f
          val nodeX = canvasCenter.x + nodeDistance * cos(angleRad)
          val nodeY = canvasCenter.y + nodeDistance * sin(angleRad)
          drawCircle(
            color = TheoCyan,
            radius = 3.dp.toPx(),
            center = Offset(nodeX, nodeY)
          )

          val oppAngleRad = angleRad + PI.toFloat()
          val oppX = canvasCenter.x + nodeDistance * cos(oppAngleRad)
          val oppY = canvasCenter.y + nodeDistance * sin(oppAngleRad)
          drawCircle(
            color = TheoViolet,
            radius = 2.5.dp.toPx(),
            center = Offset(oppX, oppY)
          )
        }

        // Inner counter-rotating ring
        drawCircle(
          color = secondaryColor.copy(alpha = 0.35f),
          radius = maxRadius * 0.82f,
          center = canvasCenter,
          style = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), innerRingRotation * 1.5f)
          )
        )
      }
    }

    // Central Core Orb Sphere with asset image
    Box(
      modifier = Modifier
        .size(sizeDp * 0.68f)
        .graphicsLayer {
          scaleX = breathScale
          scaleY = breathScale
        }
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              primaryColor.copy(alpha = 0.35f),
              Color(0xFF0B101D),
              Color(0xFF06090E)
            )
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_theo_orb),
        contentDescription = "Theo Neural Core Orb",
        modifier = Modifier
          .fillMaxSize()
          .padding(4.dp)
          .clip(CircleShape)
      )

      // Subtly overlaid glass reflection highlight
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(CircleShape)
          .drawBehind {
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  primaryColor.copy(alpha = if (state == AssistantState.THINKING) 0.35f else 0.15f),
                  Color.Transparent
                ),
                center = Offset(size.width * 0.38f, size.height * 0.35f),
                radius = size.width * 0.5f
              )
            )
          }
      )
    }
  }
}
