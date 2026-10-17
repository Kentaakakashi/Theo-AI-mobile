package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiModelType
import com.example.model.AssistantState
import com.example.service.DeviceActionHandler
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoIceBlue
import com.example.ui.theme.TheoOnlineGreen
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import com.example.ui.theme.TheoWarningAmber

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
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xF0090D14),
            Color(0xCC090D14),
            Color.Transparent
          )
        )
      )
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Theo Brand identity & HUD label
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "THEO",
            color = TheoTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "// OS v2.4",
            color = TheoCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
          )
        }

        // Subtitle status pulse
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 2.dp)
        ) {
          val statusDotColor = when (assistantState) {
            AssistantState.IDLE -> TheoOnlineGreen
            AssistantState.LISTENING -> TheoCyan
            AssistantState.THINKING -> TheoViolet
            AssistantState.SPEAKING -> TheoIceBlue
            AssistantState.OFFLINE -> TheoWarningAmber
          }

          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(statusDotColor)
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = assistantState.label,
            color = TheoTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
          )
        }
      }

      // Right: Telemetry pill & controls
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Model Pill
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TheoSurfaceGlass)
            .border(1.dp, TheoSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "Active Model",
              tint = if (activeModel == AiModelType.LOCAL_QWEN) TheoWarningAmber else TheoCyan,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = activeModel.shortBadge,
              color = TheoTextPrimary,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Speech Toggle
        IconButton(
          onClick = onToggleSpeech,
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isSpeechEnabled) TheoSurfaceGlass else Color(0x331E293B))
            .border(1.dp, if (isSpeechEnabled) TheoCyan.copy(alpha = 0.4f) else Color.Transparent, CircleShape)
            .testTag("toggle_speech_button")
        ) {
          Icon(
            imageVector = if (isSpeechEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = "Toggle Audio Voice",
            tint = if (isSpeechEnabled) TheoCyan else TheoTextMuted,
            modifier = Modifier.size(16.dp)
          )
        }

        // Settings
        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(TheoSurfaceGlass)
            .border(1.dp, TheoSurfaceBorder, CircleShape)
            .testTag("open_settings_button")
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Assistant Settings",
            tint = TheoTextSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    // Telemetry ticker bar
    telemetry?.let {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TelemetryTickerItem(label = "PWR", value = "${it.batteryPct}%" + if (it.isCharging) "⚡" else "")
        TelemetryTickerItem(label = "RAM", value = "${it.freeRamMb}MB")
        TelemetryTickerItem(label = "STORAGE", value = "${it.freeStorageGb}GB")
      }
    }
  }
}

@Composable
private fun TelemetryTickerItem(label: String, value: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "$label: ",
      color = TheoTextMuted,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      fontWeight = FontWeight.Normal
    )
    Text(
      text = value,
      color = TheoCyan,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}