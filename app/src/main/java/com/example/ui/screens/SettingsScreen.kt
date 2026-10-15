package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiModelType
import com.example.model.AnimationIntensity
import com.example.service.DeviceActionHandler
import com.example.service.parseGeminiApiKeys
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoErrorRed
import com.example.ui.theme.TheoOnlineGreen
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceDeep
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import com.example.ui.theme.TheoWarningAmber
import com.example.viewmodel.TheoUiState

@Composable
fun SettingsScreen(
  uiState: TheoUiState,
  onSaveSettings: (AiModelType, AnimationIntensity, String, String, String) -> Unit,
  onTestQwen: () -> Unit,
  onTestGemini: () -> Unit,
  onClearChat: () -> Unit,
  onToggleSpeech: () -> Unit,
  isScreenAutomationEnabled: Boolean,
  isScreenAutomationRunning: Boolean,
  onConfigureScreenAutomation: () -> Unit,
  onStopScreenAutomation: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedModel by remember(uiState.activeModel) { mutableStateOf(uiState.activeModel) }
  var selectedIntensity by remember(uiState.animationIntensity) { mutableStateOf(uiState.animationIntensity) }
  var apiKeyInput by remember(uiState.customApiKey) { mutableStateOf(uiState.customApiKey) }
  var groqApiKeyInput by remember(uiState.groqApiKey) { mutableStateOf(uiState.groqApiKey) }
  var termuxHostInput by remember(uiState.termuxHost) { mutableStateOf(uiState.termuxHost) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(TheoDarkBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      // Title
      Text(
        text = "NEURAL CONFIGURATION",
        color = TheoTextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp
      )
      Text(
        text = "// DUAL-ENGINE ARCHITECTURE & HARDWARE PROFILES",
        color = TheoCyan,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Section 1: Model Selection
      SettingsSectionHeader(icon = Icons.Default.CloudQueue, title = "Active Neural Engine")
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiModelType.entries.forEach { model ->
          val isSelected = selectedModel == model
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) TheoSurfaceGlass else TheoSurfaceCard)
              .border(1.dp, if (isSelected) TheoCyan else TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
              .clickable {
                selectedModel = model
                onSaveSettings(model, selectedIntensity, apiKeyInput, termuxHostInput, groqApiKeyInput)
              }
              .padding(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(18.dp)
                  .clip(CircleShape)
                  .border(2.dp, if (isSelected) TheoCyan else TheoTextMuted, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(TheoCyan)
                  )
                }
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = model.displayName,
                    color = TheoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "[${model.shortBadge}]",
                    color = if (model == AiModelType.LOCAL_QWEN) TheoWarningAmber else TheoCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = model.description,
                  color = TheoTextSecondary,
                  fontSize = 11.sp,
                  lineHeight = 15.sp
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Section 2: Termux Local Qwen Bridge
      SettingsSectionHeader(icon = Icons.Default.Terminal, title = "Termux Qwen3-1.7B Local Bridge")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Column {
          Text(
            text = "On-device inference via localhost socket without internet:",
            color = TheoTextSecondary,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = termuxHostInput,
            onValueChange = {
              termuxHostInput = it
              onSaveSettings(selectedModel, selectedIntensity, apiKeyInput, it, groqApiKeyInput)
            },
            label = { Text("Local Socket URL") },
            textStyle = androidx.compose.ui.text.TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = TheoTextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = TheoCyan,
              unfocusedBorderColor = TheoSurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Button(
              onClick = onTestQwen,
              enabled = !uiState.isTestingQwen,
              colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceCard),
              modifier = Modifier.border(1.dp, TheoCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
              if (uiState.isTestingQwen) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TheoCyan)
                Spacer(modifier = Modifier.width(6.dp))
              } else {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TheoCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text("Ping Socket", color = TheoCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Text(
              text = uiState.qwenStatusMessage,
              color = if (uiState.qwenStatusMessage.contains("OK")) TheoOnlineGreen else TheoTextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              modifier = Modifier.padding(start = 8.dp)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(TheoSurfaceDeep)
              .padding(8.dp)
          ) {
            Text(
              text = "💡 In Termux, run:\nllama-server -m qwen3-1.7b.gguf --port 8080",
              color = TheoTextSecondary,
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Section 3: Gemini Cloud API Keys
      SettingsSectionHeader(icon = Icons.Default.Key, title = "Gemini Cloud Credentials")
      val configuredApiKeys = parseGeminiApiKeys(apiKeyInput)
      var apiKeyDraft by remember { mutableStateOf("") }
      var editingApiKeyIndex by remember { mutableStateOf<Int?>(null) }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Add each Gemini API key separately. Paste a key below, then tap Add API. You can edit or remove any saved key. Use a key created in Google AI Studio—not an OAuth token, Google password, or service-account JSON. Keys are stored privately on this device and excluded from Android backup. Theo uses only your configured keys (no simulated AI answers).",
            color = TheoTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
          )

          Button(
            onClick = onTestGemini,
            enabled = !uiState.isTestingGemini && configuredApiKeys.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceCard)
          ) {
            if (uiState.isTestingGemini) {
              CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = TheoCyan)
              Spacer(modifier = Modifier.width(7.dp))
            } else {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TheoCyan, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(7.dp))
            }
            Text("Test Gemini API", color = TheoCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }

          Text(
            text = uiState.geminiStatusMessage,
            color = when {
              uiState.geminiStatusMessage.startsWith("Connected") -> TheoOnlineGreen
              uiState.isTestingGemini -> TheoCyan
              uiState.geminiStatusMessage == "Not tested yet" -> TheoTextMuted
              else -> TheoWarningAmber
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp
          )

          if (configuredApiKeys.isEmpty()) {
            Text(
              text = "NO API KEYS CONFIGURED",
              color = TheoWarningAmber,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          } else {
            configuredApiKeys.forEachIndexed { index, key ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(TheoSurfaceDeep)
                  .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(8.dp))
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "API ${index + 1}",
                    color = TheoTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "••••••••" + key.takeLast(4),
                    color = TheoTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                  )
                }
                Button(
                  onClick = {
                    apiKeyDraft = key
                    editingApiKeyIndex = index
                  },
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceCard)
                ) {
                  Text("Edit", color = TheoCyan, fontSize = 11.sp)
                }
                Button(
                  onClick = {
                    val updatedKeys = configuredApiKeys.toMutableList().also { it.removeAt(index) }
                    apiKeyInput = updatedKeys.joinToString("\n")
                    editingApiKeyIndex = null
                    apiKeyDraft = ""
                    onSaveSettings(selectedModel, selectedIntensity, apiKeyInput, termuxHostInput, groqApiKeyInput)
                  },
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = TheoErrorRed.copy(alpha = 0.16f))
                ) {
                  Text("Delete", color = TheoErrorRed, fontSize = 11.sp)
                }
              }
            }
          }

          OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            placeholder = { Text("Paste Gemini API key here") },
            label = { Text(if (editingApiKeyIndex == null) "New API key" else "Edit API key ${editingApiKeyIndex!! + 1}") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = TheoTextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = TheoCyan,
              unfocusedBorderColor = TheoSurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth()
          )

          if (apiKeyDraft.isNotBlank()) {
            Button(
              onClick = {
                val cleanKey = apiKeyDraft.trim()
                val updatedKeys = configuredApiKeys.toMutableList()
                val editIndex = editingApiKeyIndex
                if (editIndex != null && editIndex in updatedKeys.indices) {
                  updatedKeys[editIndex] = cleanKey
                } else if (cleanKey !in updatedKeys) {
                  updatedKeys.add(cleanKey)
                }
                apiKeyInput = updatedKeys.distinct().joinToString("\n")
                onSaveSettings(selectedModel, selectedIntensity, apiKeyInput, termuxHostInput)
                apiKeyDraft = ""
                editingApiKeyIndex = null
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = TheoCyan)
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (editingApiKeyIndex == null) "Add API" else "Save API", color = TheoDarkBackground, fontWeight = FontWeight.Bold)
            }
          }

          if (editingApiKeyIndex != null) {
            Button(
              onClick = {
                apiKeyDraft = ""
                editingApiKeyIndex = null
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceCard)
            ) {
              Text("Cancel edit", color = TheoTextSecondary)
            }
          }

          Text(
            text = "HTTP 401 usually means a key is invalid. HTTP 403 can indicate project/API access or key restrictions.",
            color = TheoTextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Spacer(modifier = Modifier.height(20.dp))

      // Groq cloud vision provider credentials. Stored locally; never hard-code keys.
      SettingsSectionHeader(icon = Icons.Default.Key, title = "Groq Qwen Vision Credentials")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Used for cloud screenshot understanding with Groq-hosted Qwen vision. Screenshots are sent only when you explicitly run a screen task. Keep this key private; never paste it into chat.",
            color = TheoTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
          )
          OutlinedTextField(
            value = groqApiKeyInput,
            onValueChange = {
              groqApiKeyInput = it
              onSaveSettings(selectedModel, selectedIntensity, apiKeyInput, termuxHostInput, it)
            },
            label = { Text("Groq API key") },
            placeholder = { Text("gsk_…") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = TheoTextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = TheoCyan,
              unfocusedBorderColor = TheoSurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            text = if (groqApiKeyInput.isBlank()) "NOT CONFIGURED" else "KEY SAVED ON THIS DEVICE",
            color = if (groqApiKeyInput.isBlank()) TheoWarningAmber else TheoOnlineGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Section 4: Motion & Battery Optimization
      SettingsSectionHeader(icon = Icons.Default.Animation, title = "Motion & Thermal Profile")
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimationIntensity.entries.forEach { intensity ->
          val isSelected = selectedIntensity == intensity
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) TheoSurfaceGlass else TheoSurfaceCard)
              .border(1.dp, if (isSelected) TheoViolet else TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
              .clickable {
                selectedIntensity = intensity
                onSaveSettings(selectedModel, intensity, apiKeyInput, termuxHostInput)
              }
              .padding(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = intensity.label,
                color = if (isSelected) TheoViolet else TheoTextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
              )
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = TheoViolet,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Section 5: Voice & Speech Output
      SettingsSectionHeader(icon = Icons.Default.VolumeUp, title = "Voice Synthesis (TTS)")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Speak Responses",
              color = TheoTextPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Synthesize neural voice via Android TextToSpeech engine",
              color = TheoTextSecondary,
              fontSize = 11.sp
            )
          }

          Switch(
            checked = uiState.isSpeechEnabled,
            onCheckedChange = { onToggleSpeech() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = TheoCyan,
              checkedTrackColor = TheoCyan.copy(alpha = 0.3f),
              uncheckedThumbColor = TheoTextMuted,
              uncheckedTrackColor = TheoSurfaceDeep
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Screen-aware automation: opt-in Android accessibility tree, no screenshot persistence.
      SettingsSectionHeader(icon = Icons.Default.PhoneAndroid, title = "Screen-aware Automation")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isScreenAutomationEnabled) "Accessibility service enabled" else "Accessibility service not enabled",
                color = if (isScreenAutomationEnabled) TheoOnlineGreen else TheoTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (isScreenAutomationRunning) "A screen task is currently running" else "Inspect accessible labels and operate controls in other apps at your request.",
                color = TheoTextSecondary,
                fontSize = 11.sp
              )
            }
            Button(
              onClick = onConfigureScreenAutomation,
              colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceCard)
            ) {
              Text(if (isScreenAutomationEnabled) "Manage" else "Enable", color = TheoCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
          Text(
            text = "Screen labels and non-editable visible text are sent to the selected planner only while you run a task. On Android 11+, Theo also captures a transient screenshot in memory and sends it to Groq Qwen vision, falling back to your configured Gemini keys if Groq fails. Images are not saved by Theo; screenshot bytes are cleared after the request. Current editable values are excluded from the accessibility tree, but visible screen content (including anything shown in an app) may appear in the screenshot sent to the cloud. On Android 10 and earlier, Theo uses the accessibility tree only. Actions still require a matching accessible node; Theo will not guess coordinates.",
            color = TheoTextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
          )
          if (isScreenAutomationRunning) {
            Button(
              onClick = onStopScreenAutomation,
              colors = ButtonDefaults.buttonColors(containerColor = TheoErrorRed.copy(alpha = 0.18f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Stop running task", color = TheoErrorRed, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Section 6: Data & Chat History
      SettingsSectionHeader(icon = Icons.Default.Delete, title = "Session Maintenance")
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(TheoSurfaceGlass)
          .border(1.dp, TheoSurfaceBorderSubtle, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Purge Chat History",
              color = TheoTextPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Clears conversation stream (Memory Matrix is preserved)",
              color = TheoTextSecondary,
              fontSize = 11.sp
            )
          }

          Button(
            onClick = onClearChat,
            colors = ButtonDefaults.buttonColors(containerColor = TheoErrorRed.copy(alpha = 0.2f)),
            modifier = Modifier.border(1.dp, TheoErrorRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
          ) {
            Text("Purge", color = TheoErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(modifier = Modifier.height(100.dp))
    }
  }
}

@Composable
private fun SettingsSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(bottom = 8.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = TheoCyan,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = title,
      color = TheoCyan,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.sp
    )
  }
}
