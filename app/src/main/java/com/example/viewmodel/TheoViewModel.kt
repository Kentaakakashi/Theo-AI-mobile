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
import com.example.service.TheoSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
      if (isSpeaking) {
        current.copy(assistantState = AssistantState.SPEAKING)
      } else {
        if (current.assistantState == AssistantState.SPEAKING) {
          current.copy(assistantState = AssistantState.IDLE)
        } else current
      }
    }
  }

  init {
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
      if (messages.value.isEmpty()) {
        dao.insertMessage(
          MessageEntity(
            id = "welcome-msg",
            text = "Neural Core initialized. Greetings, Operator. I am **Theo**, your cybernetic Android AI companion.\n\nDual-bridge intelligence is active (Gemini Cloud + Termux Qwen 1.7B). Memory matrix is synced.",
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
    val text = (promptText ?: _uiState.value.inputText).trim()
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

    _uiState.update {
      it.copy(
        inputText = "",
        assistantState = AssistantState.THINKING
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

      val result = aiEngine.processQuery(
        prompt = text,
        activeModel = _uiState.value.activeModel,
        customApiKey = _uiState.value.customApiKey,
        termuxHost = _uiState.value.termuxHost,
        memories = currentMemories
      )

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

      val assistantMsg = MessageEntity(
        id = UUID.randomUUID().toString(),
        text = result.responseText,
        isUser = false,
        timestamp = System.currentTimeMillis(),
        modelBadge = result.modelBadge,
        latencyMs = result.latencyMs,
        actionTag = result.actionSuggested
      )
      dao.insertMessage(assistantMsg)

      _uiState.update {
        it.copy(
          assistantState = AssistantState.IDLE,
          pendingActionTag = result.actionSuggested
        )
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

  fun testQwenConnection() {
    _uiState.update { it.copy(isTestingQwen = true, qwenStatusMessage = "Pinging socket...") }
    viewModelScope.launch {
      val (success, message) = aiEngine.testQwenConnection(_uiState.value.termuxHost)
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
        termuxHost = termuxHost
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    speechManager.shutdown()
  }
}
