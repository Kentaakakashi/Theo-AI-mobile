package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TheoOrb
import com.example.ui.components.liquidGlassSurface
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.viewmodel.TheoUiState

/**
 * Calm landing surface: the background stays visible and the content is allowed to breathe.
 * Functional controls use glass; the hero itself is deliberately not boxed in.
 */
@Composable
fun TheoHomeStage(
  uiState: TheoUiState,
  onOrbClick: () -> Unit,
  onSendMessage: (String) -> Unit,
  onInputChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val compact = maxHeight < 660.dp
    val orbSize = minOf(158.dp, maxWidth * 0.44f, if (compact) 136.dp else 158.dp)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

      // A quiet status capsule gives the empty state a deliberate starting point without
      // turning the screen into a dashboard full of panels.
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .liquidGlassSurface(
            shape = RoundedCornerShape(50),
            tint = Color.White.copy(alpha = 0.055f),
            gloss = 0.07f,
            refraction = 3.dp,
            blurRadius = 2.dp
          )
          .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .liquidGlassSurface(
              shape = RoundedCornerShape(50),
              tint = when (uiState.assistantState) {
                com.example.model.AssistantState.OFFLINE -> Color(0xFFFF8295)
                com.example.model.AssistantState.THINKING -> Color(0xFFC5B4FF)
                com.example.model.AssistantState.LISTENING,
                com.example.model.AssistantState.SPEAKING -> TheoCyan
                else -> Color(0xFFB6F2D2)
              },
              gloss = 0.04f,
              blurRadius = 0.dp
            )
        )
        Spacer(Modifier.width(7.dp))
        Text(
          text = when (uiState.assistantState) {
            com.example.model.AssistantState.IDLE -> "HERE WHEN YOU NEED ME"
            com.example.model.AssistantState.LISTENING -> "I'M LISTENING"
            com.example.model.AssistantState.THINKING -> "ON IT"
            com.example.model.AssistantState.SPEAKING -> "TALKING TO YOU"
            com.example.model.AssistantState.OFFLINE -> "CHECK CONNECTION"
          },
          color = TheoTextSecondary,
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.85.sp
        )
      }

      Spacer(Modifier.height(if (compact) 2.dp else 5.dp))

      TheoOrb(
        state = uiState.assistantState,
        intensity = uiState.animationIntensity,
        sizeDp = orbSize.coerceAtLeast(128.dp),
        onClick = onOrbClick
      )

      Spacer(Modifier.height(if (compact) 1.dp else 4.dp))
      Text(
        text = "YOUR AI, YOUR SPACE",
        color = TheoTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.35.sp
      )
      Spacer(Modifier.height(7.dp))
      Text(
        text = "What are we figuring out?",
        color = TheoTextPrimary,
        fontSize = if (compact) 23.sp else 26.sp,
        lineHeight = if (compact) 28.sp else 31.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp,
        textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(5.dp))
      Text(
        text = "Ask a question, start a task, or just talk.",
        color = TheoTextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 14.dp)
      )

      Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Try asking Theo",
          color = TheoTextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
          text = "A FEW STARTING POINTS",
          color = TheoTextMuted,
          fontSize = 10.sp
        )
      }

      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuickActionChip(
          title = "Open an app",
          icon = LucideR.drawable.lucide_ic_arrow_up_right,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Open an app for me") }
        )
        QuickActionChip(
          title = "Plan my day",
          icon = LucideR.drawable.lucide_ic_sparkles,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Help me plan my day") }
        )
      }
      Spacer(Modifier.height(7.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuickActionChip(
          title = "Remember",
          icon = LucideR.drawable.lucide_ic_brain,
          modifier = Modifier.weight(1f),
          onClick = { onInputChange("Remember that ") }
        )
        QuickActionChip(
          title = "Device status",
          icon = LucideR.drawable.lucide_ic_smartphone,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Check my battery and device status") }
        )
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun QuickActionChip(
  title: String,
  icon: Int,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier = modifier
      .height(52.dp)
      .clip(shape)
      .liquidGlassSurface(
        shape = shape,
        tint = Color(0x1C29384B),
        gloss = 0.075f,
        refraction = 5.dp,
        blurRadius = 4.dp,
        opticalGlass = true
      )
      .clickable { onClick() }
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(30.dp)
        .clip(RoundedCornerShape(10.dp))
        .liquidGlassSurface(
          shape = RoundedCornerShape(10.dp),
          tint = Color(0x1F3B4B60),
          gloss = 0.08f,
          refraction = 4.dp,
          blurRadius = 3.dp
        ),
      contentAlignment = Alignment.Center
    ) {
      Icon(painter = painterResource(id = icon), contentDescription = null, tint = TheoCyan, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.width(8.dp))
    Text(
      text = title,
      color = TheoTextPrimary,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}
