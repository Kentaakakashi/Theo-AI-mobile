package com.example.service

/**
 * Recognizes Android handoff intent only. It never maps app names to hardcoded packages,
 * guesses UI controls, or claims an action succeeded.
 */
internal object ActionIntentParser {
  fun parse(input: String): String? {
    val trimmed = input.trim().trimEnd('.', '!', '?')
    if (trimmed.isBlank()) return null
    val flags = RegexOption.IGNORE_CASE

    fun match(pattern: String): MatchResult? = Regex(pattern, flags).matchEntire(trimmed)

    val explicitExtraDim = Regex(
      """^(?:please\s+)?(?:turn|switch)\s+(off|on|disable|enable)\s+(?:(?:my|the)\s+)?extra\s+dim(?:\s+feature)?$""",
      flags
    ).matchEntire(trimmed)
    if (explicitExtraDim != null) {
      val mode = explicitExtraDim.groupValues[1].lowercase()
      val normalizedMode = if (mode == "off" || mode == "disable") "off" else "on"
      SystemUiAutomationBridge.requestNotificationShade()
      return "screen:current|task:turn $normalizedMode Extra Dim"
    }

    val extraDimTrailingMode = Regex(
      """^(?:please\s+)?(?:turn|switch)\s+(?:(?:my|the)\s+)?extra\s+dim\s+(off|on|disable|enable)$""",
      flags
    ).matchEntire(trimmed)
    if (extraDimTrailingMode != null) {
      val mode = extraDimTrailingMode.groupValues[1].lowercase()
      val normalizedMode = if (mode == "off" || mode == "disable") "off" else "on"
      SystemUiAutomationBridge.requestNotificationShade()
      return "screen:current|task:turn $normalizedMode Extra Dim"
    }

    val followUpExtraDim = Regex(
      """^(?:please\s+)?(?:turn|switch)\s+(?:it|that)\s+(off|on|disable|enable)$""",
      flags
    ).matchEntire(trimmed)
    if (followUpExtraDim != null && ConversationContext.previousUserMentions("extra dim")) {
      val normalizedMode = if (followUpExtraDim.groupValues[1].equals("off", true) || followUpExtraDim.groupValues[1].equals("disable", true)) "off" else "on"
      SystemUiAutomationBridge.requestNotificationShade()
      return "screen:current|task:turn $normalizedMode Extra Dim"
    }

    // Screen inspection is a native Android action, not a prompt for the chat model to guess.
    if (Regex(
        "^(?:please\\s+)?(?:what(?:'s| is)\\s+(?:on|in)\\s+(?:my\\s+)?(?:current\\s+)?screen|(?:look\\s+at|inspect|read|describe|analy[sz]e|check)\\s+(?:(?:my|the)\\s+)?(?:current\\s+)?screen|(?:can\\s+you\\s+)?see\\s+(?:my\\s+)?screen|what\\s+can\\s+you\\s+see\\s+on\\s+(?:my\\s+)?screen)$",
        flags
      ).matches(trimmed)) return "inspect-screen"

    when {
      match("^(please\\s+)?(open|launch)\\s+(the\\s+)?settings$") != null -> return "settings"
      match("^(please\\s+)?(open|launch)\\s+(the\\s+)?camera$") != null ||
        match("^(please\\s+)?take\\s+(a\\s+)?photo$") != null -> return "camera"
      match("^(please\\s+)?(set|open)\\s+(an?\\s+)?alarm$") != null ||
        match("^(please\\s+)?open\\s+(the\\s+)?clock$") != null -> return "clock"
      match("^(please\\s+)?(?:(?:open|launch)\\s+(?:the\\s+)?)?(?:wi-?fi|wireless)(?:\\s+(?:settings|settings page))?$") != null -> return "wifi"
      match("^(please\\s+)?(?:(?:open|launch|show|check|get)\\s+(?:the\\s+|my\\s+)?)?(?:battery\\s+(?:stats|statistics|settings)|battery and system diagnostics)$") != null -> return "battery"
      match("^(please\\s+)?(?:open|launch|show)\\s+(?:the\\s+)?display(?:\\s+settings)?$") != null -> return "display"
    }

    val order = match("^(?:please\\s+)?(?:order|buy|get\\s+me|place\\s+an\\s+order\\s+for|make\\s+(?:me\\s+)?an\\s+order\\s+for)\\s+(.+)$")
    if (order != null) {
      val query = cleanTarget(order.groupValues[1]).removePrefix("me ").trim()
      if (query.isNotBlank()) return "order:$query"
    }

    // Parse the app separately from the user's follow-up task. The live AI planner resolves
    // how to interact with each app from its current accessibility tree.
    val compound = match("^(?:please\\s+)?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)\\s+and\\s+(.+)$")
    if (compound != null && isAutomationGoal(compound.groupValues[2])) {
      val app = cleanTarget(compound.groupValues[1])
      val task = compound.groupValues[2].trim()
      if (app.isNotBlank() && task.isNotBlank()) return "screen:app:$app|task:$task"
    }

    val music = match("^(?:please\\s+)?(?:play|put\\s+on|listen\\s+to)\\s+(.+)$")
    if (music != null) {
      val query = cleanTarget(music.groupValues[1])
        .replace(Regex("^(?:the\\s+)?song\\s+", flags), "")
        .replace(Regex("\\s+(?:please|now)$", flags), "")
      if (query.isNotBlank() && query.lowercase() !in setOf("something", "a song", "some music", "music")) {
        return "music:$query"
      }
      if (query.lowercase() in setOf("some music", "music")) return "music:popular music"
    }

    // Requests against the already-open app use runtime UI discovery, not fixed buttons.
    val onScreen = match("^(?:please\\s+)?(?:tap|click|press|scroll|swipe|type|enter|search|find|look for|check|view|show|read|navigate|send|message|post|publish|reply|compose|select|choose|delete|remove|share|upload|follow|unfollow)\\b.*$")
    if (onScreen != null) return "screen:current|task:$trimmed"

    val app = match("^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:(?:the\\s+)?app\\s+(?:named\\s+|called\\s+)?|the\\s+)?(.+?)(?:\\s+app)?$")
      ?: return null
    val appName = cleanTarget(app.groupValues[1])
    if (appName.isBlank() || appName.lowercase() in setOf("settings", "camera", "clock", "alarm", "wifi", "wireless", "display", "battery")) return null
    return "app:$appName"
  }

  private fun isAutomationGoal(value: String): Boolean =
    Regex(
      "^(?:play|put\\s+on|listen\\s+to|search|find|look\\s+for|check|view|show|read|tap|click|press|scroll|swipe|type|enter|send|message|post|publish|reply|compose|select|choose|navigate|go\\s+to|open|launch|delete|remove|share|upload|follow|unfollow)\\b",
      RegexOption.IGNORE_CASE
    ).containsMatchIn(value.trim())

  private fun cleanTarget(value: String): String =
    value.trim().replace(Regex("^[\\\"']|[\\\"']$"), "").trim()
}
