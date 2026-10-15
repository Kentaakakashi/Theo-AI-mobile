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
import com.example.service.ScreenAutomationRequest
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
  onSelectApp: (String) -> Unit,
  onCancelAppSelection: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  // Auto-scroll to latest message on receive
  LaunchedEffect(messages.size, uiState.streamingText, uiState.assistantState) {
    val thinkingItemCount = if (uiState.assistantState == com.example.model.AssistantState.THINKING) 1 else 0
    val streamingItemCount = if (uiState.streamingText.isNotBlank()) 1 else 0
    // Index 0 is the orb; these counts select the latest visible list item.
    val lastIndex = messages.size + thinkingItemCount + streamingItemCount
    if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
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
                text = "TAP CORE TO SPEAK",
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

        if (uiState.assistantState == com.example.model.AssistantState.THINKING) {
          item(key = "thinking_process_viewer") {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TheoSurfaceGlass)
                .border(1.dp, TheoViolet.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
              horizontalAlignment = Alignment.Start
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = TheoViolet,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                  text = "THINKING PROCESS",
                  color = TheoViolet,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                  text = "LIVE",
                  color = TheoCyan,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              uiState.thinkingSteps.forEachIndexed { index, step ->
                Row(
                  modifier = Modifier.padding(vertical = 3.dp),
                  verticalAlignment = Alignment.Top
                ) {
                  Icon(
                    imageVector = if (index < uiState.thinkingSteps.lastIndex) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (index < uiState.thinkingSteps.lastIndex) TheoCyan else TheoViolet,
                    modifier = Modifier.padding(top = 1.dp).size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = step,
                    color = if (index == uiState.thinkingSteps.lastIndex) TheoTextPrimary else TheoTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (index == uiState.thinkingSteps.lastIndex) FontWeight.SemiBold else FontWeight.Normal
                  )
                }
              }
              Text(
                text = "Live app progress, not a verbatim private chain-of-thought.",
                color = TheoTextMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 6.dp)
              )
            }
          }
        }

        if (uiState.streamingText.isNotBlank()) {
          item(key = "live_gemini_stream") {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
              horizontalAlignment = Alignment.Start
            ) {
              Text(
                text = "GEMINI · LIVE RESPONSE",
                color = TheoCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
              )
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                  .background(TheoSurfaceGlass)
                  .border(1.dp, TheoCyan.copy(alpha = 0.45f), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                  .padding(horizontal = 14.dp, vertical = 12.dp)
              ) {
                Text(
                  text = uiState.streamingText,
                  color = TheoTextPrimary,
                  fontSize = 14.sp,
                  lineHeight = 21.sp
                )
              }
            }
          }
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
                .background(TheoSurfaceDeep)
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
