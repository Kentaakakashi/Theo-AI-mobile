package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TheoLogLevel { DEBUG, INFO, WARN, ERROR }

data class TheoLogEntry(
  val id: Long,
  val timestamp: Long,
  val level: TheoLogLevel,
  val tag: String,
  val message: String,
  val details: String = ""
) {
  fun consoleLine(): String {
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    return "$time  ${level.name.padEnd(5)}  [$tag] $message" +
      if (details.isBlank()) "" else "\n$details"
  }
}

/**
 * On-device diagnostic console. Entries are stored locally and capped to avoid
 * unbounded storage. Never pass API keys, message bodies, or other secrets here.
 */
object TheoDiagnosticLogger {
  private const val PREFS = "theo_diagnostics"
  private const val KEY_ENTRIES = "entries"
  private const val MAX_ENTRIES = 500
  private const val MAX_DETAIL_CHARS = 12_000
  private lateinit var appContext: Context
  private val lock = Any()
  private val mutableEntries = MutableStateFlow<List<TheoLogEntry>>(emptyList())
  val entries: StateFlow<List<TheoLogEntry>> = mutableEntries

  fun initialize(context: Context) {
    synchronized(lock) {
      if (::appContext.isInitialized) return
      appContext = context.applicationContext
      mutableEntries.value = readStored()
      info("LIFECYCLE", "Diagnostic console initialized", "Stored entries: ${mutableEntries.value.size}")
    }
  }

  fun debug(tag: String, message: String, details: String = "") = log(TheoLogLevel.DEBUG, tag, message, details)
  fun info(tag: String, message: String, details: String = "") = log(TheoLogLevel.INFO, tag, message, details)
  fun warn(tag: String, message: String, details: String = "") = log(TheoLogLevel.WARN, tag, message, details)
  fun error(tag: String, message: String, throwable: Throwable? = null, details: String = "") {
    val stack = throwable?.let {
      val writer = StringWriter()
      it.printStackTrace(PrintWriter(writer))
      writer.toString()
    }.orEmpty()
    log(TheoLogLevel.ERROR, tag, message, listOf(details, stack).filter { it.isNotBlank() }.joinToString("\n").take(MAX_DETAIL_CHARS))
  }

  fun clear() {
    synchronized(lock) {
      mutableEntries.value = emptyList()
      if (::appContext.isInitialized) appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENTRIES).apply()
    }
    info("DIAGNOSTICS", "Console cleared")
  }

  fun exportText(): String = entries.value.asReversed().joinToString("\n\n") { it.consoleLine() }

  private fun log(level: TheoLogLevel, tag: String, message: String, details: String) {
    val entry = TheoLogEntry(System.nanoTime(), System.currentTimeMillis(), level, tag.take(48), message.take(1200), details.take(MAX_DETAIL_CHARS))
    when (level) {
      TheoLogLevel.DEBUG -> Log.d("Theo/$tag", message, null)
      TheoLogLevel.INFO -> Log.i("Theo/$tag", message)
      TheoLogLevel.WARN -> Log.w("Theo/$tag", message)
      TheoLogLevel.ERROR -> Log.e("Theo/$tag", message + if (details.isBlank()) "" else "\n$details")
    }
    synchronized(lock) {
      val updated = (mutableEntries.value + entry).takeLast(MAX_ENTRIES)
      mutableEntries.value = updated
      if (::appContext.isInitialized) {
        val array = JSONArray()
        updated.forEach { e ->
          array.put(JSONObject().apply {
            put("id", e.id); put("timestamp", e.timestamp); put("level", e.level.name)
            put("tag", e.tag); put("message", e.message); put("details", e.details)
          })
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENTRIES, array.toString()).apply()
      }
    }
  }

  private fun readStored(): List<TheoLogEntry> {
    val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        TheoLogEntry(
          id = o.optLong("id", i.toLong()),
          timestamp = o.optLong("timestamp", System.currentTimeMillis()),
          level = runCatching { TheoLogLevel.valueOf(o.optString("level", "INFO")) }.getOrDefault(TheoLogLevel.INFO),
          tag = o.optString("tag", "APP"),
          message = o.optString("message", ""),
          details = o.optString("details", "")
        )
      }.takeLast(MAX_ENTRIES)
    } catch (e: Exception) {
      emptyList()
    }
  }
}
