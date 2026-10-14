package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivityEntity
import com.example.data.MemoryEntity
import com.example.data.MessageEntity
import com.example.data.TheoDatabase
import com.example.model.AiModelType
import com.example.model.AnimationIntensity
import com.example.model.AssistantState
import com.example.model.ChatMessage
import com.example.model.MemoryItem
import com.example.service.ActionSafetyPolicy
import com.example.service.DeviceActionHandler
import com.example.service.ScreenAction
import com.example.service.ScreenAutomationRequest
import com.example.service.TheoAccessibilityService
import com.example.service.TheoAiEngine
import com.example.service.TheoDiagnosticLogger
import com.example.service.TheoSpeechManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingAppSelection(
  val requestedName: String,
  val candidates: List<DeviceActionHandler.InstalledAppMatch>,
  val task: String? = null,
  val sensitiveAuthorized: Boolean = false
)

data class TheoUiState(
  val assistantState: AssistantState = AssistantState.IDLE,
  val activeModel: AiModelType = AiModelType.GEMINI_FLASH,
  val animationIntensity: AnimationIntensity = AnimationIntensity.HIGH,
  val isSpeechEnabled: Boolean = true,
  val currentTab: Int = 0, // 0: Core/Orb, 1: Memory, 2: Activity, 3: Settings
  val termuxHost: String = "http://127.0.0.1:8080/v1",
  val customApiKey: String = "",
  val qwenStatusMessage: String = "Idle (Tap to ping 127.0.0.1:8080)",
  val isTestingQwen: Boolean = false,
  val isTestingGemini: Boolean = false,
  val geminiStatusMessage: String = "Not tested yet",
  val thinkingSteps: List<String> = emptyList(),
  val currentThinkingStep: String = "",
  val streamingText: String = "",
  val inputText: String = "",
  val selectedCategoryFilter: String = "All",
  val memorySearchQuery: String = "",
  val telemetry: DeviceActionHandler.TelemetryInfo? = null,
  val pendingActionTag: String? = null,
  val pendingScreenTargetLabel: String? = null,
  val pendingScreenPackageName: String? = null,
  val pendingAppSelection: PendingAppSelection? = null,
  val isScreenAutomationEnabled: Boolean = false,
  val isScreenAutomationRunning: Boolean = false
)

class TheoViewModel(application: Application) : AndroidViewModel(application) {

