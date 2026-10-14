package com.example.service

import org.json.JSONObject
import java.util.Locale

data class ScreenElement(
  val id: String,
  val label: String,
  val role: String,
  val resourceId: String,
  val clickable: Boolean,
  val editable: Boolean,
  val scrollable: Boolean,
  val enabled: Boolean
)

data class ScreenSnapshot(val packageName: String, val elements: List<ScreenElement>) {
  fun toModelText(maxChars: Int = 12000): String = buildString {
    append("package=").append(packageName).append('\n')
    elements.take(120).forEach { element ->
      append(JSONObject()
        .put("node", element.id)
        .put("label", element.label)
        .put("role", element.role)
        .put("resourceId", element.resourceId)
        .put("clickable", element.clickable)
        .put("editable", element.editable)
        .put("scrollable", element.scrollable)
        .put("enabled", element.enabled))
      append('\n')
    }
  }.take(maxChars)
}

data class ScreenAction(
  val kind: String,
  val nodePath: String? = null,
  val text: String? = null,
  val direction: String? = null,
  val reason: String = ""
)

internal data class ScreenPlanResult(val action: ScreenAction? = null, val error: String? = null)

internal data class ScreenAutomationRequest(val appName: String?, val task: String) {
  companion object {
    fun parse(actionKey: String): ScreenAutomationRequest? {
      if (!actionKey.startsWith("screen:", ignoreCase = true)) return null
      val payload = actionKey.substringAfter(':')
      val delimiter = payload.indexOf("|task:", ignoreCase = true)
      if (delimiter < 0) return null
      val target = payload.substring(0, delimiter).trim()
      val task = payload.substring(delimiter + "|task:".length).trim()
      if (task.isBlank()) return null
      val appName = when {
        target.equals("current", ignoreCase = true) -> null
        target.startsWith("app:", ignoreCase = true) -> target.substringAfter(':').trim().takeIf { it.isNotBlank() }
        else -> return null
      }
      if (target.startsWith("app:", ignoreCase = true) && appName == null) return null
      return ScreenAutomationRequest(appName, task)
    }
  }
}

internal object ScreenActionParser {
  private val nodePathPattern = Regex("^\\d+(?:\\.\\d+){0,12}$")

  fun parse(rawResponse: String): ScreenAction? {
    val start = rawResponse.indexOf('{')
    val end = rawResponse.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val json = try { JSONObject(rawResponse.substring(start, end + 1)) } catch (_: Exception) { return null }
    val kind = json.optString("action").trim().lowercase(Locale.ROOT)
    val node = json.optString("node").trim().takeUnless { it.isBlank() || it == "null" }
    val text = json.optString("text").takeIf { it.isNotBlank() && it != "null" }?.take(1000)
    val direction = json.optString("direction").trim().lowercase(Locale.ROOT)
    val reason = json.optString("reason").trim().take(300)

    return when (kind) {
      "click" -> if (node != null && nodePathPattern.matches(node)) ScreenAction("click", nodePath = node, reason = reason) else null
      "type" -> if (node != null && nodePathPattern.matches(node) && text != null) ScreenAction("type", nodePath = node, text = text, reason = reason) else null
      "scroll" -> if (node != null && nodePathPattern.matches(node) && direction in setOf("forward", "backward")) ScreenAction("scroll", nodePath = node, direction = direction, reason = reason) else null
      "back" -> ScreenAction("back", reason = reason)
      "wait" -> ScreenAction("wait", reason = reason)
      "done", "finished" -> ScreenAction("done", reason = reason)
      "clarify", "ask_user" -> ScreenAction("clarify", reason = reason.ifBlank { "The task needs clarification." })
      else -> null
    }
  }
}

internal object ScreenTextSanitizer {
  private val contextualCode = Regex(
    """(?i)\b((?:verification|authentication|security|one[- ]time)\s+)?(code|otp|passcode|password)\b\s*[:\-]?\s*[A-Za-z0-9!@#%^&*_-]{4,24}\b"""
  )

  fun sanitize(value: String): String {
    val trimmed = value.trim()
    if (Regex("""^\d{4,8}$""").matches(trimmed)) {
      return "[possible verification code redacted]"
    }
    return contextualCode.replace(trimmed) { match ->
      (match.groupValues[1] + match.groupValues[2]).trim() + " [redacted]"
    }
  }
}


internal object ActionSafetyPolicy {
  private val sensitiveIntent = Regex(
    "\\b(send|message|dm|email|reply|comment|submit|post|publish|delete|remove|clear|buy|purchase|pay|checkout|order|transfer|share|upload|install|uninstall|password|passcode|one[- ]time code|otp|bank|payment|grant permission)\\b",
    RegexOption.IGNORE_CASE
  )
  private val sensitiveTarget = Regex(
    "\\b(send|submit|reply|comment|post|publish|delete|remove|clear|buy|purchase|pay|checkout|place order|transfer|share|upload|confirm payment|confirm purchase|install|uninstall|allow access|grant permission|password|passcode|one[- ]time code|otp|card number|bank)\\b",
    RegexOption.IGNORE_CASE
  )

  fun requiresConfirmation(actionKey: String): Boolean {
    if (actionKey.startsWith("order:", ignoreCase = true)) return true
    val task = ScreenAutomationRequest.parse(actionKey)?.task ?: actionKey
    return sensitiveIntent.containsMatchIn(task)
  }

  fun targetRequiresConfirmation(action: ScreenAction, targetLabel: String): Boolean =
    action.kind in setOf("click", "type") && sensitiveTarget.containsMatchIn(targetLabel)
}
