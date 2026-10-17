package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiModelType
import com.example.model.AssistantState
import com.example.service.DeviceActionHandler
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet

@Composable
fun TheoHeader(
  assistantState: AssistantState,
  activeModel: AiModelType,
  telemetry: DeviceActionHandler.TelemetryInfo?,
  isSpeechEnabled: Boolean,
  onToggleSpeech: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val stateColor = when (assistantState) {
    AssistantState.IDLE -> Color(0xFF72E7BA)
    AssistantState.LISTENING -> TheoCyan
    AssistantState.THINKING -> TheoViolet
    AssistantState.SPEAKING -> TheoCyan
    AssistantState.OFFLINE -> Color(0xFFFF8295)
  }
  val stateLabel = when (assistantState) {
    AssistantState.IDLE -> "Ready"
    AssistantState.LISTENING -> "Listening"
    AssistantState.THINKING -> "Thinking"
    AssistantState.SPEAKING -> "Speaking"
    AssistantState.OFFLINE -> "Offline"
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 7.dp)
      .clip(RoundedCornerShape(26.dp))
      .background(Color.Transparent)
      .liquidGlassSurface(
        shape = RoundedCornerShape(26.dp),
        tint = Color(0x7610182A),
        gloss = 0.20f,
        refraction = 10.dp,
        blurRadius = 8.dp
      )
      .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(26.dp))
      .padding(start = 11.dp, end = 7.dp, top = 9.dp, bottom = 9.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF4DB8FF), Color(0xFF544CBA), Color(0xFF11182D))
          )
        )
        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.GraphicEq,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(21.dp)
      )
    }

    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Theo",
        color = TheoTextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
      )
      Spacer(Modifier.width(2.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(stateColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
          text = "$stateLabel · ${activeModel.shortBadge}",
          color = TheoTextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1
        )
      }
    }

    if (telemetry != null) {
      Column(
        modifier = Modifier.padding(end = 4.dp),
        horizontalAlignment = Alignment.End
      ) {
        Text(
          text = "${telemetry.batteryPct}%${if (telemetry.isCharging) " ⚡" else ""}",
          color = TheoTextPrimary,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text("battery", color = TheoTextMuted, fontSize = 8.sp)
      }
    }

    IconButton(
      onClick = onToggleSpeech,
      modifier = Modifier
        .padding(start = 2.dp)
        .size(36.dp)
        .clip(CircleShape)
        .background(if (isSpeechEnabled) Color.White.copy(alpha = 0.07f) else Color.Transparent)
    ) {
      Icon(
        imageVector = if (isSpeechEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
        contentDescription = if (isSpeechEnabled) "Disable spoken responses" else "Enable spoken responses",
        tint = if (isSpeechEnabled) TheoCyan else TheoTextMuted,
        modifier = Modifier.size(18.dp)
      )
    }

    IconButton(
      onClick = onOpenSettings,
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.06f))
    ) {
      Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = TheoTextSecondary,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
