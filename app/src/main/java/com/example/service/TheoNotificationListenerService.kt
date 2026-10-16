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
enum class PlaybackVerificationStatus {
  PLAYING,
  NOT_PLAYING,
  TRACK_NOT_FOUND,
  ACCESS_UNAVAILABLE
}

data class PlaybackVerificationResult(
  val status: PlaybackVerificationStatus,
  val requestedTrack: String,
  val title: String = "",
  val artist: String = "",
  val packageName: String = "",
  val explanation: String
) {
  val verified: Boolean get() = status == PlaybackVerificationStatus.PLAYING
}

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

  /**
   * Independently verifies an explicitly requested track using Android media sessions.
   * This is an on-demand read; results are never stored or logged.
   */
  fun verifyRequestedTrack(
    requestedTrack: String,
    expectedPackageName: String? = null
  ): PlaybackVerificationResult {
    if (requestedTrack.isBlank()) {
      return PlaybackVerificationResult(
        PlaybackVerificationStatus.TRACK_NOT_FOUND, requestedTrack,
        explanation = "Theo could not identify the requested track title from the task."
      )
    }
    if (!isEnabled(this)) {
      return PlaybackVerificationResult(
        PlaybackVerificationStatus.ACCESS_UNAVAILABLE, requestedTrack,
        explanation = "Android Notification access is not enabled for Theo."
      )
    }
    return try {
      val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        ?: return PlaybackVerificationResult(
          PlaybackVerificationStatus.ACCESS_UNAVAILABLE, requestedTrack,
          explanation = "Android did not expose its media-session manager."
        )
      val controllers = manager.getActiveSessions(
        ComponentName(this, TheoNotificationListenerService::class.java)
      )
      if (controllers.isEmpty()) {
        return PlaybackVerificationResult(
          PlaybackVerificationStatus.TRACK_NOT_FOUND, requestedTrack,
          explanation = "Android currently exposes no active media sessions."
        )
      }

      data class Candidate(
        val title: String,
        val artist: String,
        val packageName: String,
        val playbackState: Int?
      )

      val candidates = controllers.mapNotNull { controller ->
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        if (title.isBlank()) null else Candidate(
          title = title,
          artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().trim(),
          packageName = controller.packageName.orEmpty(),
          playbackState = controller.playbackState?.state
        )
      }
      val requested = normalizeTrackTitle(requestedTrack)
      if (requested.isBlank()) {
        return PlaybackVerificationResult(
          PlaybackVerificationStatus.TRACK_NOT_FOUND,
          requestedTrack,
          explanation = "The requested track title did not contain searchable letters or numbers."
        )
      }
      val matching = candidates.filter { candidate ->
        val actual = normalizeTrackTitle(candidate.title)
        actual == requested || actual.contains(requested) || requested.contains(actual)
      }
      val expected = expectedPackageName?.takeIf { it.isNotBlank() }
      val selected = if (expected == null) {
        matching.firstOrNull { it.playbackState == PlaybackState.STATE_PLAYING }
          ?: matching.firstOrNull()
      } else {
        matching.firstOrNull { it.packageName == expected && it.playbackState == PlaybackState.STATE_PLAYING }
          ?: matching.firstOrNull { it.packageName == expected }
      }

      if (selected == null) {
        val otherPlayerMatch = matching.firstOrNull()
        return PlaybackVerificationResult(
          PlaybackVerificationStatus.TRACK_NOT_FOUND,
          requestedTrack,
          title = otherPlayerMatch?.title.orEmpty(),
          artist = otherPlayerMatch?.artist.orEmpty(),
          packageName = otherPlayerMatch?.packageName.orEmpty(),
          explanation = if (otherPlayerMatch != null && expected != null) {
            "The requested track appears in a different player (${otherPlayerMatch.packageName}), not the expected app ($expected)."
          } else {
            "No active media session exposed a title matching the requested track."
          }
        )
      }

      val status = when (selected.playbackState) {
        PlaybackState.STATE_PLAYING -> PlaybackVerificationStatus.PLAYING
        else -> PlaybackVerificationStatus.NOT_PLAYING
      }
      val stateDescription = when (selected.playbackState) {
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_NONE, null -> "not reporting active playback"
        else -> "not confirmed as playing"
      }
      PlaybackVerificationResult(
        status = status,
        requestedTrack = requestedTrack,
        title = selected.title,
        artist = selected.artist,
        packageName = selected.packageName,
        explanation = if (status == PlaybackVerificationStatus.PLAYING) {
          "Android media-session state confirms the requested track is playing."
        } else {
          "The requested track is visible in the media session, but it is $stateDescription."
        }
      )
    } catch (error: SecurityException) {
      PlaybackVerificationResult(
        PlaybackVerificationStatus.ACCESS_UNAVAILABLE, requestedTrack,
        explanation = "Android denied media-session access. Recheck Theo's Notification access permission."
      )
    } catch (error: Exception) {
      TheoDiagnosticLogger.warn(
        "PLAYBACK_VERIFY", "Could not inspect media session", error.javaClass.simpleName
      )
      PlaybackVerificationResult(
        PlaybackVerificationStatus.ACCESS_UNAVAILABLE, requestedTrack,
        explanation = "Media-session verification failed (${error.javaClass.simpleName})."
      )
    }
  }

  private fun normalizeTrackTitle(value: String): String =
    value.lowercase(java.util.Locale.ROOT)
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .trim()

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
