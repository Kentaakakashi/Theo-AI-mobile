package com.example.service

/**
 * Small, deterministic parsing helpers for task completion checks.
 * These functions only recognize an explicitly named track/message; they never claim success.
 */
internal data class MessageExpectation(
  val recipient: String,
  val body: String
)

internal object AutomationTaskVerifier {
  /**
   * Fetch notification/media context only for explicit, related requests.
   * General conversation and ordinary send-message tasks never read notification previews.
   */
  fun requestsDeviceContext(task: String): Boolean = Regex(
    """(?i)\b(notifications?|notification shade|what(?:'s| is) playing|now playing|current song|current track|music playback|what song|unread messages|message notifications|recent notifications|read my messages|message previews|who messaged me|who texted me)\b"""
  ).containsMatchIn(task)

  fun requestedTrack(task: String): String? {
    val quoted = Regex(
      """(?i)\b(?:song|track)\s+["“']([^"”']+)["”']"""
    ).find(task)?.groupValues?.getOrNull(1)?.trim()
    if (!quoted.isNullOrBlank()) return quoted

    val playMatch = Regex(
      """(?i)\bplay\s+(?:(?:the\s+)?(?:song|track)\s+)?["“']?(.+?)["”']?(?=\s+(?:in|on|using|through|via)\s+|[.!?,]|$)"""
    ).find(task)?.groupValues?.getOrNull(1)
    val candidate = playMatch
      ?.trim()
      ?.removeSurrounding("\"")
      ?.removeSurrounding("'")
      ?.replace(Regex("""(?i)\s+(?:please|now)$"""), "")
      ?.trimEnd('.', ',', ':')
      ?.trim()
    return candidate?.takeIf { it.isNotBlank() && !it.equals("music", ignoreCase = true) }
  }

  fun requestedMessage(task: String): MessageExpectation? {
    val patterns = listOf(
      Regex("""(?i)\b(?:send\s+a\s+message\s+to|send\s+message\s+to|message\s+to|message|dm)\s+(.+?)\s+saying\s+(.+?)\s*$"""),
      Regex("""(?i)\bsend\s+(.+?)\s+(?:a\s+)?message\s+saying\s+(.+?)\s*$""")
    )
    val match = patterns.firstNotNullOfOrNull { it.find(task) } ?: return null
    val recipient = match.groupValues.getOrNull(1)?.trim()
      ?.removeSurrounding("\"")?.removeSurrounding("'")?.trim().orEmpty()
    val body = match.groupValues.getOrNull(2)?.trim()
      ?.removeSurrounding("\"")?.removeSurrounding("'")
      ?.removeSurrounding("“", "”")?.trim().orEmpty()
    if (recipient.isBlank() || body.isBlank()) return null
    return MessageExpectation(recipient, body)
  }

  fun normalizeVisibleText(value: String): String =
    value.lowercase(java.util.Locale.ROOT)
      .replace(Regex("\\s+"), " ")
      .trim()

  /**
   * Match complete visible phrases rather than arbitrary substrings. Thus "hi" matches
   * "Hi!" but does not match the middle of "this".
   */
  fun containsVisiblePhrase(visibleText: String, expectedPhrase: String): Boolean {
    val visible = normalizePhraseForMatching(visibleText)
    val expected = normalizePhraseForMatching(expectedPhrase)
    if (visible.isBlank() || expected.isBlank()) return false
    return " $visible ".contains(" $expected ")
  }

  fun verifyRequestedMessage(expectation: MessageExpectation, visibleText: String): MessageVerificationResult =
    MessageVerificationResult(
      recipientVisible = containsVisiblePhrase(visibleText, expectation.recipient),
      bodyVisible = containsVisiblePhrase(visibleText, expectation.body)
    )

  private fun normalizePhraseForMatching(value: String): String =
    value.lowercase(java.util.Locale.ROOT)
      .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
}

internal data class MessageVerificationResult(
  val recipientVisible: Boolean,
  val bodyVisible: Boolean
) {
  val verified: Boolean get() = recipientVisible && bodyVisible
}
