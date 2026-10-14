package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast

object DeviceActionHandler {

  data class TelemetryInfo(
    val batteryPct: Int,
    val isCharging: Boolean,
    val freeRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Long,
    val totalStorageGb: Long
  )

  fun getSystemTelemetry(context: Context): TelemetryInfo {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      bm?.isCharging == true
    } else false

    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager?.getMemoryInfo(memInfo)
    val freeRam = memInfo.availMem / (1024 * 1024)
    val totalRam = memInfo.totalMem / (1024 * 1024)

    val stat = StatFs(Environment.getDataDirectory().path)
    val freeStorage = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
    val totalStorage = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)

    return TelemetryInfo(
      batteryPct = batteryPct,
      isCharging = isCharging,
      freeRamMb = freeRam,
      totalRamMb = totalRam,
      freeStorageGb = freeStorage,
      totalStorageGb = totalStorage
    )
  }

  data class InstalledAppMatch(val label: String, val packageName: String)

  fun findInstalledApps(context: Context, requestedName: String): List<InstalledAppMatch> {
    val query = requestedName.trim()
    if (query.isBlank()) return emptyList()
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val candidates = pm.queryIntentActivities(launcherIntent, 0)
      .map { info ->
        InstalledAppMatch(info.loadLabel(pm).toString(), info.activityInfo.packageName)
      }
      .distinctBy { it.packageName }
    val exact = candidates.filter { it.label.equals(query, ignoreCase = true) }
    if (exact.isNotEmpty()) return exact
    val normalizedQuery = query.lowercase().filter(Char::isLetterOrDigit)
    val normalized = candidates.filter { it.label.lowercase().filter(Char::isLetterOrDigit) == normalizedQuery }
    if (normalized.isNotEmpty()) return normalized
    val partial = candidates.filter {
      it.label.contains(query, ignoreCase = true) || query.contains(it.label, ignoreCase = true)
    }
    return partial.take(6)
  }

  private fun launchInstalledApp(context: Context, requestedName: String): Boolean {
    val matches = findInstalledApps(context, requestedName)
    if (matches.size != 1) {
      val message = if (matches.isEmpty()) {
        "Couldn't find an installed app named \"$requestedName\"."
      } else {
        "Several apps match \"$requestedName\": ${matches.joinToString { it.label }}. Use a more exact app name."
      }
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      return false
    }
    val app = matches.single()
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: run {
      Toast.makeText(context, "${app.label} is installed but Android did not provide a launch intent.", Toast.LENGTH_LONG).show()
      return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return true
  }

  fun executeAction(context: Context, actionKey: String): Boolean {
    return try {
      val normalizedAction = actionKey.trim()
      if (normalizedAction.startsWith("music:", ignoreCase = true)) {
        val payload = normalizedAction.substringAfter(':').trim()
        val separator = payload.indexOf('|')
        val provider = if (separator >= 0) payload.substring(0, separator).lowercase() else "youtube"
        val query = if (separator >= 0) payload.substring(separator + 1).trim() else payload
        if (query.isBlank()) return false

        // App Links may route into the chosen music app when installed. This opens
        // a real search; playback can still require track selection or sign-in.
        val musicUrl = if (provider == "spotify") {
          "https://open.spotify.com/search/${Uri.encode(query)}"
        } else {
          "https://music.youtube.com/search?q=${Uri.encode(query)}"
        }
        val musicIntent = Intent(
          Intent.ACTION_VIEW,
          Uri.parse(musicUrl)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(musicIntent)
        return true
      }

      if (normalizedAction.startsWith("order:", ignoreCase = true)) {
        val query = normalizedAction.substringAfter(':').trim()
        if (query.isBlank()) return false

        // Never place an order or spend money automatically. Open a real search so
        // the user can choose a provider, review the basket/address/price, and confirm.
        val orderQuery = if (query.contains("order", ignoreCase = true)) query else "order $query online"
        val orderIntent = Intent(
          Intent.ACTION_VIEW,
          Uri.parse("https://www.google.com/search?q=${Uri.encode(orderQuery)}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(orderIntent)
        return true
      }

      if (normalizedAction.startsWith("app:", ignoreCase = true)) {
        val requestedName = normalizedAction.substringAfter(':').trim()
        if (requestedName.isBlank()) return false
        return launchInstalledApp(context, requestedName)
      }

      if (normalizedAction.startsWith("sequence:", ignoreCase = true)) {
        // Do not silently ignore the requested app or claim the song is playing.
        // Until the target app's supported playback API is known, open the requested
        // app when uniquely resolved; the result remains "app opened", not "playing".
        val payload = normalizedAction.substringAfter(':')
        val appPart = payload.substringBefore("|music:", missingDelimiterValue = "")
        val song = payload.substringAfter("|music:", missingDelimiterValue = "").trim()
        if (!appPart.startsWith("app:", ignoreCase = true) || song.isBlank()) return false
        val requestedName = appPart.substringAfter(':').trim()
        val matches = findInstalledApps(context, requestedName)
        if (matches.size == 1) {
          val launched = launchInstalledApp(context, matches.first().label)
          if (launched) Toast.makeText(context, "Opened ${matches.first().label}. This app's playback wasn't confirmed; use its search/play controls.", Toast.LENGTH_LONG).show()
          return launched
        }
        val message = if (matches.isEmpty()) {
          "Couldn't find an installed app named \"$requestedName\". Choose a music provider from the chat to search for \"$song\"."
        } else {
          "More than one app matches \"$requestedName\": ${matches.joinToString { it.label }}. Please choose the exact app."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return false
      }

      val intent = when (normalizedAction.lowercase()) {
        "settings" -> Intent(Settings.ACTION_SETTINGS)
        "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        "clock", "alarm" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        "wireless", "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        else -> null
      }

      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
      } else {
        false
      }
    } catch (e: Exception) {
      Toast.makeText(context, "Action execution error: ${e.message}", Toast.LENGTH_SHORT).show()
      false
    }
  }
}
