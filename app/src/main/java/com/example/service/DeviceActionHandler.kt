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

  fun executeAction(context: Context, actionKey: String): Boolean {
    return try {
      val normalizedAction = actionKey.trim()
      if (normalizedAction.startsWith("music:", ignoreCase = true)) {
        val query = normalizedAction.substringAfter(':').trim()
        if (query.isBlank()) return false

        // Android App Links may route this to YouTube Music when installed; otherwise
        // the browser opens the real search page. This starts a music search, not
        // guaranteed playback, because services may require the user to choose a track.
        val musicIntent = Intent(
          Intent.ACTION_VIEW,
          Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
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

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = packageManager.queryIntentActivities(launcherIntent, 0)
        val matchingActivity = launchables.firstOrNull { info ->
          info.loadLabel(packageManager).toString().equals(requestedName, ignoreCase = true)
        } ?: launchables.firstOrNull { info ->
          info.loadLabel(packageManager).toString().contains(requestedName, ignoreCase = true)
        }

        val launchIntent = matchingActivity?.activityInfo?.let { activityInfo ->
          packageManager.getLaunchIntentForPackage(activityInfo.packageName)
        }
        if (launchIntent == null) {
          Toast.makeText(context, "Couldn't find an installed app named \"$requestedName\".", Toast.LENGTH_LONG).show()
          return false
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
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
