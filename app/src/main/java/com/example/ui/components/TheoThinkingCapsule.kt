package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimationIntensity
import com.example.model.AssistantState
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary

/**
 * The live activity surface for Theo. It intentionally replaces the old boxed "thinking process"
 * checklist with one animated glass capsule: a miniature Theo orb, a changing action caption,
 * and a luminous sweep along the capsule's rim.
 */
@Composable
fun TheoThinkingCapsule(
  assistantState: AssistantState,
  thinkingSteps: List<String>,
  modelLabel: String,
  modifier: Modifier = Modifier
) {
  val transition = rememberInfiniteTransition(label = "TheoCapsuleMotion")
  val sweep by transition.animateFloat(
    initialValue = -0.24f,
    targetValue = 1.24f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = if (assistantState == AssistantState.THINKING) 1750 else 3200,
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart
    ),
    label = "CapsuleLightSweep"
  )
  val breathe by transition.animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "CapsuleBreath"
  )

  val title = when (assistantState) {
    AssistantState.IDLE -> "Theo is ready"
    AssistantState.LISTENING -> "Listening to you"
    AssistantState.THINKING -> "Theo is thinking"
    AssistantState.SPEAKING -> "Theo is speaking"
    AssistantState.OFFLINE -> "Theo is offline"
  }
  val detail = when (assistantState) {
    AssistantState.IDLE -> "Tell me what you want done"
    AssistantState.LISTENING -> "Go ahead — I'm listening"
    AssistantState.THINKING -> thinkingSteps.lastOrNull()?.takeIf { it.isNotBlank() }
      ?: "Planning the next step"
    AssistantState.SPEAKING -> "Putting the answer into words"
    AssistantState.OFFLINE -> "Check your model connection"
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(30.dp))
        .background(TheoSurfaceGlass)
        .liquidGlassSurface(
          shape = RoundedCornerShape(30.dp),
          tint = Color(0x80111D30),
          gloss = 0.22f,
          refraction = 12.dp,
          blurRadius = 8.dp
        )
        .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(30.dp))
        .padding(start = 9.dp, end = 15.dp, top = 9.dp, bottom = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TheoOrb(
        state = assistantState,
        intensity = AnimationIntensity.HIGH,
        sizeDp = 42.dp,
        onClick = {}
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
        AnimatedContent(
          targetState = title,
          transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
          label = "CapsuleTitle"
        ) { text ->
          Text(
            text = text,
            color = TheoTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
          targetState = detail,
          transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) },
          label = "CapsuleDetail"
        ) { text ->
          Text(
            text = text,
            color = TheoTextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1
          )
        }
      }
      Spacer(Modifier.width(10.dp))
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(
              when (assistantState) {
                AssistantState.OFFLINE -> Color(0xFFFF8295)
                AssistantState.THINKING -> TheoCyan.copy(alpha = breathe)
                AssistantState.LISTENING -> Color(0xFF72E7BA)
                AssistantState.SPEAKING -> TheoCyan
                AssistantState.IDLE -> Color(0xFF72E7BA)
              }
            )
        )
        Spacer(Modifier.height(5.dp))
        Text(
          text = if (assistantState == AssistantState.THINKING) "WORKING" else modelLabel,
          color = TheoTextMuted,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.45.sp
        )
      }
    }

    Spacer(Modifier.height(5.dp))
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(3.dp)
        .padding(horizontal = 18.dp)
    ) {
      val trackY = size.height / 2f
      drawLine(
        color = Color.White.copy(alpha = 0.08f),
        start = Offset(0f, trackY),
        end = Offset(size.width, trackY),
        strokeWidth = size.height
      )
      val segmentWidth = size.width * if (assistantState == AssistantState.THINKING) 0.24f else 0.12f
      val start = (size.width * sweep).coerceIn(-segmentWidth, size.width)
      drawRoundRect(
        brush = Brush.horizontalGradient(
          colors = listOf(Color.Transparent, TheoCyan.copy(alpha = 0.24f), Color.White.copy(alpha = 0.92f), TheoCyan.copy(alpha = 0.28f), Color.Transparent),
          startX = start - segmentWidth,
          endX = start + segmentWidth
        ),
        topLeft = Offset(start.coerceIn(0f, size.width), 0f),
        size = Size(segmentWidth.coerceAtMost(size.width - start.coerceIn(0f, size.width)), size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height, size.height)
      )
    }
  }
}
