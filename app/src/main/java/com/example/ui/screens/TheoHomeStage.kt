package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Responsive empty-chat landing view. The orb, copy and task suggestions resize as one layout
 * instead of forcing a tall, scroll-heavy 224dp hero onto every phone.
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
    val availableHeight = maxHeight
    val orbSize = minOf(196.dp, maxWidth * 0.54f, availableHeight * 0.34f).coerceAtLeast(126.dp)
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(if (availableHeight < 620.dp) 3.dp else 9.dp))

      TheoOrb(
        state = uiState.assistantState,
        intensity = uiState.animationIntensity,
        sizeDp = orbSize,
        onClick = onOrbClick
      )

      Spacer(Modifier.height(4.dp))
      Text(
        text = "YOUR AI, IN MOTION",
        color = TheoTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.05.sp
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = "What’s on your mind?",
        color = TheoTextPrimary,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.75).sp,
        textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(5.dp))
      Text(
        text = "Talk naturally. Theo can think, act, and verify the result.",
        color = TheoTextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 10.dp)
      )

      Spacer(Modifier.height(if (availableHeight < 620.dp) 14.dp else 20.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Try a quick start",
          color = TheoTextSecondary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "4 ideas",
          color = TheoTextMuted,
          fontSize = 11.sp
        )
      }
      Spacer(Modifier.height(9.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
      ) {
        QuickActionChip(
          title = "Open an app",
          icon = Icons.Default.Launch,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Open an app for me") }
        )
        QuickActionChip(
          title = "Plan my day",
          icon = Icons.Default.AutoAwesome,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Help me plan my day") }
        )
      }
      Spacer(Modifier.height(9.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
      ) {
        QuickActionChip(
          title = "Remember",
          icon = Icons.Default.Memory,
          modifier = Modifier.weight(1f),
          onClick = { onInputChange("Remember that ") }
        )
        QuickActionChip(
          title = "Device status",
          icon = Icons.Default.PhoneAndroid,
          modifier = Modifier.weight(1f),
          onClick = { onSendMessage("Check my battery and device status") }
        )
      }
      Spacer(Modifier.height(18.dp))
    }
  }
}

@Composable
private fun QuickActionChip(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier = modifier
      .height(46.dp)
      .clip(shape)
      .background(Color.White.copy(alpha = 0.018f))
      .liquidGlassSurface(shape, tint = Color(0x291A2C45), gloss = 0.08f)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = TheoCyan, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(8.dp))
    Text(
      text = title,
      color = TheoTextPrimary,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}
