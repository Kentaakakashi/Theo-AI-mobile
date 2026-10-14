package com.example.viewmodel

import android.app.Application
import android.content.Context
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
import com.example.service.DeviceActionHandler
import com.example.service.TheoAiEngine
import com.example.service.TheoDiagnosticLogger
import com.example.service.TheoSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
  val pendingActionTag: String? = null
)

class TheoViewModel(application: Application) : AndroidViewModel(application) {

  private val database = TheoDatabase.getDatabase(application)
  private val dao = database.theoDao()
  private val aiEngine = TheoAiEngine()
  private val prefs = application.getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)

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
      val isSensitiveHandoff = actionKey?.startsWith("order:", ignoreCase = true) == true
      val needsMultiStepReview = actionKey?.startsWith("sequence:", ignoreCase = true) == true
      // Safe, single-step app/system/music intents execute immediately. Orders stay
      // behind confirmation; compound app+media requests stay user-triggered until
      // the requested app's playback capability can be verified.
      val executeAutomatically = actionKey != null && !isSensitiveHandoff && !needsMultiStepReview

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
          pendingActionTag = if (isSensitiveHandoff || needsMultiStepReview) actionKey else null,
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
    val success = DeviceActionHandler.executeAction(getApplication(), actionKey)
    if (success) {
      viewModelScope.launch {
        dao.insertActivity(
          ActivityEntity(
            id = UUID.randomUUID().toString(),
            title = "Device Intent Dispatched",
            subtitle = "Target: $actionKey",
            timestamp = System.currentTimeMillis(),
            type = "DEVICE"
          )
        )
      }
    }
    _uiState.update { it.copy(pendingActionTag = null) }
  }

  fun dismissPendingAction() {
    _uiState.update { it.copy(pendingActionTag = null) }
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
