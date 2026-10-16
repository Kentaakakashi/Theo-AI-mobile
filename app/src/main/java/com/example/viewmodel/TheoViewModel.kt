package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
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
import com.example.service.TheoNotificationListenerService
import com.example.service.TheoAiEngine
import com.example.service.LocalQwenRuntime
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
  val customApiKey: String = "",
  val groqApiKey: String = "",
  val qwenStatusMessage: String = "Not downloaded · requires a one-time ~1.11 GB download.",
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
  val isNotificationAccessEnabled: Boolean = false,
  val isScreenAutomationRunning: Boolean = false
)

class TheoViewModel(application: Application) : AndroidViewModel(application) {

  private val database = TheoDatabase.getDatabase(application)
  private val dao = database.theoDao()
  private val localQwenRuntime = LocalQwenRuntime(application)
  private val aiEngine = TheoAiEngine(localQwenRuntime)
  private val screenVisionAnalyzer = com.example.service.ScreenVisionAnalyzer()
  private val prefs = application.getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)
  private var screenAutomationJob: Job? = null
  @Volatile private var screenAutomationPaused = false

  private val _uiState = MutableStateFlow(
    TheoUiState(
      customApiKey = prefs.getString("gemini_api_key", "") ?: "",
      groqApiKey = prefs.getString("groq_api_key", "") ?: "",
      qwenStatusMessage = if (localQwenRuntime.isDownloaded()) "Model downloaded · tap Download & Load to initialize native Qwen." else "Not downloaded · requires a one-time ~1.11 GB download.",
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
    // Clear the obsolete host URL on upgrade; local inference no longer uses a Termux bridge.
    prefs.edit().remove("termux_host").apply()
    TheoAccessibilityService.overlayStopHandler = { stopScreenAutomation() }
    TheoAccessibilityService.overlayPauseToggleHandler = { toggleScreenAutomationPause() }
    com.example.service.TheoAutomationForegroundService.onStopRequested = { stopScreenAutomation() }
    TheoDiagnosticLogger.info("VIEW_MODEL", "TheoViewModel initialized", "model=${_uiState.value.activeModel}")
    val interruptedAt = prefs.getLong("screen_automation_started_at", 0L)
    val wasRunning = prefs.getBoolean("screen_automation_in_progress", false)
    if (wasRunning && interruptedAt > 0L) {
      prefs.edit()
        .putBoolean("screen_automation_in_progress", false)
        .remove("screen_automation_started_at")
        .remove("screen_automation_target_package")
        .apply()
      viewModelScope.launch {
        appendAssistantMessage(
          "Theo detected a screen task interrupted by Android or an app/process restart. I did not replay it automatically because the last tap may already have taken effect. Check the target app, then start the task again if needed.",
          badge = "TASK INTERRUPTED"
        )
      }
    }
    refreshTelemetry()
    seedWelcomeIfEmpty()
  }

  fun refreshTelemetry() {
    val telemetry = DeviceActionHandler.getSystemTelemetry(getApplication())
    _uiState.update { it.copy(telemetry = telemetry) }
  }

  fun refreshScreenAutomationStatus() {
    _uiState.update {
      it.copy(
        isScreenAutomationEnabled = TheoAccessibilityService.isEnabled(getApplication()),
        isNotificationAccessEnabled = TheoNotificationListenerService.isEnabled(getApplication())
      )
    }
  }

  fun openNotificationAccessSettings() {
    try {
      getApplication<Application>().startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (error: Exception) {
      TheoDiagnosticLogger.error("NOTIFICATION_ACCESS", "Could not open notification access settings", error)
      appendMessageAsync("Android could not open Notification access settings. Open Settings and search for Notification access.")
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
            text = "Neural Core initialized. Greetings, Operator. I am **Theo**, your native Android AI companion.\n\nConfigure a Gemini API key in Settings or download Qwen once in the On-device Qwen Runtime section to enable offline AI responses. Your memories and chat history are stored on this device.",
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

      val deviceContextRequested = com.example.service.AutomationTaskVerifier.requestsDeviceContext(text)
      val requestedDeviceContext = if (deviceContextRequested) {
        val application = getApplication<Application>()
        val listener = TheoNotificationListenerService.instance
        when {
          !TheoNotificationListenerService.isEnabled(application) ->
            "LIVE DEVICE CONTEXT UNAVAILABLE: Theo's Android Notification access permission is disabled. Do not claim to read current media or notification previews."
          listener == null ->
            "LIVE DEVICE CONTEXT UNAVAILABLE: Android reports Notification access enabled, but Theo's listener is not connected; no live media or notification content is available."
          else -> listener.buildTaskContext(text)
            ?: "LIVE DEVICE CONTEXT UNAVAILABLE: Theo could not obtain relevant current media metadata or notification previews for this request. Do not invent them."
        }
      } else null

      val result = try {
        aiEngine.processQuery(
          prompt = text,
          activeModel = stateAtSubmission.activeModel,
          customApiKey = stateAtSubmission.customApiKey,
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
          },
          deviceContext = requestedDeviceContext
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

      // Device actions already show progress in Theo's UI. Do not read the long
      // implementation/safety preamble aloud before the action even begins.
      if (_uiState.value.isSpeechEnabled && actionKey == null) {
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
    if (actionKey.startsWith("music:", ignoreCase = true)) {
      startMusicSearchAndPlayback(actionKey)
      return
    }
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

  /**
   * Music requests are not complete when the search URL merely opens. Launch the
   * provider's search, then hand the live screen to the same native accessibility
   * action loop so Theo can select a track and press Play like a user would.
   */
  private fun startMusicSearchAndPlayback(actionKey: String) {
    val payload = actionKey.substringAfter(':').trim()
    val separator = payload.indexOf('|')
    val query = if (separator >= 0) payload.substring(separator + 1).trim() else payload
    if (query.isBlank()) {
      appendMessageAsync("Tell Theo which song to play.")
      return
    }
    if (!TheoAccessibilityService.isEnabled(getApplication())) {
      appendMessageAsync(
        "Theo can open the music search, but screen control must be enabled before it can tap the result and Play.",
        actionTag = "setup:accessibility"
      )
      return
    }
    val opened = DeviceActionHandler.executeAction(getApplication(), actionKey)
    if (!opened) {
      appendMessageAsync("Theo couldn't open the music search for \"$query\".")
      return
    }
    viewModelScope.launch {
      // Wait for the app-link transition or a fresh accessibility event instead of
      // sleeping a fixed 900 ms. Keep a bounded deadline for apps that emit no events.
      val service = TheoAccessibilityService.instance
      val ownPackage = getApplication<Application>().packageName
      val deadline = System.currentTimeMillis() + 1_600L
      var revision = service?.currentScreenRevision() ?: 0L
      var snapshot = service?.captureActiveScreen()
      while (
        service != null &&
        (snapshot == null || snapshot.packageName == ownPackage) &&
        System.currentTimeMillis() < deadline
      ) {
        service.awaitScreenChange(revision, timeoutMs = 220L)
        revision = service.currentScreenRevision()
        snapshot = service.captureActiveScreen()
      }
      if (snapshot == null || snapshot.packageName == ownPackage) {
        appendAssistantMessage("The music search opened, but Theo couldn't read the destination screen before the bounded transition wait expired. No playback is claimed.")
        return@launch
      }
      runScreenAutomation(
        task = "Find the song \"$query\" in the current music app and start playback. Prefer an exact title match. If multiple plausible tracks remain, ask me instead of guessing. Verify visible playback state before reporting completion.",
        sensitiveAuthorized = false,
        expectedPackageName = snapshot.packageName
      )
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
    TheoDiagnosticLogger.info(
      "SCREEN_AUTOMATION",
      "Automation coroutine scheduled",
      "taskChars=${task.length}; expectedPackage=${expectedPackageName ?: "(any external app)"}; model=${_uiState.value.activeModel}"
    )
    screenAutomationJob = viewModelScope.launch {
      val automationStartedAt = System.currentTimeMillis()
      com.example.service.TheoAutomationForegroundService.start(getApplication<Application>())
      screenAutomationPaused = false
      TheoAccessibilityService.instance?.setAutomationPaused(false)
      prefs.edit()
        .putBoolean("screen_automation_in_progress", true)
        .putLong("screen_automation_started_at", System.currentTimeMillis())
        .putString("screen_automation_target_package", expectedPackageName.orEmpty())
        .apply()
      TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Automation coroutine started", "task=${task.take(140)}")
      _uiState.update { state ->
        state.copy(
          isScreenAutomationRunning = true,
          assistantState = AssistantState.THINKING,
          currentThinkingStep = "Reading accessible screen elements",
          thinkingSteps = listOf("Screen task accepted")
        )
      }
      publishAutomationOverlay(
        task = task,
        status = "Starting · checking the active app",
        detail = if (expectedPackageName != null) "Expected app: $expectedPackageName" else "Reading the currently active app."
      )
      val history = mutableListOf<String>()
      val pendingPlannedActions = mutableListOf<com.example.service.ScreenAction>()
      var finalMessage = ""
      var pausedForReview = false
      var consecutiveFailures = 0
      var consecutiveWaits = 0
      var lastSnapshotFingerprint: String? = null
      var lastDispatchedActionWasSend = false
      // Prevent a planner from repeatedly dispatching the same terminal tap/click when
      // the target app accepts the input but exposes no meaningful tree change.
      var lastDispatchedActionSignature: String? = null
      var lastDispatchedActionFingerprint: String? = null
      try {
        var playbackAlreadyVerified = false
        val requestedTrackBeforeStart = com.example.service.AutomationTaskVerifier.requestedTrack(task)
        if (requestedTrackBeforeStart != null) {
          val listener = TheoNotificationListenerService.instance
          val packageConstraint = expectedPackageName
            ?: TheoAccessibilityService.instance?.getActiveWindowPackageName()
          val initialPlayback = listener?.verifyRequestedTrack(requestedTrackBeforeStart, packageConstraint)
          if (initialPlayback?.verified == true) {
            val label = listOf(initialPlayback.title, initialPlayback.artist)
              .filter { it.isNotBlank() }.joinToString(" — ").ifBlank { requestedTrackBeforeStart }
            finalMessage = "Already playing and independently verified: \"$label\" in ${initialPlayback.packageName}."
            playbackAlreadyVerified = true
            publishAutomationOverlay(
              task = task,
              status = "Playback already confirmed",
              detail = finalMessage,
              autoHideMs = 9_000L
            )
            TheoDiagnosticLogger.info(
              "PLAYBACK_VERIFY",
              "Task satisfied by existing playback session",
              "package=${initialPlayback.packageName}; track=${initialPlayback.title}"
            )
          }
        }
        for (stepNumber in 1..20) {
          if (playbackAlreadyVerified) break
          kotlinx.coroutines.currentCoroutineContext().ensureActive()
          awaitAutomationResume(task)
          publishAutomationOverlay(
            task = task,
            status = "Inspecting screen · step $stepNumber/20",
            detail = "Checking the current app and available controls before acting."
          )
          val service = TheoAccessibilityService.instance
          if (service == null) {
            finalMessage = "Theo's Accessibility Service is enabled in Android Settings but is not connected yet. Return to Theo and retry."
            break
          }
          // A device lock can steal the active window at ANY point. Wait for the owner to unlock.
          val keyguard = getApplication<Application>().getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
          if (keyguard?.isKeyguardLocked == true) {
            val unlockStartedAt = System.currentTimeMillis()
            val unlockTimeoutMs = 120_000L
            publishAutomationOverlay(task, "Waiting for device unlock", "Android is locked. Unlock your phone normally; Theo will resume only after Android reports it unlocked.")
            _uiState.update { state ->
              state.copy(currentThinkingStep = "Waiting for you to unlock Android",
                thinkingSteps = (state.thinkingSteps + "Device locked · waiting for owner unlock").takeLast(6))
            }
            TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Device keyguard detected; waiting for manual unlock", "timeoutMs=$unlockTimeoutMs")
            while (keyguard.isKeyguardLocked && System.currentTimeMillis() - unlockStartedAt < unlockTimeoutMs) {
              kotlinx.coroutines.currentCoroutineContext().ensureActive()
              delay(500L)
            }
            if (keyguard.isKeyguardLocked) {
              finalMessage = "Theo paused because the phone stayed locked for 2 minutes. Unlock the phone and start the task again; Theo did not try to bypass the lock screen."
              break
            }
            TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Device unlocked; re-reading target screen", "elapsedMs=${System.currentTimeMillis() - unlockStartedAt}")
            pendingPlannedActions.clear()
            history.add("Step $stepNumber: device was locked; user unlocked it. Discarded stale plans and re-read the screen.")
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
            // An empty tree can be a custom-drawn app, not a loading failure. Let the
            // visual fallback inspect it immediately instead of burning ~8 seconds retrying.
            if (packageMatches && (hasUsefulElements || snapshot?.elements?.isEmpty() == true)) break
            snapshotAttempts++
            if (snapshotAttempts < 5) delay(350)
          } while (snapshotAttempts < 5)

          if (snapshot != null && expectedPackageName != null && snapshot.packageName != expectedPackageName) {
            // Android App Lock / biometric UI and app-resolution screens can temporarily
            // become the active window after an explicit app launch. Never interact with
            // those screens as if they were the target app; wait for the user to finish
            // the system prompt, then resume only when the exact target package is active.
            run {
              val targetPackage = expectedPackageName
              val transitionStartedAt = System.currentTimeMillis()
              // System App Lock and biometric prompts can appear after any action.
              val transitionTimeoutMs = 120_000L
              var observedPackage = snapshot.packageName
              publishAutomationOverlay(
                task = task,
                status = "Waiting for app unlock / return",
                detail = "Expected: $targetPackage · Currently showing: $observedPackage. Unlock the Android prompt; Theo will not click this screen."
              )
              _uiState.update {
                it.copy(
                  currentThinkingStep = "Waiting for Android app selection or unlock",
                  thinkingSteps = listOf(
                    "Target app launch requested",
                    "Waiting for Android chooser / App Lock",
                    "Will resume only inside the requested app"
                  )
                )
              }
              TheoDiagnosticLogger.info(
                "SCREEN_AUTOMATION",
                "Waiting for target app to become active",
                "expected=$targetPackage observed=$observedPackage timeoutMs=$transitionTimeoutMs"
              )
              while (System.currentTimeMillis() - transitionStartedAt < transitionTimeoutMs) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                delay(500)
                val refreshedService = TheoAccessibilityService.instance ?: continue
                val refreshed = refreshedService.captureActiveScreen()
                if (refreshed == null) continue
                observedPackage = refreshed.packageName
                if (observedPackage == targetPackage) {
                  snapshot = refreshed
                  TheoDiagnosticLogger.info(
                    "SCREEN_AUTOMATION",
                    "Target app became active after system prompt",
                    "expected=$targetPackage elapsedMs=${System.currentTimeMillis() - transitionStartedAt}"
                  )
                  break
                }
                TheoDiagnosticLogger.debug(
                  "SCREEN_AUTOMATION",
                  "Still waiting outside target app; no actions dispatched",
                  "expected=$targetPackage observed=$observedPackage"
                )
              }
              if (snapshot?.packageName != targetPackage) {
                finalMessage = "Theo waited 2 minutes for $targetPackage, but Android kept showing $observedPackage. Unlock the phone/app or finish the app chooser, then start the task again. Theo did not interact with that other screen."
                break
              }
            } else {
              finalMessage = "Theo waited for the target app, but the active screen still belongs to ${snapshot.packageName}. Theo stopped rather than inspecting or operating in another app."
              break
            }
          }
          if (snapshot == null) {
            val activePackage = service.getActiveWindowPackageName()
            if (expectedPackageName != null && activePackage == getApplication<Application>().packageName) {
              finalMessage = "Theo's own screen is still in the foreground instead of the requested app ($expectedPackageName). Theo stopped without analyzing its own UI. Open the target app, then retry the task."
              publishAutomationOverlay(
                task = task,
                status = "Wrong foreground app · stopped safely",
                detail = finalMessage,
                autoHideMs = 9_000L
              )
              break
            }
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
          // Keep a short observation trail so the planner can reason about transitions.
          val observedLabels = snapshot.elements.map { it.label.trim() }
            .filter { it.isNotBlank() }.distinct().take(35)
          history.add("Step $stepNumber screen observation: package=${snapshot.packageName}; visible labels=${observedLabels.joinToString(" | ").take(1_400)}")
          val fingerprint = snapshot.elements.joinToString("|") {
            "${it.id}:${it.label}:${it.clickable}:${it.editable}:${it.scrollable}"
          }.hashCode().toString()
          // Never repeat a Send tap when the fresh screen has not visibly changed.
          if (lastDispatchedActionWasSend && fingerprint == lastSnapshotFingerprint) {
            finalMessage = "Theo attempted to send the requested message, but the screen did not visibly change. To prevent a duplicate, Theo stopped without tapping Send again. Check the conversation before retrying."
            TheoDiagnosticLogger.warn("SCREEN_AUTOMATION", "Stopped after send without visible screen change; duplicate prevented", "step=$stepNumber")
            break
          }
          lastSnapshotFingerprint = fingerprint

          var validatedQueuedAction: com.example.service.ScreenAction? = null
          if (pendingPlannedActions.isNotEmpty()) {
            val queuedCandidate = pendingPlannedActions.removeAt(0)
            if (isBatchedActionApplicable(queuedCandidate, snapshot)) {
              validatedQueuedAction = queuedCandidate
              TheoDiagnosticLogger.info(
                "SCREEN_AUTOMATION",
                "Using fresh-screen validated planned action",
                "step=$stepNumber; kind=${queuedCandidate.kind}; target=${queuedCandidate.targetLabel.take(80)}; remaining=${pendingPlannedActions.size}"
              )
            } else {
              pendingPlannedActions.clear()
              TheoDiagnosticLogger.info(
                "SCREEN_AUTOMATION",
                "Discarded stale planned action batch",
                "step=$stepNumber; kind=${queuedCandidate.kind}; target=${queuedCandidate.targetLabel.take(80)}"
              )
              history.add("Step $stepNumber: discarded queued actions because the current screen no longer matched their node, label or capability; replan from fresh UI.")
            }
          }

          _uiState.update { state ->
            state.copy(
              currentThinkingStep = "Planning step $stepNumber",
              thinkingSteps = (state.thinkingSteps + "Inspecting accessible UI · step $stepNumber").takeLast(6)
            )
          }
          // The accessibility tree above is a fresh snapshot and action dispatch revalidates
          // the package and control capabilities. Do not burn an extra full planner iteration
          // and 700 ms merely because the tree changed; action settling is event-driven below.
          // Accessibility-first is both faster and more reliable for native controls.
          // Only pay for a screenshot + cloud vision call when this screen exposes no
          // actionable controls; don't send every screen to a second API on every step.
          val hasActionableControls = snapshot.elements.any {
            it.enabled && (it.clickable || it.editable || it.scrollable)
          }
          val visualContext = if (!hasActionableControls && validatedQueuedAction == null) {
            _uiState.update { state ->
              state.copy(currentThinkingStep = "Trying visual fallback")
            }
            try {
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
                    "Visual fallback finished",
                    "provider=${vision.provider}, descriptionChars=${vision.text?.length ?: 0}"
                  )
                  vision.text
                } finally {
                  screenshotBytes.fill(0)
                }
              } else {
                TheoDiagnosticLogger.info("SCREEN_VISION", "Visual fallback unavailable; using accessibility tree", capture.error ?: "No reason returned")
                null
              }
            } catch (visionError: Exception) {
              TheoDiagnosticLogger.warn("SCREEN_VISION", "Visual fallback failed; continuing with accessibility tree", visionError.javaClass.simpleName)
              null
            }
          } else null
          val notificationContext = if (validatedQueuedAction == null && com.example.service.AutomationTaskVerifier.requestsDeviceContext(task)) {
            val application = getApplication<Application>()
            val listener = TheoNotificationListenerService.instance
            when {
              !TheoNotificationListenerService.isEnabled(application) ->
                "LIVE DEVICE CONTEXT UNAVAILABLE: Notification access is disabled; do not claim to know current playback or notification contents."
              listener == null ->
                "LIVE DEVICE CONTEXT UNAVAILABLE: Notification access is enabled, but Theo's listener is not connected; no live media or notification content is available."
              else -> listener.buildTaskContext(task)
                ?: "LIVE DEVICE CONTEXT UNAVAILABLE: No relevant live media metadata or notification previews were available. Do not invent them."
            }
          } else null
          val learnedWorkflowHint = if (com.example.service.AutomationTaskVerifier.requestedTrack(task) != null) {
            prefs.getString("verified_music_flow_${snapshot.packageName.hashCode().toString(16)}", null)
          } else null
          TheoDiagnosticLogger.info(
            "SCREEN_AUTOMATION",
            "Selecting next screen action",
            "step=$stepNumber; package=${snapshot.packageName}; elements=${snapshot.elements.size}; actionable=$hasActionableControls; queuedAction=${validatedQueuedAction != null}; visualContextChars=${visualContext?.length ?: 0}; model=${_uiState.value.activeModel}"
          )
          val plannerStartedAt = System.currentTimeMillis()
          val queuedActionForPlan = validatedQueuedAction
          val plan = if (queuedActionForPlan != null) {
            com.example.service.ScreenPlanResult(
              action = queuedActionForPlan,
              actions = listOf(queuedActionForPlan)
            )
          } else {
            aiEngine.planScreenAction(
              task = task,
              screen = snapshot,
              history = history,
              activeModel = _uiState.value.activeModel,
              customApiKey = _uiState.value.customApiKey,
              sensitiveActionsAuthorized = sensitiveAuthorized,
              visualContext = visualContext,
              deviceContext = notificationContext,
              learnedWorkflowHint = learnedWorkflowHint
            )
          }
          if (screenAutomationPaused) {
            pendingPlannedActions.clear()
            awaitAutomationResume(task)
            history.add("Step $stepNumber: discarded the pre-pause plan and will inspect a fresh screen after resume.")
            continue
          }
          if (validatedQueuedAction == null) {
            pendingPlannedActions.clear()
            val safeBatch = plan.actions.takeIf { isSafePlannedBatch(task, it) }
            if (safeBatch != null) {
              pendingPlannedActions.addAll(safeBatch.drop(1))
              TheoDiagnosticLogger.info(
                "SCREEN_AUTOMATION",
                "Accepted guarded action batch",
                "step=$stepNumber; queued=${pendingPlannedActions.size}; model=${_uiState.value.activeModel}"
              )
            } else if (plan.actions.size > 1) {
              TheoDiagnosticLogger.info(
                "SCREEN_AUTOMATION",
                "Rejected unsafe action batch",
                "step=$stepNumber; count=${plan.actions.size}"
              )
            }
          }
          val action = plan.action
          TheoDiagnosticLogger.info(
            "SCREEN_AUTOMATION",
            "Screen planner returned",
            "step=$stepNumber; source=${if (queuedActionForPlan != null) "validated_batch" else "model"}; latencyMs=${System.currentTimeMillis() - plannerStartedAt}; action=${action?.kind ?: "none"}; reason=${action?.reason?.take(120).orEmpty()}; error=${plan.error?.take(180).orEmpty()}"
          )
          if (action != null) {
            publishAutomationOverlay(
              task = task,
              status = "Plan: ${action.kind}",
              detail = action.reason.ifBlank { action.targetLabel }.ifBlank { "Theo is preparing the next action." }
            )
          }
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
              delay(450L)
              continue
            }
            "done" -> {
              val requestedTrack = com.example.service.AutomationTaskVerifier.requestedTrack(task)
              if (requestedTrack != null) {
                publishAutomationOverlay(
                  task = task,
                  status = "Verifying playback · checking Android media session",
                  detail = "Looking for \"$requestedTrack\" in the expected player. Theo will not repeat the final tap while checking."
                )
                val verificationStartedAt = System.currentTimeMillis()
                var verification: com.example.service.PlaybackVerificationResult? = null
                val notificationAccessGranted = TheoNotificationListenerService.isEnabled(getApplication<Application>())
                val verificationDeadline = verificationStartedAt + 4_500L
                while (System.currentTimeMillis() < verificationDeadline) {
                  kotlinx.coroutines.currentCoroutineContext().ensureActive()
                  val listener = TheoNotificationListenerService.instance
                  if (listener != null) {
                    verification = listener.verifyRequestedTrack(requestedTrack, snapshot.packageName)
                    if (verification?.verified == true) break
                    if (verification?.status == com.example.service.PlaybackVerificationStatus.ACCESS_UNAVAILABLE) break
                  } else if (!notificationAccessGranted) {
                    verification = com.example.service.PlaybackVerificationResult(
                      status = com.example.service.PlaybackVerificationStatus.ACCESS_UNAVAILABLE,
                      requestedTrack = requestedTrack,
                      explanation = "Enable Theo's Notification access in Android Settings to let Theo inspect media-session playback state."
                    )
                    break
                  }
                  delay(350L)
                }
                finalMessage = if (verification?.verified == true) {
                  val workflowSteps = history.mapNotNull { entry ->
                    Regex("""(?i)\bStep\s+\d+:\s*(click|type|scroll|tap|back)\b""")
                      .find(entry)?.groupValues?.getOrNull(1)?.lowercase(java.util.Locale.ROOT)
                  }.take(8)
                  if (workflowSteps.isNotEmpty()) {
                    prefs.edit()
                      .putString("verified_music_flow_${snapshot.packageName.hashCode().toString(16)}", workflowSteps.joinToString(" → "))
                      .apply()
                    TheoDiagnosticLogger.info(
                      "SCREEN_AUTOMATION",
                      "Saved privacy-safe verified music workflow hint",
                      "steps=${workflowSteps.size}"
                    )
                  }
                  val trackLabel = listOf(verification?.title, verification?.artist)
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" — ")
                  "Verified playing: \"${trackLabel.ifBlank { requestedTrack }}\". Android reports playback active in ${verification?.packageName ?: snapshot.packageName}."
                } else {
                  val reason = verification?.explanation
                    ?: "Android did not expose a matching active media session within 4.5 seconds."
                  "Theo completed the screen steps, but could not verify that \"$requestedTrack\" is playing. $reason Check the player before retrying; Theo will not repeat the final tap automatically."
                }
                publishAutomationOverlay(
                  task = task,
                  status = if (verification?.verified == true) "Playback confirmed" else "Playback unverified",
                  detail = finalMessage,
                  autoHideMs = 9_000L
                )
                TheoDiagnosticLogger.info(
                  "PLAYBACK_VERIFY",
                  "Requested track verification finished",
                  "verified=${verification?.verified == true}; status=${verification?.status ?: "NO_RESULT"}; elapsedMs=${System.currentTimeMillis() - verificationStartedAt}"
                )
              } else {
                val requestedMessage = com.example.service.AutomationTaskVerifier.requestedMessage(task)
                if (requestedMessage != null) {
                  val visibleLabels = snapshot.elements
                    .filterNot { it.editable }
                    .map { it.label }
                    .filter { it.isNotBlank() }
                  val visibleText = com.example.service.AutomationTaskVerifier.normalizeVisibleText(visibleLabels.joinToString(" "))
                  val recipientVisible = visibleText.contains(com.example.service.AutomationTaskVerifier.normalizeVisibleText(requestedMessage.recipient))
                  val bodyVisible = visibleText.contains(com.example.service.AutomationTaskVerifier.normalizeVisibleText(requestedMessage.body))
                  finalMessage = if (recipientVisible && bodyVisible) {
                    "The current conversation visibly contains the requested recipient and message text. Theo did not tap Send again. The app's delivery status is not independently exposed, so delivery is not being claimed."
                  } else {
                    "Theo's planner marked the message task complete, but the current accessible screen does not independently show both the requested recipient and message text. To prevent a duplicate, Theo did not press Send again. Check the conversation before retrying."
                  }
                } else {
                  finalMessage = "Theo's configured AI provider judged the task complete from the visible accessibility tree. Review the target app to verify the final state."
                }
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
          val hasVisualCoordinates = Regex(
            "(?i)CONTROL:.*center=\\(\\d{1,4},\\s*\\d{1,4}\\)"
          ).containsMatchIn(visualContext.orEmpty())
          if (action.kind == "tap" && (hasActionableControls || !hasVisualCoordinates)) {
            finalMessage = "Theo rejected the visual tap because a fresh screenshot did not provide a usable coordinate target, or accessible controls were available. No action was taken."
            break
          }
          if (action.kind in setOf("click", "type", "scroll")) {
            if (targetPath == null || snapshot.elements.none { it.id == targetPath }) {
              consecutiveFailures++
              history.add("Planner selected a node absent from the latest tree; fresh plan attempt #" + stepNumber + ". No action was taken.")
              TheoDiagnosticLogger.warn("SCREEN_AUTOMATION", "Planner selected a stale node; retrying with a fresh tree", "step=" + stepNumber + "; failures=" + consecutiveFailures)
              if (consecutiveFailures >= 3) {
                finalMessage = "Theo couldn't recover after three fresh-tree retries because the planner kept selecting controls that weren't present. No action was taken for those failed selections. Try again with the target app open and fully loaded."
                break
              }
              delay(250L)
              continue
            }
            val hasSupportedTarget = when (action.kind) {
              "click" -> pathElements.any { it.enabled && it.clickable }
              "type" -> pathElements.any { it.enabled && it.editable }
              "scroll" -> pathElements.any { it.enabled && it.scrollable }
              else -> false
            }
            if (!hasSupportedTarget) {
              consecutiveFailures++
              history.add("Planner action did not match the current control capabilities at step " + stepNumber + "; re-read and replan.")
              TheoDiagnosticLogger.warn("SCREEN_AUTOMATION", "Planner action did not match node capability; retrying", "step=" + stepNumber)
              if (consecutiveFailures >= 3) {
                finalMessage = "Theo stopped after three attempts to match an action to a current control. No action was taken on the unsupported target."
                break
              }
              delay(250L)
              continue
            }
          }
          // Include the node and nearby actionable ancestors so a labelled parent button
          // (for example, Send) cannot be hidden behind an unlabeled child icon.
          val targetLabel = if (action.kind == "tap") {
            action.targetLabel
          } else {
            pathElements.take(4).map { it.label }.filter { it.isNotBlank() }.joinToString(" ")
          }
          val actionSignature = listOf(
            action.kind,
            action.nodePath.orEmpty(),
            action.text.orEmpty(),
            action.x?.toString().orEmpty(),
            action.y?.toString().orEmpty(),
            targetLabel.lowercase().trim()
          ).joinToString("|")
          if (
            actionSignature == lastDispatchedActionSignature &&
            fingerprint == lastDispatchedActionFingerprint
          ) {
            finalMessage = "Theo stopped because the planner repeated the same action on an unchanged screen. No duplicate action was dispatched. The task may need a different screen state or a manual check."
            TheoDiagnosticLogger.warn(
              "SCREEN_AUTOMATION",
              "Duplicate action prevented on unchanged screen",
              "step=$stepNumber; action=${action.kind}; target=${targetLabel.take(100)}; fingerprint=$fingerprint"
            )
            break
          }
          val explicitlyRequestedOrdinarySend =
            action.kind == "click" &&
              Regex("""\b(message|reply|send)\b""", RegexOption.IGNORE_CASE).containsMatchIn(task) &&
              Regex("""\b(send|send message|reply)\b""", RegexOption.IGNORE_CASE).containsMatchIn(targetLabel) &&
              !Regex("""\b(pay|payment|purchase|transfer|delete|remove|share|publish|install|permission|password|otp|code)\b""", RegexOption.IGNORE_CASE).containsMatchIn(task)
          if (!sensitiveAuthorized && !explicitlyRequestedOrdinarySend && ActionSafetyPolicy.targetRequiresConfirmation(action, targetLabel)) {
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
          publishAutomationOverlay(
            task = task,
            status = "Doing: ${action.kind}",
            detail = "Target: ${targetLabel.ifBlank { action.nodePath ?: "screen control" }} · ${action.reason.take(130)}"
          )
          val revisionBeforeAction = service.currentScreenRevision()
          val actionStartedAt = System.currentTimeMillis()
          val success = service.performScreenAction(action, snapshot.packageName)
          val actionDispatchMs = System.currentTimeMillis() - actionStartedAt
          TheoDiagnosticLogger.debug(
            "SCREEN_AUTOMATION",
            "Accessibility action dispatch finished",
            "step=$stepNumber; action=${action.kind}; success=$success; latencyMs=$actionDispatchMs"
          )
          if (success) {
            publishAutomationOverlay(
              task = task,
              status = "Action sent: ${action.kind}",
              detail = "Target: ${targetLabel.ifBlank { action.nodePath ?: "screen control" }}"
            )
            consecutiveFailures = 0
            lastDispatchedActionWasSend = explicitlyRequestedOrdinarySend
            lastDispatchedActionSignature = actionSignature
            lastDispatchedActionFingerprint = fingerprint
            history.add("Step $stepNumber: ${action.kind} on ${action.nodePath ?: "(system)"} dispatched; target=${targetLabel.take(100)}. Reason: ${action.reason.take(120)}")
            _uiState.update { state ->
              state.copy(
                currentThinkingStep = "Dispatched step $stepNumber",
                thinkingSteps = (state.thinkingSteps + "Dispatched ${action.kind} · step $stepNumber").takeLast(6)
              )
            }
          } else {
            pendingPlannedActions.clear()
            lastDispatchedActionWasSend = false
            consecutiveFailures++
            history.add("Step $stepNumber: ${action.kind} on ${action.nodePath ?: "(system)"} failed; read a fresh tree.")
            if (consecutiveFailures >= 3) {
              finalMessage = "Theo stopped after three consecutive accessibility actions failed. The task may be incomplete; no further actions were guessed."
              break
            }
          }
          if (success) {
            val settleStartedAt = System.currentTimeMillis()
            val screenChanged = service.awaitScreenChange(revisionBeforeAction, timeoutMs = 420L)
            TheoDiagnosticLogger.debug(
              "SCREEN_AUTOMATION",
              "Action settle wait finished",
              "step=$stepNumber; screenChanged=$screenChanged; waitMs=${System.currentTimeMillis() - settleStartedAt}; maxWaitMs=420"
            )
          } else {
            delay(100L)
          }
        }
        if (finalMessage.isBlank()) {
          finalMessage = "Theo reached the 20-step safety limit. The task may be incomplete; review the target app."
        }
        val finalOverlayStatus = when {
          pausedForReview -> "Paused for your confirmation"
          finalMessage.startsWith("Verified playing", ignoreCase = true) ||
            finalMessage.startsWith("Already playing and independently verified", ignoreCase = true) -> "Playback confirmed"
          finalMessage.startsWith("Theo completed the screen steps, but could not verify", ignoreCase = true) -> "Playback unverified"
          finalMessage.startsWith("The current conversation visibly contains", ignoreCase = true) -> "Message text visible · delivery unverified"
          finalMessage.contains("does not independently show both", ignoreCase = true) -> "Message unverified · no duplicate sent"
          finalMessage.contains("judged the task complete", ignoreCase = true) -> "Task steps finished · verify result"
          else -> "Task ended"
        }
        publishAutomationOverlay(
          task = task,
          status = finalOverlayStatus,
          detail = finalMessage,
          autoHideMs = 9_000L
        )
        appendAssistantMessage(finalMessage, badge = "SCREEN AUTOMATION")
        dao.insertActivity(ActivityEntity(
          id = UUID.randomUUID().toString(),
          title = if (pausedForReview) "Screen Automation Paused" else "Screen Automation Finished",
          subtitle = finalMessage.take(110),
          timestamp = System.currentTimeMillis(),
          type = "DEVICE"
        ))
      } catch (cancelled: CancellationException) {
        TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Automation coroutine cancelled", "task=${task.take(100)}")
        TheoDiagnosticLogger.info("SCREEN_AUTOMATION", "Task stopped by user")
      } catch (e: Exception) {
        TheoDiagnosticLogger.error("SCREEN_AUTOMATION", "Screen automation failed", e)
        appendAssistantMessage("Screen automation stopped after an unexpected error. No further steps were attempted: ${e.localizedMessage ?: "unknown error"}.", badge = "SCREEN AUTOMATION")
      } finally {
        com.example.service.TheoAutomationForegroundService.stop(getApplication<Application>())
        screenAutomationPaused = false
        TheoAccessibilityService.instance?.setAutomationPaused(false)
        prefs.edit()
          .putBoolean("screen_automation_in_progress", false)
          .remove("screen_automation_started_at")
          .remove("screen_automation_target_package")
          .apply()
        TheoDiagnosticLogger.info(
          "SCREEN_AUTOMATION",
          "Automation coroutine finished",
          "task=${task.take(100)}; durationMs=${System.currentTimeMillis() - automationStartedAt}; finalMessage=${finalMessage.take(180)}; pausedForReview=$pausedForReview"
        )
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

  private fun publishAutomationOverlay(
    task: String,
    status: String,
    detail: String,
    autoHideMs: Long = 0L
  ) {
    TheoAccessibilityService.instance?.showAutomationOverlay(
      title = task.take(100),
      status = status.take(180),
      detail = detail.take(220),
      autoHideMs = autoHideMs
    )
  }

  private fun isSafePlannedBatch(
    task: String,
    actions: List<com.example.service.ScreenAction>
  ): Boolean {
    if (actions.size !in 2..3) return false
    if (ActionSafetyPolicy.requiresConfirmation("screen:current|task:$task")) return false
    if (Regex("""(?i)\b(permission|settings|enable|disable|grant access|login|log in|sign in|sign up|account|password|passcode|otp|verification|authenticate)\b""").containsMatchIn(task)) return false
    val nonBatchableTarget = Regex("""(?i)\b(save|submit|confirm|approve|accept|allow|send|publish|post|delete|remove|checkout|purchase|payment)\b""")
    return actions.all { action ->
      if (nonBatchableTarget.containsMatchIn(action.targetLabel)) return@all false
      if (action.kind !in setOf("click", "type", "scroll")) return@all false
      if (action.nodePath.isNullOrBlank() || action.targetLabel.isBlank()) return@all false
      if (ActionSafetyPolicy.targetRequiresConfirmation(action, action.targetLabel)) return@all false
      if (action.kind == "type") {
        val textToType = action.text?.trim().orEmpty()
        if (textToType.isBlank() || !task.contains(textToType, ignoreCase = true)) return@all false
      }
      true
    }
  }

  private fun isBatchedActionApplicable(
    action: com.example.service.ScreenAction,
    snapshot: com.example.service.ScreenSnapshot
  ): Boolean {
    if (action.kind !in setOf("click", "type", "scroll")) return false
    val path = action.nodePath ?: return false
    val expectedLabel = action.targetLabel.trim()
    if (expectedLabel.isBlank()) return false
    val element = snapshot.elements.firstOrNull { it.id == path } ?: return false
    if (!element.enabled) return false
    val actualLabel = element.label.trim()
    if (
      !actualLabel.equals(expectedLabel, ignoreCase = true) &&
      !actualLabel.contains(expectedLabel, ignoreCase = true)
    ) return false

    return when (action.kind) {
      "click" -> element.clickable
      "type" -> element.editable && !action.text.isNullOrBlank()
      "scroll" -> element.scrollable
      else -> false
    }
  }

  private suspend fun awaitAutomationResume(task: String) {
    while (screenAutomationPaused) {
      kotlinx.coroutines.currentCoroutineContext().ensureActive()
      publishAutomationOverlay(
        task = task,
        status = "Paused · waiting for Resume",
        detail = "Theo will not dispatch actions while paused."
      )
      delay(120L)
    }
  }

  fun toggleScreenAutomationPause(): Boolean {
    if (screenAutomationJob?.isActive != true) return false
    screenAutomationPaused = !screenAutomationPaused
    TheoAccessibilityService.instance?.setAutomationPaused(screenAutomationPaused)
    publishAutomationOverlay(
      task = "Theo screen automation",
      status = if (screenAutomationPaused) "Paused" else "Resuming · refreshing screen",
      detail = if (screenAutomationPaused) {
        "No new actions will be dispatched until you resume."
      } else {
        "Theo will inspect the current screen again before continuing."
      }
    )
    TheoDiagnosticLogger.info("SCREEN_AUTOMATION", if (screenAutomationPaused) "Task paused by user" else "Task resumed by user")
    return screenAutomationPaused
  }

  fun stopScreenAutomation() {
    screenAutomationJob?.cancel()
    screenAutomationJob = null
    com.example.service.TheoAutomationForegroundService.stop(getApplication<Application>())
    TheoAccessibilityService.instance?.showAutomationOverlay(
      title = "Task stopped",
      status = "Stopped by you",
      detail = "Theo will not send any more actions.",
      autoHideMs = 2500L
    )
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
    if (_uiState.value.isSpeechEnabled) {
      val spokenStatus = if (badge == "SCREEN AUTOMATION") {
        when {
          text.contains("judged the task complete", ignoreCase = true) ->
            "Theo finished its steps, but the result is not verified. Check your screen."
          text.contains("stopped", ignoreCase = true) || text.contains("failed", ignoreCase = true) ->
            "Theo stopped the task. Check the chat for details."
          else -> "Theo's screen task ended. Check the chat for details."
        }
      } else text
      speechManager.speak(spokenStatus)
    }
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

  fun prepareLocalQwen() {
    if (_uiState.value.isTestingQwen) return
    TheoDiagnosticLogger.info("LOCAL_QWEN", "Native model preparation requested from Settings")
    _uiState.update { it.copy(isTestingQwen = true, qwenStatusMessage = "Preparing native Qwen runtime…") }
    viewModelScope.launch {
      try {
        val message = localQwenRuntime.prepare { progress ->
          _uiState.update { it.copy(qwenStatusMessage = progress) }
        }
        TheoDiagnosticLogger.info("LOCAL_QWEN", "Native model preparation completed", message)
        _uiState.update { it.copy(qwenStatusMessage = message) }
      } catch (cancelled: CancellationException) {
        _uiState.update { it.copy(qwenStatusMessage = "Local Qwen preparation cancelled.") }
        throw cancelled
      } catch (error: OutOfMemoryError) {
        localQwenRuntime.release()
        TheoDiagnosticLogger.error("LOCAL_QWEN", "Native model preparation ran out of memory", error)
        _uiState.update {
          it.copy(qwenStatusMessage = "Failed to prepare local Qwen: Android ran out of memory. Close background apps and retry.")
        }
      } catch (error: Throwable) {
        TheoDiagnosticLogger.error("LOCAL_QWEN", "Native model preparation failed", error)
        val message = error.localizedMessage ?: error.javaClass.simpleName
        _uiState.update { it.copy(qwenStatusMessage = "Failed to prepare local Qwen: $message") }
      } finally {
        _uiState.update { it.copy(isTestingQwen = false) }
      }
    }
  }

  fun updateSettings(
    model: AiModelType,
    intensity: AnimationIntensity,
    apiKey: String,
    groqKey: String = _uiState.value.groqApiKey
  ) {
    prefs.edit()
      .putString("active_model", model.name)
      .putString("anim_intensity", intensity.name)
      .putString("gemini_api_key", apiKey)
      .putString("groq_api_key", groqKey)
      .remove("termux_host")
      .apply()

    _uiState.update {
      it.copy(
        activeModel = model,
        animationIntensity = intensity,
        customApiKey = apiKey,
        groqApiKey = groqKey,
        groqStatusMessage = if (groqKey != it.groqApiKey) "Not tested yet" else it.groqStatusMessage,
        geminiStatusMessage = if (apiKey != it.customApiKey) "Not tested yet" else it.geminiStatusMessage
      )
    }
  }

  override fun onCleared() {
    if (com.example.service.TheoAutomationForegroundService.onStopRequested != null) {
      com.example.service.TheoAutomationForegroundService.onStopRequested = null
    }
    TheoDiagnosticLogger.info("VIEW_MODEL", "TheoViewModel cleared")
    super.onCleared()
    speechManager.shutdown()
    localQwenRuntime.release()
  }
}
