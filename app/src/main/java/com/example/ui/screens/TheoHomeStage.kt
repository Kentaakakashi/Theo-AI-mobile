package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import com.example.ui.components.TheoOrb
import com.example.ui.components.TheoThinkingCapsule
import com.example.ui.components.liquidGlassSurface
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.viewmodel.TheoUiState

@Composable
fun TheoHomeStage(
  uiState: TheoUiState,
  onOrbClick: () -> Unit,
  onSendMessage: (String) -> Unit,
  onInputChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(Modifier.height(14.dp))

    TheoOrb(
      state = uiState.assistantState,
      intensity = uiState.animationIntensity,
      sizeDp = 224.dp,
      onClick = onOrbClick
    )

    Spacer(Modifier.height(7.dp))
    Text(
      text = "Your AI, in motion.",
      color = TheoTextSecondary,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(3.dp))
    Text(
      text = "What's on your mind?",
      color = TheoTextPrimary,
      fontSize = 30.sp,
      lineHeight = 36.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Talk naturally. Theo can think, act, and check the result.",
      color = TheoTextMuted,
      fontSize = 12.sp,
      lineHeight = 17.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 12.dp)
    )

    Spacer(Modifier.height(16.dp))
    TheoThinkingCapsule(
      assistantState = uiState.assistantState,
      thinkingSteps = uiState.thinkingSteps,
      modelLabel = uiState.activeModel.shortBadge,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(22.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "START WITH A TASK",
        color = TheoTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.75.sp
      )
      Spacer(Modifier.weight(1f))
      Text(
        text = "4 suggestions",
        color = TheoTextMuted,
        fontSize = 10.sp
      )
    }
    Spacer(Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      HomeActionCard(
        title = "Open an app",
        detail = "Jump into a task",
        icon = { Icon(Icons.Default.Launch, null, tint = TheoCyan, modifier = Modifier.size(19.dp)) },
        modifier = Modifier.weight(1f),
        onClick = { onSendMessage("Open an app for me") }
      )
      HomeActionCard(
        title = "Plan my day",
        detail = "Make a clear plan",
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFB2A1FF), modifier = Modifier.size(19.dp)) },
        modifier = Modifier.weight(1f),
        onClick = { onSendMessage("Help me plan my day") }
      )
    }
    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      HomeActionCard(
        title = "Remember",
        detail = "Keep a useful fact",
        icon = { Icon(Icons.Default.Memory, null, tint = Color(0xFFB8ECFF), modifier = Modifier.size(19.dp)) },
        modifier = Modifier.weight(1f),
        onClick = { onInputChange("Remember that ") }
      )
      HomeActionCard(
        title = "Device status",
        detail = "Check your phone",
        icon = { Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF72E7BA), modifier = Modifier.size(19.dp)) },
        modifier = Modifier.weight(1f),
        onClick = { onSendMessage("Check my battery and device status") }
      )
    }
    Spacer(Modifier.height(22.dp))
  }
}

@Composable
private fun HomeActionCard(
  title: String,
  detail: String,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Column(
    modifier = modifier
      .height(104.dp)
      .clip(RoundedCornerShape(23.dp))
      .background(Color.Transparent)
      .liquidGlassSurface(
        shape = RoundedCornerShape(23.dp),
        tint = Color(0x67121E32),
        gloss = 0.18f,
        refraction = 8.dp,
        blurRadius = 5.dp
      )
      .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(23.dp))
      .clickable(onClick = onClick)
      .padding(13.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(
          Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f))
          )
        ),
      contentAlignment = Alignment.Center
    ) { icon() }

    Column {
      Text(title, color = TheoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(2.dp))
      Text(detail, color = TheoTextMuted, fontSize = 10.sp)
    }
  }
}
