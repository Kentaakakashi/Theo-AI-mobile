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
  val groqApiKey: String = "",
  val qwenStatusMessage: String = "Idle (Tap to ping 127.0.0.1:8080)",
  val isTestingQwen: Boolean = false,
  val isTestingGemini: Boolean = false,
  val isTestingGroq: Boolean = false,
  val groqStatusMessage: String = "Not tested yet",
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
  private val screenVisionAnalyzer = com.example.service.ScreenVisionAnalyzer()
  private val prefs = application.getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)
  private var screenAutomationJob: Job? = null

  private val _uiState = MutableStateFlow(
    TheoUiState(
      customApiKey = prefs.getString("gemini_api_key", "") ?: "",
      groqApiKey = prefs.getString("groq_api_key", "") ?: "",
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
    if (actionKey.equals("inspect-screen", ignoreCase = true)) {
      _uiState.update { it.copy(pendingActionTag = null, pendingScreenTargetLabel = null, pendingScreenPackageName = null) }
      inspectCurrentScreen()
      return
    }

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

  private fun inspectCurrentScreen() {
    if (screenAutomationJob?.isActive == true) {
      appendMessageAsync("A screen task is already running. Stop it from Settings before requesting another inspection.")
      return
    }
    if (!TheoAccessibilityService.isEnabled(getApplication())) {
      appendMessageAsync(
        "Enable Theo Screen Automation in Android Accessibility Settings, return to Theo, and retry screen inspection.",
        actionTag = "setup:accessibility"
      )
      return
    }
    screenAutomationJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isScreenAutomationRunning = true,
          assistantState = AssistantState.THINKING,
          currentThinkingStep = "Capturing current screen",
          thinkingSteps = listOf("Screen inspection requested")
        )
      }
      try {
        val service = TheoAccessibilityService.instance
        if (service == null) {
          appendAssistantMessage("Theo Screen Automation is enabled but Android has not connected the service yet. Return to Theo and retry.", badge = "SCREEN INSPECTION")
          return@launch
        }
        val snapshot = service.captureActiveScreen()
        val capture = service.captureActiveScreenJpeg()
        var description: String? = null
        var provider: String? = null
        var failure = capture.error
        val bytes = capture.jpegBytes
        if (bytes != null) {
          try {
            _uiState.update { it.copy(currentThinkingStep = "Analyzing screenshot with configured vision API") }
            val vision = screenVisionAnalyzer.describe(
              jpegBytes = bytes,
              groqApiKey = _uiState.value.groqApiKey,
              geminiApiKeys = com.example.service.parseGeminiApiKeys(_uiState.value.customApiKey)
            )
            description = vision.text
            provider = vision.provider
            failure = vision.error
            TheoDiagnosticLogger.info("SCREEN_VISION", "Screen inspection finished", "provider=${vision.provider}, descriptionChars=${vision.text?.length ?: 0}")
          } finally {
            bytes.fill(0)
          }
        }
        val visibleElements = snapshot?.elements.orEmpty()
          .filter { it.label.isNotBlank() || it.clickable || it.editable || it.scrollable }
          .take(40)
        val tree = visibleElements.joinToString("\n") { element ->
          "- ${element.label.ifBlank { element.role.ifBlank { "Unlabelled control" } }}${if (element.clickable) " [clickable]" else ""}${if (element.editable) " [editable]" else ""}${if (element.scrollable) " [scrollable]" else ""}"
        }
        val response = when {
          !description.isNullOrBlank() -> "Current screen inspection via ${provider ?: "configured vision API"}:\n\n$description"
          tree.isNotBlank() -> "Visual analysis unavailable: ${failure ?: "No configured vision provider returned a description."}\n\nAccessibility-only view of ${snapshot?.packageName ?: "the active app"}:\n$tree\n\nThis fallback may omit visual layout and images."
          else -> "Theo could not inspect the current screen. ${failure ?: "Android did not expose a readable accessibility tree or screenshot."} Check that the screen is unlocked and Theo Screen Automation is enabled. Secure or DRM-protected screens may block capture. Native capture does not require Termux or ADB."
        }
        appendAssistantMessage(response, badge = "SCREEN INSPECTION")
        dao.insertActivity(ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = "Screen Inspection",
          subtitle = response.take(110),
          timestamp = System.currentTimeMillis(),
          type = "DEVICE"
        ))
      } catch (cancelled: CancellationException) {
        TheoDiagnosticLogger.info("SCREEN_VISION", "Screen inspection cancelled")
      } catch (error: Exception) {
        TheoDiagnosticLogger.error("SCREEN_VISION", "Screen inspection failed", error)
        appendAssistantMessage("Screen inspection failed: ${error.localizedMessage ?: error.javaClass.simpleName}. No screen action was attempted.", badge = "SCREEN INSPECTION")
      } finally {
        _uiState.update {
          it.copy(
            isScreenAutomationRunning = false,
            assistantState = if (it.assistantState == AssistantState.THINKING) AssistantState.IDLE else it.assistantState,
            currentThinkingStep = "",
            thinkingSteps = emptyList()
          )
        }
      }
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
      var consecutiveWaits = 0
      var lastSnapshotFingerprint: String? = null
      var stableSnapshotCount = 0
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
          // App launch intents often expose a splash screen or an empty tree first.
          // Re-sample for a bounded period instead of treating the first frame as final.
          do {
            snapshot = service.captureActiveScreen()
            val packageMatches = snapshot != null &&
              (expectedPackageName == null || snapshot.packageName == expectedPackageName)
            val hasUsefulElements = snapshot?.elements?.any {
              it.label.isNotBlank() || it.clickable || it.editable || it.scrollable
            } == true
            if (packageMatches && hasUsefulElements) break
            snapshotAttempts++
            if (snapshotAttempts < 12) delay(650)
          } while (snapshotAttempts < 12)

          if (snapshot != null && expectedPackageName != null && snapshot.packageName != expectedPackageName) {
            finalMessage = "Theo expected the target app package $expectedPackageName but the active screen belongs to ${snapshot.packageName}. Theo stopped rather than inspecting or operating in another app."
            break
          }
          if (snapshot == null || snapshot.elements.isEmpty()) {
            val capture = service.captureActiveScreenJpeg()
            val bytes = capture.jpegBytes
            var visualDescription: String? = null
            var visualFailure = capture.error
            if (bytes != null) {
              try {
                val vision = screenVisionAnalyzer.describe(
                  jpegBytes = bytes,
                  groqApiKey = _uiState.value.groqApiKey,
                  geminiApiKeys = com.example.service.parseGeminiApiKeys(_uiState.value.customApiKey)
                )
                visualDescription = vision.text
                visualFailure = vision.error
              } finally {
                bytes.fill(0)
              }
            }
            finalMessage = if (!visualDescription.isNullOrBlank()) {
              "Theo can visually inspect the screen, but Android exposed no actionable accessibility elements. No guessed taps were made. Visual context: $visualDescription"
            } else {
              "Android exposed no usable accessibility elements and visual inspection failed: ${visualFailure ?: "no vision description was returned"}. No guessed taps were made. Check the LOGS tab for SCREEN_VISION details."
            }
            break
          }
          val fingerprint = snapshot.elements.joinToString("|") {
            "${it.id}:${it.label}:${it.clickable}:${it.editable}:${it.scrollable}"
          }.hashCode().toString()
          if (fingerprint == lastSnapshotFingerprint) stableSnapshotCount++ else stableSnapshotCount = 0
          lastSnapshotFingerprint = fingerprint

          _uiState.update { state ->
            state.copy(
              currentThinkingStep = if (stableSnapshotCount < 1) "Waiting for interface to settle" else "Planning step $stepNumber",
              thinkingSteps = (state.thinkingSteps + if (stableSnapshotCount < 1) {
                "Waiting for app transition · sample $stepNumber"
              } else {
                "Inspecting accessible UI · step $stepNumber"
              }).takeLast(6)
            )
          }
          // Give a newly opened screen another beat to finish its transition before asking
          // the model to judge it. The retry is bounded by the overall action-step limit.
          if (stableSnapshotCount < 1) {
            delay(700)
            continue
          }
          _uiState.update { state ->
            state.copy(currentThinkingStep = "Analyzing screenshot with cloud vision")
          }
          val visualContext = try {
            val capture = service.captureActiveScreenJpeg()
            val screenshotBytes = capture.jpegBytes
            if (screenshotBytes != null) {
              try {
                val vision = screenVisionAnalyzer.describe(
                  jpegBytes = screenshotBytes,
                  groqApiKey = _uiState.value.groqApiKey,
                  geminiApiKeys = com.example.service.parseGeminiApiKeys(_uiState.value.customApiKey)
                )
                TheoDiagnosticLogger.info(
                  "SCREEN_VISION",
                  "Screenshot interpretation finished",
                  "provider=${vision.provider}, descriptionChars=${vision.text?.length ?: 0}"
                )
                if (vision.text == null) {
                  TheoDiagnosticLogger.warn("SCREEN_VISION", "Vision provider returned no description", vision.error ?: "Unknown provider error")
                }
                vision.text
              } finally {
                screenshotBytes.fill(0)
              }
            } else {
              TheoDiagnosticLogger.info("SCREEN_VISION", "Screenshot unavailable; using accessibility tree only", capture.error ?: "No reason returned")
              null
            }
          } catch (visionError: Exception) {
            TheoDiagnosticLogger.warn("SCREEN_VISION", "Vision step failed; continuing with accessibility tree", visionError.javaClass.simpleName)
            null
          }
          val plan = aiEngine.planScreenAction(
            task = task,
            screen = snapshot,
            history = history,
            activeModel = _uiState.value.activeModel,
            customApiKey = _uiState.value.customApiKey,
            termuxHost = _uiState.value.termuxHost,
            sensitiveActionsAuthorized = sensitiveAuthorized,
            visualContext = visualContext
          )
          val action = plan.action
          if (action == null) {
            finalMessage = "Screen automation stopped safely: ${plan.error ?: "the configured AI provider returned no usable action."}"
            break
          }
          when (action.kind) {
            "wait" -> {
              consecutiveWaits++
              if (consecutiveWaits >= 5) {
                finalMessage = "Theo waited and rechecked the screen several times, but the configured AI provider still could not identify a safe next step. Check whether the app finished loading, then retry."
                break
              }
              history.add("Step $stepNumber: wait for the interface to load or settle. Reason: ${action.reason.take(120)}")
              _uiState.update { state ->
                state.copy(
                  currentThinkingStep = "Waiting for the app interface",
                  thinkingSteps = (state.thinkingSteps + "Waiting and re-reading the screen").takeLast(6)
                )
              }
              delay(1_200)
              continue
            }
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

          val targetPath = action.nodePath
          val pathElements = targetPath?.let { nodeId ->
            snapshot.elements
              .filter { it.id == nodeId || nodeId.startsWith("${it.id}.") }
              .sortedByDescending { it.id.length }
          }.orEmpty()
          if (action.kind in setOf("click", "type", "scroll")) {
            if (targetPath == null || snapshot.elements.none { it.id == targetPath }) {
              finalMessage = "Theo's configured AI provider selected a node that was not present in the current accessibility tree. No action was taken."
              break
            }
            val hasSupportedTarget = when (action.kind) {
              "click" -> pathElements.any { it.enabled && it.clickable }
              "type" -> pathElements.any { it.enabled && it.editable }
              "scroll" -> pathElements.any { it.enabled && it.scrollable }
              else -> false
            }
            if (!hasSupportedTarget) {
              finalMessage = "Theo's proposed action does not match the current accessible control capabilities. No action was taken."
              break
            }
          }
          // Include the node and nearby actionable ancestors so a labelled parent button
          // (for example, Send) cannot be hidden behind an unlabeled child icon.
          val targetLabel = pathElements.take(4).map { it.label }.filter { it.isNotBlank() }.joinToString(" ")
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

          consecutiveWaits = 0
          val success = service.performScreenAction(action, snapshot.packageName)
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

  fun testGroqConnection() {
    _uiState.update { it.copy(isTestingGroq = true, groqStatusMessage = "Testing saved Groq key…") }
    viewModelScope.launch {
      val (success, message) = aiEngine.testGroqConnection(_uiState.value.groqApiKey)
      TheoDiagnosticLogger.info("GROQ_TEST", "API key test finished", "success=$success, result=$message")
      _uiState.update { it.copy(isTestingGroq = false, groqStatusMessage = message) }
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
    termuxHost: String,
    groqKey: String = _uiState.value.groqApiKey
  ) {
    prefs.edit()
      .putString("active_model", model.name)
      .putString("anim_intensity", intensity.name)
      .putString("gemini_api_key", apiKey)
      .putString("groq_api_key", groqKey)
      .putString("termux_host", termuxHost)
      .apply()

    _uiState.update {
      it.copy(
        activeModel = model,
        animationIntensity = intensity,
        customApiKey = apiKey,
        groqApiKey = groqKey,
        groqStatusMessage = if (groqKey != it.groqApiKey) "Not tested yet" else it.groqStatusMessage,
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
