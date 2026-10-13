package com.example.model

enum class AssistantState(val label: String) {
  IDLE("ONLINE — STANDBY"),
  LISTENING("SENSORS ACTIVE"),
  THINKING("PROCESSING SYNAPSE"),
  SPEAKING("AUDIO STREAMING"),
  OFFLINE("LOCAL SAFE MODE")
}

enum class AiModelType(val displayName: String, val shortBadge: String, val description: String) {
  GEMINI_FLASH("Gemini 3.5 Flash-Lite", "GEMINI FAST", "Low-latency cloud responses optimized for everyday chat"),
  GEMINI_PRO("Gemini 3.1 Pro (Preview)", "GEMINI PRO", "Advanced reasoning for complex analysis and coding"),
  LOCAL_QWEN("Qwen3-1.7B Local", "TERMUX LOCAL", "On-device private inference via localhost:8080 (Termux)")
}

enum class AnimationIntensity(val label: String) {
  HIGH("Cybernetic (Full Particle & Orbitals)"),
  BALANCED("Optimized (Smooth Pulse)"),
  BATTERY_SAVER("Minimal (Low Draw)")
}

data class ChatMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val text: String,
  val isUser: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val modelBadge: String = "THEO CORE",
  val latencyMs: Long = 0L,
  val actionTag: String? = null
)

data class MemoryItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val category: String, // e.g. "Identity", "Preference", "Project", "Device"
  val key: String,
  val value: String,
  val createdAt: Long = System.currentTimeMillis()
)

data class ActivityLog(
  val id: String = java.util.UUID.randomUUID().toString(),
  val title: String,
  val subtitle: String,
  val timestamp: Long = System.currentTimeMillis(),
  val type: String // "CHAT", "MEMORY", "DEVICE", "SYSTEM"
)

data class DeviceAction(
  val title: String,
  val description: String,
  val iconName: String,
  val actionType: String
)
