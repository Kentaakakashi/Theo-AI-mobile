package com.example.service

/**
 * Deterministic parser for actions Theo can execute natively.
 * This parser only identifies intent; it never claims an action succeeded.
 */
internal object ActionIntentParser {
  fun parse(input: String): String? {
    val trimmed = input.trim().trimEnd('.', '!', '?')
    if (trimmed.isBlank()) return null
    val flags = RegexOption.IGNORE_CASE

    fun match(pattern: String): MatchResult? = Regex(pattern, flags).matchEntire(trimmed)

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

    // Preserve a requested app separately from the song title. The action executor
    // decides whether the app can be opened and whether its playback integration exists.
    val compound = match("^(?:please\\s+)?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)\\s+and\\s+(?:play|put\\s+on|listen\\s+to)\\s+(.+)$")
    if (compound != null) {
      val app = cleanTarget(compound.groupValues[1])
      val song = cleanTarget(compound.groupValues[2]).replace(Regex("^(?:the\\\\s+)?song\\\\s+", flags), "").let(::cleanTarget)
      if (app.isNotBlank() && song.isNotBlank()) return "sequence:app:$app|music:$song"
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

    val order = match("^(?:please\\s+)?(?:order|buy|get\\s+me|place\\s+an\\s+order\\s+for|make\\s+(?:me\\s+)?an\\s+order\\s+for)\\s+(.+)$")
    if (order != null) {
      val query = cleanTarget(order.groupValues[1]).removePrefix("me ").trim()
      if (query.isNotBlank()) return "order:$query"
    }

    val app = match("^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:(?:the\\s+)?app\\s+(?:named\\s+|called\\s+)?|the\\s+)?(.+?)(?:\\s+app)?$")
      ?: return null
    val appName = cleanTarget(app.groupValues[1])
    if (appName.isBlank() || appName.lowercase() in setOf("settings", "camera", "clock", "alarm", "wifi", "wireless", "display", "battery")) return null
    return "app:$appName"
  }

  private fun cleanTarget(value: String): String =
    value.trim().replace(Regex("^[\\\"']|[\\\"']$"), "").trim()
}
