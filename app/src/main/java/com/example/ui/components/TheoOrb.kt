package com.example.ui.components

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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native Jetpack Compose adaptation inspired by VoiceOrbs' Particles Orb.
 * The identity is a rotating point-cloud sphere, not a drawn glass ball with a ribbon.
 * A restrained glass-like aura and specular points keep it at home in Theo's liquid-glass UI.
 */
private data class OrbParticle(
  val x: Float,
  val y: Float,
  val z: Float,
  val phase: Float,
  val tint: Float
)

private const val PARTICLE_COUNT = 420
private const val TAU = (PI * 2.0).toFloat()
private val GOLDEN_ANGLE = (PI * (3.0 - sqrt(5.0))).toFloat()

@Composable
fun TheoOrb(
  state: AssistantState,
  intensity: AnimationIntensity,
  modifier: Modifier = Modifier,
  sizeDp: Dp = 190.dp,
  onClick: () -> Unit = {}
) {
  val lowMotion = intensity == AnimationIntensity.BATTERY_SAVER
  val transition = rememberInfiniteTransition(label = "TheoParticlesOrb")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = TAU,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 8200
          AssistantState.LISTENING -> 10500
          AssistantState.SPEAKING -> 6200
          AssistantState.OFFLINE -> 22000
          AssistantState.IDLE -> 18000
        },
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart
    ),
    label = "particleSphereRotation"
  )
  val breath by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (state) {
          AssistantState.THINKING -> 900
          AssistantState.LISTENING -> 1450
          AssistantState.SPEAKING -> 650
          AssistantState.OFFLINE -> 3000
          AssistantState.IDLE -> 3400
        }
      ),
      repeatMode = RepeatMode.Reverse
    ),
    label = "particleSphereBreath"
  )
  val baseColor = when (state) {
    AssistantState.IDLE -> TheoIceBlue
    AssistantState.LISTENING -> Color(0xFFE7F7FF)
    AssistantState.THINKING -> TheoViolet
    AssistantState.SPEAKING -> TheoCyan
    AssistantState.OFFLINE -> TheoWarningAmber
  }
  val accentColor = when (state) {
    AssistantState.IDLE -> TheoViolet
    AssistantState.LISTENING -> TheoCyan
    AssistantState.THINKING -> TheoMagenta
    AssistantState.SPEAKING -> TheoIceBlue
    AssistantState.OFFLINE -> Color(0xFFFF8D79)
  }
  val particles = remember {
    List(PARTICLE_COUNT) { i ->
      val y = 1f - (i.toFloat() / (PARTICLE_COUNT - 1)) * 2f
      val ringRadius = sqrt((1f - y * y).coerceAtLeast(0f))
      val theta = GOLDEN_ANGLE * i
      OrbParticle(
        x = cos(theta) * ringRadius,
        y = y,
        z = sin(theta) * ringRadius,
        phase = ((i * 0.7548777f) % 1f) * TAU,
        tint = (i * 0.618034f) % 1f
      )
    }
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
      val unit = size.minDimension
      val breathe = if (lowMotion) 0f else breath
      val angle = if (lowMotion) 0.16f else phase
      val listening = if (state == AssistantState.LISTENING) 1f else 0f
      val thinking = if (state == AssistantState.THINKING) 1f else 0f
      val speaking = if (state == AssistantState.SPEAKING) 1f else 0f
      val offline = if (state == AssistantState.OFFLINE) 1f else 0f
      val sphereRadius = unit * (0.305f + breathe * (0.008f + speaking * 0.018f + thinking * 0.012f))
      val rotY = angle * when (state) {
        AssistantState.THINKING -> 1.18f
        AssistantState.LISTENING -> 0.86f
        AssistantState.SPEAKING -> 1.45f
        AssistantState.OFFLINE -> 0.38f
        AssistantState.IDLE -> 0.52f
      }
      val rotX = 0.28f
      val cosY = cos(rotY)
      val sinY = sin(rotY)
      val cosX = cos(rotX)
      val sinX = sin(rotX)

      // Soft refractive haze behind the point cloud; the sphere itself is made from particles.
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            baseColor.copy(alpha = 0.13f + breathe * 0.035f),
            accentColor.copy(alpha = 0.07f),
            Color.Transparent
          ),
          center = Offset(cx - unit * 0.025f, cy + unit * 0.015f),
          radius = unit * (0.48f + breathe * 0.015f)
        ),
        radius = unit * 0.48f,
        center = Offset(cx, cy)
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
          center = Offset(cx - unit * 0.12f, cy - unit * 0.15f),
          radius = unit * 0.24f
        ),
        radius = unit * 0.24f,
        center = Offset(cx - unit * 0.04f, cy - unit * 0.04f)
      )

      // A dim, thin glass rim anchors the particle cloud without replacing its silhouette.
      drawCircle(
        color = baseColor.copy(alpha = 0.10f + breathe * 0.035f),
        radius = sphereRadius * 1.04f,
        center = Offset(cx, cy),
        style = Stroke(width = unit * 0.0035f)
      )

      val projectedX = FloatArray(PARTICLE_COUNT)
      val projectedY = FloatArray(PARTICLE_COUNT)
      val projectedDepth = FloatArray(PARTICLE_COUNT)
      for (i in particles.indices) {
        val particle = particles[i]
        val x1 = particle.x * cosY - particle.z * sinY
        val z1 = particle.x * sinY + particle.z * cosY
        val y1 = particle.y * cosX - z1 * sinX
        val z2 = particle.y * sinX + z1 * cosX
        val depth = ((z2 + 1f) / 2f).coerceIn(0f, 1f)
        val wave = sin(particle.phase + angle * 1.4f)
        val ripple = listening * (0.025f + 0.065f * (0.5f + 0.5f * sin(particle.y * 8f - angle * 3f)))
        val pulse = thinking * (0.025f * sin(breathe * PI.toFloat() + particle.phase))
        val flow = speaking * (0.035f * sin(particle.phase * 2f - angle * 2.8f))
        val jitter = speaking * 0.004f * wave
        val ringSpread = offline * 0.055f
        val localRadius = sphereRadius * (1f + ripple + pulse + flow + ringSpread)
        val perspective = 0.72f + depth * 0.42f
        projectedX[i] = cx + x1 * localRadius * perspective + jitter * unit
        projectedY[i] = cy + y1 * localRadius * perspective + (listening * 0.006f * sin(particle.phase * 2f)) * unit
        projectedDepth[i] = depth
      }

      // Draw rear particles first, then front particles to create depth and a natural luminous core.
      for (pass in 0..1) {
        for (i in particles.indices) {
          val depth = projectedDepth[i]
          if ((pass == 0 && depth >= 0.52f) || (pass == 1 && depth < 0.52f)) continue
          val particle = particles[i]
          val front = depth * depth
          val twinkle = 0.72f + 0.28f * sin(particle.phase * 2f + angle * 1.6f)
          val alpha = (0.10f + front * 0.78f) * twinkle * (if (offline > 0f) 0.78f else 1f)
          val radius = unit * (0.0018f + front * 0.0043f) * (0.85f + particle.tint * 0.42f)
          val color = if (particle.tint > 0.78f) accentColor else baseColor
          drawCircle(
            color = color.copy(alpha = alpha.coerceIn(0f, 0.92f)),
            radius = radius,
            center = Offset(projectedX[i], projectedY[i])
          )
          if (depth > 0.84f && particle.tint > 0.74f) {
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.26f), color.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(projectedX[i], projectedY[i]),
                radius = radius * 5.5f
              ),
              radius = radius * 5.5f,
              center = Offset(projectedX[i], projectedY[i])
            )
          }
        }
      }

      // Listening briefly breathes into a wider halo; speaking gets a restrained orbiting glint.
      if (!lowMotion && (state == AssistantState.LISTENING || state == AssistantState.SPEAKING)) {
        val orbitAngle = angle * (if (speaking > 0f) 1.5f else 0.72f)
        val orbitX = cx + cos(orbitAngle) * sphereRadius * 1.08f
        val orbitY = cy + sin(orbitAngle) * sphereRadius * 0.48f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.75f), baseColor.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(orbitX, orbitY),
            radius = unit * 0.045f
          ),
          radius = unit * 0.045f,
          center = Offset(orbitX, orbitY)
        )
      }
    }
  }
}
