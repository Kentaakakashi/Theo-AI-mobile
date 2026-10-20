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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TheoThinkingCapsule
import com.example.data.MessageEntity
import com.example.service.ScreenAutomationRequest
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.TheoComposer
import com.example.ui.components.TheoHeader
import com.example.ui.components.TheoOrb
import com.example.ui.components.liquidGlassSurface
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
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
  onSelectApp: (String) -> Unit,
  onCancelAppSelection: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  val hasConversation = messages.isNotEmpty() || uiState.streamingText.isNotBlank()

  LaunchedEffect(messages.size, uiState.streamingText) {
    if (hasConversation) {
      // The conversation header is the first item; the empty-state hero is removed as soon
      // as a conversation starts, so messages never have to scroll past a giant landing panel.
      val lastMessageIndex = (1 + messages.size - 1 + if (uiState.streamingText.isNotBlank()) 1 else 0)
        .coerceAtLeast(0)
      listState.animateScrollToItem(lastMessageIndex)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Transparent)
  ) {
    Column(Modifier.fillMaxSize()) {
      TheoHeader(
        assistantState = uiState.assistantState,
        activeModel = uiState.activeModel,
        telemetry = uiState.telemetry,
        isSpeechEnabled = uiState.isSpeechEnabled,
        onToggleSpeech = onToggleSpeech,
        onOpenSettings = onOpenSettings
      )

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        if (!hasConversation) {
          item(key = "theo_home_stage") {
            TheoHomeStage(
              uiState = uiState,
              onOrbClick = onOrbClick,
              onSendMessage = onSendMessage,
              onInputChange = onInputChange,
              modifier = Modifier.fillMaxWidth().height(440.dp)
            )
          }
        }

        if (hasConversation) {
          item(key = "conversation_header") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.055f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(painter = painterResource(id = LucideR.drawable.lucide_ic_sparkles), contentDescription = null, tint = TheoCyan, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Conversation", color = TheoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              Text(
                when (uiState.assistantState) {
                  com.example.model.AssistantState.IDLE -> "Theo is here with you"
                  com.example.model.AssistantState.LISTENING -> "Listening for your next command"
                  com.example.model.AssistantState.THINKING -> "Working on your request"
                  com.example.model.AssistantState.SPEAKING -> "Replying to you"
                  com.example.model.AssistantState.OFFLINE -> "Check the active AI provider"
                },
                color = TheoTextMuted,
                fontSize = 11.sp,
                maxLines = 1
              )
            }
            Text(messages.size.toString(), color = TheoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
          }
          }
        }

        items(messages, key = { it.id }) { message ->
          ChatMessageBubble(message = message, onExecuteAction = onExecuteAction)
        }

        if (uiState.streamingText.isNotBlank()) {
          item(key = "live_gemini_stream") {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp)) {
              Text("Theo is replying", color = TheoTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, bottom = 5.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                  .background(Color.Transparent)
                  .liquidGlassSurface(RoundedCornerShape(topStart = 5.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp), tint = Color(0x8A1B1B1B), gloss = 0.12f)
                  .padding(horizontal = 15.dp, vertical = 13.dp)
              ) {
                Text(uiState.streamingText, color = TheoTextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
              }
            }
          }
        }
        item { Spacer(Modifier.height(8.dp)) }
      }

      AnimatedVisibility(
        visible = uiState.assistantState == com.example.model.AssistantState.THINKING ||
          uiState.assistantState == com.example.model.AssistantState.LISTENING ||
          uiState.assistantState == com.example.model.AssistantState.SPEAKING,
        enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 3 },
        exit = fadeOut()
      ) {
        TheoThinkingCapsule(
          assistantState = uiState.assistantState,
          thinkingSteps = uiState.thinkingSteps,
          modelLabel = uiState.activeModel.shortBadge,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
      }

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
              painter = painterResource(id = LucideR.drawable.lucide_ic_arrow_up_right),
              contentDescription = null,
              tint = TheoCyan,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = when {
                actionTag.startsWith("order:", ignoreCase = true) -> "Review Before Continuing"
                uiState.pendingScreenTargetLabel != null -> "Potentially Sensitive Control"
                actionTag.startsWith("screen:", ignoreCase = true) -> "Confirm Screen Task"
                else -> "Review Action"
              },
              color = TheoTextPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Column {
            Text(
              text = when {
                actionTag.startsWith("order:", ignoreCase = true) ->
                  "Theo will open real search options only. Review the item, address and price yourself; no purchase will be made."
                uiState.pendingScreenTargetLabel != null ->
                  "Theo stopped before activating \"${uiState.pendingScreenTargetLabel}\". Nothing was submitted or changed. Confirm to restart the task and authorize this sensitive step."
                actionTag.startsWith("screen:", ignoreCase = true) ->
                  "This task may submit, send, delete, share, or otherwise change data. Theo will use the AI provider selected in Settings and the current accessibility tree. Confirm to continue."
                else ->
                  "Review this action. Theo will report the handoff result rather than claim an action succeeded without evidence."
              },
              color = TheoTextSecondary,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Transparent)
                .liquidGlassSurface(RoundedCornerShape(8.dp), tint = TheoSurfaceDeep, gloss = 0.06f)
                .padding(10.dp)
            ) {
              val screenRequest = ScreenAutomationRequest.parse(actionTag)
              Text(
                text = when {
                  uiState.pendingScreenTargetLabel != null -> "Control: ${uiState.pendingScreenTargetLabel}"
                  screenRequest != null -> "Task: ${screenRequest.task}"
                  actionTag.startsWith("order:", ignoreCase = true) -> "Search: ${actionTag.substringAfter(':')}"
                  else -> "Action: $actionTag"
                },
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
            Text(
              when {
                uiState.pendingScreenTargetLabel != null || actionTag.startsWith("screen:", ignoreCase = true) -> "Confirm and run"
                actionTag.startsWith("order:", ignoreCase = true) -> "Open search"
                else -> "Continue"
              },
              color = TheoDarkBackground,
              fontWeight = FontWeight.Bold
            )
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissAction) {
            Text("Dismiss", color = TheoTextMuted)
          }
        }
      )
    }

    uiState.pendingAppSelection?.let { request ->
      AlertDialog(
        onDismissRequest = onCancelAppSelection,
        containerColor = TheoSurfaceCard,
        title = {
          Text("Choose an installed app", color = TheoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Theo couldn't identify one exact match for \"${request.requestedName}\". Choose the intended app from the installed matches below.",
              color = TheoTextSecondary,
              fontSize = 13.sp
            )
            request.candidates.forEach { candidate ->
              OutlinedButton(
                onClick = { onSelectApp(candidate.packageName) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                  Text(candidate.label, color = TheoTextPrimary, fontWeight = FontWeight.SemiBold)
                  Text(candidate.packageName, color = TheoTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = onCancelAppSelection) { Text("Cancel", color = TheoTextMuted) }
        }
      )
    }
  }
}