  private val database = TheoDatabase.getDatabase(application)
  private val dao = database.theoDao()
  private val aiEngine = TheoAiEngine()
  private val prefs = application.getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)
  private var screenAutomationJob: Job? = null

  private val _uiState = MutableStateFlow(
    TheoUiState(
      customApiKey = prefs.getString("gemini_api_key", "") ?: "",
      termuxHost = prefs.getString("termux_host", "http://127.0.0.1:8080/v1") ?: "http://127.0.0.1:8080/v1",
      activeModel = try {
        AiModelType.valueOf(prefs.getString("active_model", AiModelType.GEMINI_FLASH.name) ?: AiModelType.GEMINI_FLASH.name)
      } catch (e: Exception) {
        AiModelType.GEMINI_FLASH
      },
      animationIntensity = try {
        AnimationIntensity.valueOf(prefs.getString("anim_intensity", AnimationIntensity.HIGH.name) ?: AnimationIntensity.HIGH.name)
      } catch (e: Exception) {
        AnimationIntensity.HIGH
      },
      isSpeechEnabled = prefs.getBoolean("speech_enabled", true)
    )
  )
  val uiState: StateFlow<TheoUiState> = _uiState.asStateFlow()

  val messages: StateFlow<List<MessageEntity>> = dao.getAllMessages()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val memories: StateFlow<List<MemoryEntity>> = dao.getAllMemories()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val activities: StateFlow<List<ActivityEntity>> = dao.getAllActivities()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val speechManager = TheoSpeechManager(application) { isSpeaking ->
    _uiState.update { current ->
      if (isSpeaking && current.assistantState != AssistantState.THINKING) {
        current.copy(assistantState = AssistantState.SPEAKING)
      } else {
        if (current.assistantState == AssistantState.SPEAKING) {
          current.copy(assistantState = AssistantState.IDLE)
        } else current
      }
    }
  }

  init {
    TheoDiagnosticLogger.info("VIEW_MODEL", "TheoViewModel initialized", "model=${_uiState.value.activeModel}")
    refreshTelemetry()
    seedWelcomeIfEmpty()
  }

  fun refreshTelemetry() {
    val telemetry = DeviceActionHandler.getSystemTelemetry(getApplication())
    _uiState.update { it.copy(telemetry = telemetry) }
  }

  fun refreshScreenAutomationStatus() {
    _uiState.update {
      it.copy(isScreenAutomationEnabled = TheoAccessibilityService.isEnabled(getApplication()))
    }
  }

  fun openAccessibilitySettings() {
    try {
      getApplication<Application>().startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (e: Exception) {
      TheoDiagnosticLogger.error("SCREEN_AUTOMATION", "Could not open Accessibility Settings", e)
      appendMessageAsync("Android could not open Accessibility Settings. Open Settings manually and search for Accessibility.")
    }
  }

  private fun seedWelcomeIfEmpty() {
    viewModelScope.launch {
      // If messages are empty, seed initial welcome greeting from Theo
      // StateFlow's initial empty value is not authoritative while its Room flow is unsubscribed.
      if (dao.getAllMessages().first().isEmpty()) {
        dao.insertMessage(
          MessageEntity(
            id = "welcome-msg",
            text = "Neural Core initialized. Greetings, Operator. I am **Theo**, your native Android AI companion.\n\nConfigure a Gemini API key in Settings or connect your local Qwen server to enable AI responses. Your memories and chat history are stored on this device.",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            modelBadge = "THEO CORE v2.4",
            latencyMs = 42L,
            actionTag = null
          )
        )
      }
    }
  }

  fun setTab(index: Int) {
    TheoDiagnosticLogger.debug("NAVIGATION", "Tab selected", "index=$index")
    _uiState.update { it.copy(currentTab = index) }
  }

  fun setInputText(text: String) {
    _uiState.update { it.copy(inputText = text) }
  }

  fun setCategoryFilter(category: String) {
    _uiState.update { it.copy(selectedCategoryFilter = category) }
  }

  fun setMemorySearchQuery(query: String) {
    _uiState.update { it.copy(memorySearchQuery = query) }
  }

  fun toggleSpeech() {
    val current = !_uiState.value.isSpeechEnabled
    speechManager.isSpeechEnabled = current
    prefs.edit().putBoolean("speech_enabled", current).apply()
    _uiState.update { it.copy(isSpeechEnabled = current) }
    if (!current) {
      speechManager.stop()
    }
  }

  fun onOrbClicked() {
    val currentState = _uiState.value.assistantState
    when (currentState) {
      AssistantState.SPEAKING -> {
        speechManager.stop()
      }
      AssistantState.IDLE -> {
        _uiState.update { it.copy(assistantState = AssistantState.LISTENING) }
        speechManager.speak("Theo listening. State your command.")
      }
      AssistantState.LISTENING -> {
        _uiState.update { it.copy(assistantState = AssistantState.IDLE) }
      }
      else -> {
        // No-op for thinking
      }
    }
  }

  fun sendMessage(promptText: String? = null) {
    val stateAtSubmission = _uiState.value
    if (stateAtSubmission.assistantState == AssistantState.THINKING) {
      TheoDiagnosticLogger.warn("CHAT", "Ignored a second send while an AI request is active")
      return
    }
    val text = (promptText ?: stateAtSubmission.inputText).trim()
    if (text.isBlank()) return

    val userMsg = MessageEntity(
      id = UUID.randomUUID().toString(),
      text = text,
      isUser = true,
      timestamp = System.currentTimeMillis(),
      modelBadge = "OPERATOR",
      latencyMs = 0L,
      actionTag = null
    )

    TheoDiagnosticLogger.info("CHAT", "User submitted message", "characters=${text.length}, model=${stateAtSubmission.activeModel}")
    _uiState.update {
      it.copy(
        inputText = "",
        assistantState = AssistantState.THINKING,
        thinkingSteps = listOf("Message received"),
        currentThinkingStep = "Message received",
        streamingText = ""
      )
    }

    viewModelScope.launch {
      dao.insertMessage(userMsg)
      dao.insertActivity(
        ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = "User Synapse Query",
          subtitle = text.take(50),
          timestamp = System.currentTimeMillis(),
          type = "CHAT"
        )
      )

      // Map current memories to MemoryItem domain objects
      val currentMemories = memories.value.map {
        MemoryItem(
          id = it.id,
          category = it.category,
          key = it.memoryKey,
          value = it.memoryValue,
          createdAt = it.createdAt
        )
      }

      val result = try {
        aiEngine.processQuery(
          prompt = text,
          activeModel = stateAtSubmission.activeModel,
          customApiKey = stateAtSubmission.customApiKey,
          termuxHost = stateAtSubmission.termuxHost,
          memories = currentMemories,
          onStreamChunk = { chunk ->
            _uiState.update { current -> current.copy(streamingText = current.streamingText + chunk) }
          },
          onProgress = { step ->
            _uiState.update { current ->
              current.copy(
                currentThinkingStep = step,
                thinkingSteps = if (current.thinkingSteps.lastOrNull() == step) current.thinkingSteps
                  else current.thinkingSteps + step
              )
            }
          }
        )
      } catch (e: Exception) {
        TheoDiagnosticLogger.error("CHAT", "Unhandled AI processing exception", e)
        TheoAiEngine.EngineResult(
          responseText = "Theo hit an unexpected error while processing this request. Open the LOGS tab and copy the ERROR entry to diagnose it.",
          modelBadge = "ENGINE ERROR",
          latencyMs = 0L
        )
      }

      // If a memory extraction occurred, insert it
      result.memoryExtracted?.let { (key, value) ->
        val mem = MemoryEntity(
          id = UUID.randomUUID().toString(),
          category = "Captured",
          memoryKey = key,
          memoryValue = value,
          createdAt = System.currentTimeMillis()
        )
        dao.insertMemory(mem)
        dao.insertActivity(
          ActivityEntity(
            id = UUID.randomUUID().toString(),
            title = "Memory Encoded",
            subtitle = "$key -> $value",
            timestamp = System.currentTimeMillis(),
            type = "MEMORY"
          )
        )
      }

      val actionKey = result.actionSuggested
      val requiresConfirmation = actionKey != null && ActionSafetyPolicy.requiresConfirmation(actionKey)
      // A central policy sends safe navigation to execution directly and pauses sensitive intents.
      val executeAutomatically = actionKey != null && !requiresConfirmation

      val assistantMsg = MessageEntity(
        id = UUID.randomUUID().toString(),
        text = result.responseText,
        isUser = false,
        timestamp = System.currentTimeMillis(),
        modelBadge = result.modelBadge,
        latencyMs = result.latencyMs,
        actionTag = if (executeAutomatically) null else actionKey
      )
      dao.insertMessage(assistantMsg)
      TheoDiagnosticLogger.info("CHAT", "Assistant response stored", "badge=${result.modelBadge}, chars=${result.responseText.length}, latencyMs=${result.latencyMs}")

      _uiState.update {
        it.copy(
          assistantState = AssistantState.IDLE,
          pendingActionTag = if (requiresConfirmation) actionKey else null,
          thinkingSteps = emptyList(),
          currentThinkingStep = "",
          streamingText = ""
        )
      }

      if (executeAutomatically && actionKey != null) {
        executeDeviceAction(actionKey)
      }

      // Voice read-out if speech enabled
      if (_uiState.value.isSpeechEnabled) {
        speechManager.speak(result.responseText)
      }
    }
  }

  fun executeDeviceAction(actionKey: String) {
    val state = _uiState.value
    val explicitlyConfirmed = state.pendingActionTag == actionKey

    // If an unexpected sensitive target was found, explicit confirmation restarts the
    // task from the target app's current screen with the additional authorization.
    if (actionKey.startsWith("screen:", ignoreCase = true)) {
      val authorizeTask = explicitlyConfirmed || state.pendingScreenTargetLabel != null
      val expectedPackageName = state.pendingScreenPackageName
      _uiState.update {
        it.copy(
          pendingActionTag = null,
          pendingScreenTargetLabel = null,
          pendingScreenPackageName = null
        )
      }
      startScreenAction(actionKey, sensitiveAuthorized = authorizeTask, expectedPackageName = expectedPackageName)
      return
    }

    _uiState.update { it.copy(pendingActionTag = null, pendingScreenTargetLabel = null) }
    if (actionKey.startsWith("app:", ignoreCase = true)) {
      startAppLaunch(actionKey.substringAfter(':').trim())
      return
    }

    val success = DeviceActionHandler.executeAction(getApplication(), actionKey)
    if (success) {
      viewModelScope.launch {
        dao.insertActivity(ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = "Device Intent Dispatched",
          subtitle = "Target: $actionKey",
          timestamp = System.currentTimeMillis(),
          type = "DEVICE"
        ))
        if (actionKey == "setup:accessibility") delay(250)
        refreshScreenAutomationStatus()
      }
    } else {
      appendMessageAsync("Android could not dispatch the requested action. No successful outcome is being claimed.")
    }
  }

  private fun startAppLaunch(requestedName: String) {
    if (requestedName.isBlank()) {
      appendMessageAsync("Theo couldn't identify an app name in that request.")
      return
    }
    val candidates = DeviceActionHandler.findInstalledApps(getApplication(), requestedName)
    if (candidates.isEmpty()) {
      appendMessageAsync("No installed launchable app matched \"$requestedName\". Theo did not open a different app.")
      return
    }
    val exact = candidates.filter { DeviceActionHandler.isExactAppNameMatch(it.label, requestedName) }
    if (exact.size == 1) {
      launchChosenApp(exact.single(), task = null, sensitiveAuthorized = false)
    } else {
      _uiState.update {
        it.copy(pendingAppSelection = PendingAppSelection(requestedName, candidates.take(6)))
      }
    }
  }

  private fun startScreenAction(
    actionKey: String,
    sensitiveAuthorized: Boolean,
    expectedPackageName: String? = null
  ) {
    val request = ScreenAutomationRequest.parse(actionKey)
    if (request == null) {
      appendMessageAsync("Theo couldn't safely parse the screen task, so no app action was taken.")
      return
    }
    if (!TheoAccessibilityService.isEnabled(getApplication())) {
      appendMessageAsync(
        "Screen-aware automation is not enabled. Enable Theo Screen Automation in Android Accessibility Settings, then repeat the task.",
        actionTag = "setup:accessibility"
      )
      return
    }
    if (request.appName == null) {
      runScreenAutomation(request.task, sensitiveAuthorized, expectedPackageName)
      return
    }

    val candidates = DeviceActionHandler.findInstalledApps(getApplication(), request.appName)
    if (candidates.isEmpty()) {
      appendMessageAsync("No installed launchable app matched \"${request.appName}\". Theo did not open a different app.")
      return
    }
    val exact = candidates.filter { DeviceActionHandler.isExactAppNameMatch(it.label, request.appName) }
    if (exact.size == 1) {
      launchChosenApp(exact.single(), request.task, sensitiveAuthorized)
    } else {
      _uiState.update {
        it.copy(pendingAppSelection = PendingAppSelection(
          request.appName, candidates.take(6), request.task, sensitiveAuthorized
        ))
      }
    }
  }

  fun selectAppForPendingRequest(packageName: String) {
    val pending = _uiState.value.pendingAppSelection ?: return
    val selected = pending.candidates.firstOrNull { it.packageName == packageName } ?: return
    _uiState.update { it.copy(pendingAppSelection = null) }
    launchChosenApp(selected, pending.task, pending.sensitiveAuthorized)
  }

  fun dismissAppSelection() {
    _uiState.update { it.copy(pendingAppSelection = null) }
  }

  private fun launchChosenApp(
    app: DeviceActionHandler.InstalledAppMatch,
    task: String?,
    sensitiveAuthorized: Boolean
  ) {
    if (!DeviceActionHandler.launchInstalledAppByPackage(getApplication(), app.packageName)) {
      appendMessageAsync("Android couldn't launch ${app.label}. No task was attempted.")
      return
    }
    viewModelScope.launch {
      dao.insertActivity(ActivityEntity(
        id = UUID.randomUUID().toString(),
        title = "App Launch Intent Dispatched",
        subtitle = app.label,
        timestamp = System.currentTimeMillis(),
        type = "DEVICE"
      ))
      if (task == null) {
        appendAssistantMessage("Android accepted the launch intent for ${app.label}. Theo cannot infer from that alone that a screen or action inside the app succeeded.")
      } else {
        delay(900)
        runScreenAutomation(task, sensitiveAuthorized, app.packageName)
      }
    }
  }

  private fun runScreenAutomation(
    task: String,
    sensitiveAuthorized: Boolean,
    expectedPackageName: String? = null
  ) {
    if (screenAutomationJob?.isActive == true) {
      appendMessageAsync("A screen automation task is already running. Stop it from Settings before starting another.")
      return
    }
    screenAutomationJob = viewModelScope.launch {
      _uiState.update { state ->
        state.copy(
          isScreenAutomationRunning = true,
          assistantState = AssistantState.THINKING,
          currentThinkingStep = "Reading accessible screen elements",
          thinkingSteps = listOf("Screen task accepted")
        )
      }
      val history = mutableListOf<String>()
      var finalMessage = ""
      var pausedForReview = false
      var consecutiveFailures = 0
      try {
        for (stepNumber in 1..12) {
          kotlinx.coroutines.currentCoroutineContext().ensureActive()
          val service = TheoAccessibilityService.instance
          if (service == null) {
            finalMessage = "Theo's Accessibility Service is enabled in Android Settings but is not connected yet. Return to Theo and retry."
            break
          }
          var snapshot: com.example.service.ScreenSnapshot? = null
          var snapshotAttempts = 0
          do {
            snapshot = service.captureActiveScreen()
            if (expectedPackageName == null || snapshot?.packageName == expectedPackageName) break
            snapshotAttempts++
            if (snapshotAttempts < 5) delay(400)
          } while (snapshotAttempts < 5)

          if (snapshot == null || snapshot.elements.isEmpty()) {
            finalMessage = "Theo couldn't read an accessible UI tree from the current app. No guessed taps were made. This app may not expose accessible controls; screenshot fallback is not enabled in this build."
            break
          }
          if (expectedPackageName != null && snapshot.packageName != expectedPackageName) {
            finalMessage = "Theo expected the target app package $expectedPackageName but the active screen belongs to ${snapshot.packageName}. Theo stopped rather than operating in another app."
            break
          }
          _uiState.update { state ->
            state.copy(
              currentThinkingStep = "Planning step $stepNumber",
              thinkingSteps = (state.thinkingSteps + "Inspecting accessible UI · step $stepNumber").takeLast(6)
            )
          }
          val plan = aiEngine.planScreenAction(
            task = task,
            screen = snapshot,
            history = history,
            activeModel = _uiState.value.activeModel,
            customApiKey = _uiState.value.customApiKey,
            termuxHost = _uiState.value.termuxHost,
            sensitiveActionsAuthorized = sensitiveAuthorized
          )
          val action = plan.action
          if (action == null) {
            finalMessage = "Screen automation stopped safely: ${plan.error ?: "the configured AI provider returned no usable action."}"
            break
          }
          when (action.kind) {
            "done" -> {
              finalMessage = if (Regex("\\b(play|playing|playback|music)\\b", RegexOption.IGNORE_CASE).containsMatchIn(task)) {
                "Theo's configured AI provider judged the task complete from the visible accessibility tree. Playback itself could not be independently verified."
              } else {
                "Theo's configured AI provider judged the task complete from the visible accessibility tree. Review the target app to verify the final state."
              }
              break
            }
            "clarify" -> {
              finalMessage = "Theo needs your input before continuing: ${action.reason}"
              break
            }
          }

          val targetLabel = action.nodePath?.let { nodeId ->
            snapshot.elements.firstOrNull { it.id == nodeId }?.label.orEmpty()
          }.orEmpty()
          if (!sensitiveAuthorized && ActionSafetyPolicy.targetRequiresConfirmation(action, targetLabel)) {
            finalMessage = "Theo stopped before activating the potentially sensitive control \"$targetLabel\". Nothing was submitted, deleted, shared, or paid for. Review and confirm before retrying."
            pausedForReview = true
            _uiState.update { it.copy(
              pendingActionTag = "screen:current|task:$task",
              pendingScreenTargetLabel = targetLabel.ifBlank { action.kind },
              pendingScreenPackageName = snapshot.packageName
            ) }
            break
          }

          val success = service.performScreenAction(action)
          if (success) {
            consecutiveFailures = 0
            history.add("Step $stepNumber: ${action.kind} on ${action.nodePath ?: "(system)"} dispatched. Reason: ${action.reason.take(120)}")
            _uiState.update { state ->
              state.copy(
                currentThinkingStep = "Dispatched step $stepNumber",
                thinkingSteps = (state.thinkingSteps + "Dispatched ${action.kind} · step $stepNumber").takeLast(6)
              )
            }
          } else {
            consecutiveFailures++
            history.add("Step $stepNumber: ${action.kind} on ${action.nodePath ?: "(system)"} failed; read a fresh tree.")
            if (consecutiveFailures >= 2) {
              finalMessage = "Theo stopped after two consecutive accessibility actions failed. The task may be incomplete; no further actions were guessed."
              break
            }
          }
          delay(if (action.kind == "wait") 1000L else 650L)
        }
        if (finalMessage.isBlank()) {
          finalMessage = "Theo reached the 12-step safety limit. The task may be incomplete; review the target app."
        }
        appendAssistantMessage(finalMessage, badge = "SCREEN AUTOMATION")
        dao.insertActivity(ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = if (pausedForReview) "Screen Automation Paused" else "Screen Automation Finished",
          subtitle = finalMessage.take(110),
          timestamp = System.currentTimeMillis(),
          type = "DEVICE"
        ))
      } catch (cancelled: CancellationException) {
        TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Task stopped by user")
      } catch (e: Exception) {
        TheoDiagnosticLogger.error("SCREEN_AUTOMATION", "Screen automation failed", e)
        appendAssistantMessage("Screen automation stopped after an unexpected error. No further steps were attempted: ${e.localizedMessage ?: "unknown error"}.", badge = "SCREEN AUTOMATION")
      } finally {
        _uiState.update { state ->
          state.copy(
            isScreenAutomationRunning = false,
            assistantState = if (state.assistantState == AssistantState.THINKING) AssistantState.IDLE else state.assistantState,
            currentThinkingStep = "",
            thinkingSteps = emptyList(),
            pendingActionTag = if (pausedForReview) state.pendingActionTag else null,
            pendingScreenTargetLabel = if (pausedForReview) state.pendingScreenTargetLabel else null,
            pendingScreenPackageName = if (pausedForReview) state.pendingScreenPackageName else null
          )
        }
      }
    }
  }

  fun stopScreenAutomation() {
    screenAutomationJob?.cancel()
    screenAutomationJob = null
  }

  private fun appendMessageAsync(text: String, actionTag: String? = null) {
    viewModelScope.launch { appendAssistantMessage(text, actionTag) }
  }

  private suspend fun appendAssistantMessage(
    text: String,
    actionTag: String? = null,
    badge: String = "THEO"
  ) {
    dao.insertMessage(MessageEntity(
      id = UUID.randomUUID().toString(),
      text = text,
      isUser = false,
      timestamp = System.currentTimeMillis(),
      modelBadge = badge,
      latencyMs = 0L,
      actionTag = actionTag
    ))
    if (_uiState.value.isSpeechEnabled) speechManager.speak(text)
  }

  fun dismissPendingAction() {
    _uiState.update {
      it.copy(pendingActionTag = null, pendingScreenTargetLabel = null, pendingScreenPackageName = null)
    }
  }

  fun addMemory(category: String, key: String, value: String) {
    viewModelScope.launch {
      val entity = MemoryEntity(
        id = UUID.randomUUID().toString(),
        category = category.ifBlank { "Personal" },
        memoryKey = key.trim(),
        memoryValue = value.trim(),
        createdAt = System.currentTimeMillis()
      )
      dao.insertMemory(entity)
      dao.insertActivity(
        ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = "Manual Memory Inscribed",
          subtitle = key,
          timestamp = System.currentTimeMillis(),
          type = "MEMORY"
        )
      )
    }
  }

  fun deleteMemory(id: String) {
    viewModelScope.launch {
      dao.deleteMemoryById(id)
    }
  }

  fun clearChatHistory() {
    viewModelScope.launch {
      dao.clearMessages()
      speechManager.stop()
      seedWelcomeIfEmpty()
    }
  }

  fun clearActivities() {
    viewModelScope.launch {
      dao.clearActivities()
    }
  }

  fun testGeminiConnection() {
    TheoDiagnosticLogger.info("GEMINI_TEST", "Test requested from Settings")
    _uiState.update { it.copy(isTestingGemini = true, geminiStatusMessage = "Testing saved Gemini key…") }
    viewModelScope.launch {
      val (success, message) = aiEngine.testGeminiConnection(_uiState.value.customApiKey)
      TheoDiagnosticLogger.info("GEMINI_TEST", "Test result displayed", "success=" + success + ", result=" + message)
      _uiState.update {
        it.copy(
          isTestingGemini = false,
          geminiStatusMessage = message
        )
      }
    }
  }

  fun testQwenConnection() {
    TheoDiagnosticLogger.info("QWEN_BRIDGE", "Ping requested from Settings")
    _uiState.update { it.copy(isTestingQwen = true, qwenStatusMessage = "Pinging socket...") }
    viewModelScope.launch {
      val (success, message) = aiEngine.testQwenConnection(_uiState.value.termuxHost)
      TheoDiagnosticLogger.info("QWEN_BRIDGE", "Ping result displayed", "success=$success, result=$message")
      _uiState.update {
        it.copy(
          isTestingQwen = false,
          qwenStatusMessage = message
        )
      }
    }
  }

  fun updateSettings(
    model: AiModelType,
    intensity: AnimationIntensity,
    apiKey: String,
    termuxHost: String
  ) {
    prefs.edit()
      .putString("active_model", model.name)
      .putString("anim_intensity", intensity.name)
      .putString("gemini_api_key", apiKey)
      .putString("termux_host", termuxHost)
      .apply()

    _uiState.update {
      it.copy(
        activeModel = model,
        animationIntensity = intensity,
        customApiKey = apiKey,
        termuxHost = termuxHost,
        geminiStatusMessage = if (apiKey != it.customApiKey) "Not tested yet" else it.geminiStatusMessage
      )
    }
  }

  override fun onCleared() {
    TheoDiagnosticLogger.info("VIEW_MODEL", "TheoViewModel cleared")
    super.onCleared()
    speechManager.shutdown()
  }
}
