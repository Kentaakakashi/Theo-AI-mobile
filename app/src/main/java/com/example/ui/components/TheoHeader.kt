package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiModelType
import com.example.model.AssistantState
import com.example.service.DeviceActionHandler
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoOnlineGreen
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
    AssistantState.IDLE -> TheoOnlineGreen
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
  val shape = RoundedCornerShape(24.dp)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 18.dp, vertical = 7.dp)
      .clip(shape)
      .liquidGlassSurface(
        shape = shape,
        tint = Color(0x202A3A50),
        gloss = 0.085f,
        refraction = 6.dp,
        blurRadius = 4.dp,
        opticalGlass = true
      )
      .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .liquidGlassSurface(CircleShape, tint = Color(0x263E607A), gloss = 0.08f, refraction = 4.dp, blurRadius = 3.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.GraphicEq,
        contentDescription = null,
        tint = TheoCyan,
        modifier = Modifier.size(19.dp)
      )
    }

    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Theo",
        color = TheoTextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(stateColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
          text = "$stateLabel · ${activeModel.shortBadge}",
          color = TheoTextSecondary,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1
        )
      }
    }

    if (telemetry != null) {
      Column(
        modifier = Modifier.padding(end = 2.dp),
        horizontalAlignment = Alignment.End
      ) {
        Text(
          text = telemetry.batteryPct.toString() + "%" + if (telemetry.isCharging) " ⚡" else "",
          color = TheoTextPrimary,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text("battery", color = TheoTextMuted, fontSize = 8.sp)
      }
    }

    IconButton(
      onClick = onToggleSpeech,
      modifier = Modifier.size(38.dp)
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
      modifier = Modifier.size(38.dp)
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
