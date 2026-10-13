package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.TheoComposer
import com.example.ui.components.TheoHeader
import com.example.ui.components.TheoOrb
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceDeep
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import com.example.viewmodel.TheoUiState

@Composable
fun CoreAssistantScreen(
  uiState: TheoUiState,
  messages: List<MessageEntity>,
  onSendMessage: (String) -> Unit,
  onInputChange: (String) -> Unit,
  onOrbClick: () -> Unit,
  onToggleSpeech: () -> Unit,
  onOpenSettings: () -> Unit,
  onExecuteAction: (String) -> Unit,
  onDismissAction: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  // Auto-scroll to latest message on receive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(TheoDarkBackground)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 1. Top HUD Header
      TheoHeader(
        assistantState = uiState.assistantState,
        activeModel = uiState.activeModel,
        telemetry = uiState.telemetry,
        isSpeechEnabled = uiState.isSpeechEnabled,
        onToggleSpeech = onToggleSpeech,
        onOpenSettings = onOpenSettings
      )

      // 2. Center Chat & Orb Area
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Centerpiece Orb Header item
        item(key = "centerpiece_orb") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            TheoOrb(
              state = uiState.assistantState,
              intensity = uiState.animationIntensity,
              sizeDp = 180.dp,
              onClick = onOrbClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle state pill
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TheoSurfaceGlass)
                .border(1.dp, TheoSurfaceBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = TheoCyan,
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "Tap Orb to State Voice Command",
                color = TheoTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // Messages list
        items(messages, key = { it.id }) { message ->
          ChatMessageBubble(
            message = message,
            onExecuteAction = onExecuteAction
          )
        }

        item {
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      // 3. Anchored Message Composer
      TheoComposer(
        inputText = uiState.inputText,
        assistantState = uiState.assistantState,
        onInputChange = onInputChange,
        onSend = onSendMessage,
        onMicClick = onOrbClick
      )
    }

    // Action Execution Confirmation Dialog
    uiState.pendingActionTag?.let { actionTag ->
      AlertDialog(
        onDismissRequest = onDismissAction,
        containerColor = TheoSurfaceCard,
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Launch,
              contentDescription = null,
              tint = TheoCyan,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Confirm Safe Android Action",
              color = TheoTextPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Column {
            Text(
              text = "Theo requested to launch native system intent:",
              color = TheoTextSecondary,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TheoSurfaceDeep)
                .padding(10.dp)
            ) {
              Text(
                text = "Intent: ${actionTag.uppercase()}",
                color = TheoCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { onExecuteAction(actionTag) },
            colors = ButtonDefaults.buttonColors(containerColor = TheoCyan),
            modifier = Modifier.testTag("confirm_action_btn")
          ) {
            Text("Launch Intent", color = TheoDarkBackground, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissAction) {
            Text("Dismiss", color = TheoTextMuted)
          }
        }
      )
    }
  }
}
