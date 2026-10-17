package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet

@Composable
fun TheoComposer(
  inputText: String,
  assistantState: AssistantState,
  onInputChange: (String) -> Unit,
  onSend: (String) -> Unit,
  onMicClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quickActions = listOf(
    "Remember fact",
    "Plan my day",
    "Open Settings",
    "Open Camera",
    "Set alarm",
    "Battery status"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color.Transparent,
            Color(0xEE090D14),
            Color(0xFF090D14)
          )
        )
      )
      .imePadding()
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    // Quick contextual action chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      quickActions.forEach { action ->
        val promptText = when (action) {
          "Remember fact" -> "Remember that: "
          "Plan my day" -> "Plan my day with optimal productivity"
          "Open Settings" -> "Open settings"
          "Open Camera" -> "Open camera"
          "Set alarm" -> "Set alarm for morning routine"
          "Battery status" -> "Check battery and system diagnostics"
          else -> action
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TheoSurfaceCard.copy(alpha = 0.8f))
            .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(16.dp))
            .clickable(enabled = assistantState != AssistantState.THINKING) {
              if (action == "Remember fact") {
                onInputChange(promptText)
              } else {
                onSend(promptText)
              }
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
          Text(
            text = action,
            color = TheoTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Main Composer Input Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(TheoSurfaceGlass)
        .neonBorder(RoundedCornerShape(24.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Microphone / Listening Mode Trigger
      val isListening = assistantState == AssistantState.LISTENING
      IconButton(
        onClick = onMicClick,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(if (isListening) TheoCyan.copy(alpha = 0.2f) else Color.Transparent)
          .testTag("composer_mic_button")
      ) {
        Icon(
          imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
          contentDescription = "Voice Input",
          tint = if (isListening) TheoCyan else TheoTextMuted,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(4.dp))

      // Input field
      BasicTextField(
        value = inputText,
        onValueChange = onInputChange,
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 8.dp)
          .testTag("composer_text_input"),
        textStyle = TextStyle(
          color = TheoTextPrimary,
          fontSize = 14.sp,
          fontFamily = FontFamily.SansSerif
        ),
        cursorBrush = SolidColor(TheoCyan),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (assistantState != AssistantState.THINKING) onSend(inputText) }),
        decorationBox = { innerTextField ->
          if (inputText.isEmpty()) {
            Text(
              text = "Ask Theo anything...",
              color = TheoTextMuted,
              fontSize = 14.sp
            )
          }
          innerTextField()
        }
      )

      Spacer(modifier = Modifier.width(4.dp))

      // Send Button
      val canSend = inputText.isNotBlank() && assistantState != AssistantState.THINKING
      IconButton(
        onClick = { if (canSend) onSend(inputText) },
        enabled = canSend,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(
            if (canSend) {
              Brush.linearGradient(listOf(TheoCyan, TheoViolet))
            } else {
              SolidColor(Color(0x331E293B))
            }
          )
          .testTag("composer_send_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "Send Command",
          tint = if (canSend) TheoDarkBackground else TheoTextMuted,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
