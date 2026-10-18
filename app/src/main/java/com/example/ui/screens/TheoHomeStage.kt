package com.example.ui.screens

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
    val orbSize = minOf(184.dp, maxWidth * 0.50f, if (compact) 164.dp else 184.dp)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(if (compact) 10.dp else 22.dp))

      TheoOrb(
        state = uiState.assistantState,
        intensity = uiState.animationIntensity,
        sizeDp = orbSize.coerceAtLeast(128.dp),
        onClick = onOrbClick
      )

      Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
      Text(
        text = "YOUR AI, IN MOTION",
        color = TheoTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.35.sp
      )
      Spacer(Modifier.height(7.dp))
      Text(
        text = "What’s on your mind?",
        color = TheoTextPrimary,
        fontSize = if (compact) 25.sp else 29.sp,
        lineHeight = if (compact) 30.sp else 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp,
        textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(5.dp))
      Text(
        text = "Talk naturally. Theo can think, act, and verify the result.",
        color = TheoTextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 14.dp)
      )

      Spacer(Modifier.height(if (compact) 16.dp else 24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Quick starts",
          color = TheoTextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
          text = "4 ideas",
          color = TheoTextMuted,
          fontSize = 10.sp
        )
      }

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier = modifier
      .height(48.dp)
      .clip(shape)
      .liquidGlassSurface(
        shape = shape,
        tint = Color(0x221A2C45),
        gloss = 0.10f,
        refraction = 7.dp,
        blurRadius = 5.dp,
        opticalGlass = true
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(28.dp)
        .clip(RoundedCornerShape(10.dp))
        .liquidGlassSurface(
          shape = RoundedCornerShape(10.dp),
          tint = Color(0x261B3552),
          gloss = 0.08f,
          refraction = 4.dp,
          blurRadius = 3.dp
        ),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = TheoCyan, modifier = Modifier.size(16.dp))
    }
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
