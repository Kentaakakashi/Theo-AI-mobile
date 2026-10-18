package com.example.service

internal object ConversationContext {
  private data class Turn(val role: String, val text: String)
  private const val MAX_TURNS = 12
  private val lock = Any()
  private val turns = ArrayDeque<Turn>()

  fun recordUser(text: String) = record("User", text)
  fun recordAssistant(text: String) = record("Theo", text)

  private fun record(role: String, text: String) {
    val clean = text.trim()
    if (clean.isBlank()) return
    synchronized(lock) {
      turns.addLast(Turn(role, clean.take(2000)))
      while (turns.size > MAX_TURNS) turns.removeFirst()
    }
  }

  fun previousUserMentions(phrase: String): Boolean {
    val needle = phrase.trim().lowercase()
    if (needle.isBlank()) return false
    synchronized(lock) {
      return turns.dropLast(1).any { it.role == "User" && it.text.lowercase().contains(needle) }
    }
  }

  fun forPrompt(maxTurns: Int = 8): String = synchronized(lock) {
    turns.takeLast(maxTurns).joinToString("\n") { "${it.role}: ${it.text}" }.take(7000)
  }
}
