package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
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
  val isListening = assistantState == AssistantState.LISTENING
  val canSend = inputText.isNotBlank() && assistantState != AssistantState.THINKING
  val composerShape = RoundedCornerShape(32.dp)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 18.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(composerShape)
        .liquidGlassSurface(
          shape = composerShape,
          tint = Color(0x32131F34),
          gloss = 0.13f,
          refraction = 8.dp,
          blurRadius = 5.dp,
          opticalGlass = true
        )
        .border(0.75.dp, Color.White.copy(alpha = 0.15f), composerShape)
        .padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onMicClick,
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(if (isListening) Color(0x3D63D8FF) else Color.White.copy(alpha = 0.055f))
          .border(
            0.7.dp,
            if (isListening) TheoCyan.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.07f),
            CircleShape
          )
          .testTag("composer_mic_button")
      ) {
        Icon(
          imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
          contentDescription = if (isListening) "Stop listening" else "Voice input",
          tint = if (isListening) TheoCyan else TheoTextMuted,
          modifier = Modifier.size(19.dp)
        )
      }

      Spacer(Modifier.width(12.dp))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        BasicTextField(
          value = inputText,
          onValueChange = onInputChange,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 25.dp, max = 104.dp)
            .testTag("composer_text_input"),
          textStyle = TextStyle(
            color = TheoTextPrimary,
            fontSize = 15.sp,
            lineHeight = 21.sp
          ),
          cursorBrush = SolidColor(TheoCyan),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = { if (canSend) onSend(inputText) }
          ),
          maxLines = 5,
          decorationBox = { innerTextField ->
            if (inputText.isEmpty()) {
              AnimatedContent(
                targetState = when (assistantState) {
                  AssistantState.LISTENING -> "Listening…"
                  AssistantState.THINKING -> "Theo is working…"
                  AssistantState.SPEAKING -> "Theo is replying…"
                  AssistantState.OFFLINE -> "Check your connection…"
                  AssistantState.IDLE -> "Ask Theo anything"
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ComposerPlaceholder"
              ) { hint ->
                Text(
                  text = hint,
                  color = TheoTextMuted,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Normal,
                  maxLines = 1
                )
              }
            }
            innerTextField()
          }
        )
      }

      Spacer(Modifier.width(8.dp))
      IconButton(
        onClick = { if (canSend) onSend(inputText) },
        enabled = canSend,
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(
            if (canSend) Brush.linearGradient(listOf(TheoCyan, TheoViolet))
            else Brush.linearGradient(listOf(Color(0x242E4D69), Color(0x18111B2D)))
          )
          .border(
            0.7.dp,
            if (canSend) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.045f),
            CircleShape
          )
          .testTag("composer_send_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "Send message",
          tint = if (canSend) TheoDarkBackground else TheoTextMuted,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
