package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity

/**
 * Keeps the process at foreground-service priority during user-started screen automation.
 * The ViewModel still owns the action loop; this service only provides a persistent,
 * user-visible notification and a Stop action. A process death never resumes old taps.
 */
class TheoAutomationForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      try {
        onStopRequested?.invoke()
      } catch (error: Exception) {
        Log.w(TAG, "Stop callback failed", error)
      }
      stopSelf()
      return START_NOT_STICKY
    }

    return try {
      val notification = buildNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      START_NOT_STICKY
    } catch (error: Exception) {
      Log.e(TAG, "Could not start foreground automation notification", error)
      stopSelf()
      START_NOT_STICKY
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Theo screen automation",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Shows when Theo is controlling an app for a user-requested task."
      setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification {
    val stopIntent = Intent(this, TheoAutomationForegroundService::class.java)
      .setAction(ACTION_STOP)
    val stopPendingIntent = PendingIntent.getService(
      this,
      STOP_REQUEST_CODE,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    return builder
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("Theo is working")
      .setContentText("Screen automation is active. Tap Stop to end the task.")
      .setCategory(Notification.CATEGORY_SERVICE)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .addAction(android.R.drawable.ic_media_pause, "Stop task", stopPendingIntent)
      .build()
  }

  companion object {
    private const val TAG = "TheoAutomationService"
    private const val CHANNEL_ID = "theo_screen_automation"
    private const val NOTIFICATION_ID = 7306
    private const val STOP_REQUEST_CODE = 7307
    private const val ACTION_STOP = "com.example.theo.action.STOP_SCREEN_AUTOMATION"

    @Volatile
    var onStopRequested: (() -> Unit)? = null

    fun start(context: Context) {
      val intent = Intent(context, TheoAutomationForegroundService::class.java)
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (error: Exception) {
        // Automation can still function when the OEM refuses an FGS start; diagnostics
        // reveal this without crashing or falsely claiming long-running protection.
        Log.w(TAG, "Foreground protection unavailable; continuing with ordinary task", error)
      }
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, TheoAutomationForegroundService::class.java))
      } catch (error: Exception) {
        Log.w(TAG, "Could not stop foreground automation service", error)
      }
    }
  }
}
