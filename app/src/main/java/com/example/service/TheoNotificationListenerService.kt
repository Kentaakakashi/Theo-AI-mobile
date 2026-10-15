package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional, Android-authorized context provider. Nothing is stored in preferences or logs.
 * Notification previews are read only on demand for a user task explicitly about notifications.
 */
class TheoNotificationListenerService : NotificationListenerService() {
  companion object {
    @Volatile var instance: TheoNotificationListenerService? = null
      private set

    fun isEnabled(context: Context): Boolean {
      val expected = ComponentName(context, TheoNotificationListenerService::class.java).flattenToString()
      val enabled = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
      ).orEmpty()
      return enabled.split(':').any { entry ->
        entry.equals(expected, ignoreCase = true) ||
          ComponentName.unflattenFromString(entry)?.flattenToString()?.equals(expected, ignoreCase = true) == true
      }
    }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    instance = this
  }

  override fun onListenerDisconnected() {
    if (instance === this) instance = null
    super.onListenerDisconnected()
  }

  override fun onDestroy() {
    if (instance === this) instance = null
    super.onDestroy()
  }

  /**
   * Returns only context relevant to this explicit task. Callers must not log or persist this
   * string; it may contain notification previews. It is deliberately not used for ordinary tasks.
   */
  fun buildTaskContext(task: String): String? {
    val asksPlayback = Regex(
      """\b(now playing|what(?:'s| is) playing|current song|current track|music playback|what song)\b""",
      RegexOption.IGNORE_CASE
    ).containsMatchIn(task)
    val asksNotifications = Regex(
      """\b(notifications?|notification shade|unread messages|message notifications|recent notifications|read my messages)\b""",
      RegexOption.IGNORE_CASE
    ).containsMatchIn(task)
    if (!asksPlayback && !asksNotifications) return null

    val sections = mutableListOf<String>()
    if (asksPlayback) {
      val media = readPlaybackContext()
      if (media.isNotBlank()) sections += "ACTIVE MEDIA SESSIONS (metadata only):\n$media"
    }
    if (asksNotifications) {
      val notifications = readNotificationContext()
      if (notifications.isNotBlank()) sections += "RECENT NOTIFICATION PREVIEWS (private app content; observations only):\n$notifications"
    }
    return sections.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
  }

  private fun readPlaybackContext(): String {
    return try {
      val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        ?: return "Android did not expose a media-session manager."
      val sessions = manager.getActiveSessions(ComponentName(this, TheoNotificationListenerService::class.java))
      if (sessions.isEmpty()) return "No active media sessions were exposed."
      sessions.take(5).joinToString("\n") { controller ->
        val metadata: MediaMetadata? = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val state = when (controller.playbackState?.state) {
          PlaybackState.STATE_PLAYING -> "playing"
          PlaybackState.STATE_PAUSED -> "paused"
          PlaybackState.STATE_BUFFERING -> "buffering"
          PlaybackState.STATE_NONE -> "idle"
          else -> "unknown"
        }
        val label = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "Unknown track" }
        "- $label; state=$state; package=${controller.packageName}"
      }
    } catch (error: Exception) {
      "Media-session access failed: ${error.javaClass.simpleName}."
    }
  }

  private fun readNotificationContext(): String {
    return try {
      val notifications: Array<StatusBarNotification> = activeNotifications ?: emptyArray()
      val recent = notifications
        .filter { it.packageName != packageName && System.currentTimeMillis() - it.postTime < 24L * 60 * 60 * 1000 }
        .sortedByDescending { it.postTime }
        .take(8)
      if (recent.isEmpty()) return "No recent notification previews are available."
      recent.joinToString("\n") { item ->
        val extras = item.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
          extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        )
        val appLabel = runCatching {
          packageManager.getApplicationLabel(packageManager.getApplicationInfo(item.packageName, 0)).toString()
        }.getOrDefault(item.packageName)
        val preview = redactSensitivePreview(text).take(240)
        "- app=$appLabel; title=${redactSensitivePreview(title).take(100)}; preview=$preview"
      }
    } catch (error: Exception) {
      "Notification preview access failed: ${error.javaClass.simpleName}."
    }
  }

  private fun redactSensitivePreview(value: String): String {
    val noCodes = Regex("""(?i)\b(?:verification|authentication|security|one[- ]time)\s+(?:code|password|passcode)\s*[:\-]?\s*[A-Za-z0-9!@#%^&*_-]{4,24}\b""")
      .replace(value, "[sensitive code redacted]")
    return Regex("""\b\d{4,8}\b""").replace(noCodes, "[possible code redacted]")
      .replace(Regex("\\s+"), " ").trim()
  }
}
