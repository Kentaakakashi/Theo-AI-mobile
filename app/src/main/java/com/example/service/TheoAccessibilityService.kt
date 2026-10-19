package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * User-enabled, on-demand bridge to Android's accessibility tree.
 *
 * The service does not retain event text or persist screen contents. It builds a tree only for
 * an explicitly requested task. Editable field values and password contents are excluded.
 * This service uses Android-native APIs and the accessibility-tree/action-loop pattern
 * referenced from MIT-licensed MobileAgent-Android; see THIRD_PARTY_NOTICES.md.
 */
class TheoAccessibilityService : AccessibilityService() {
  companion object {
    @Volatile var instance: TheoAccessibilityService? = null
      private set
    private const val MAX_NODES = 120
    private const val MAX_DEPTH = 12
    @Volatile var overlayStopHandler: (() -> Unit)? = null
    @Volatile var overlayPauseToggleHandler: (() -> Boolean)? = null

    fun isEnabled(context: Context): Boolean {
      val expected = ComponentName(context, TheoAccessibilityService::class.java).flattenToString()
      val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      ).orEmpty()
      return enabled.split(':').any { entry ->
        entry.equals(expected, ignoreCase = true) ||
          ComponentName.unflattenFromString(entry)?.flattenToString()?.equals(expected, ignoreCase = true) == true
      }
    }
  }

  // A counter only: accessibility event text is never retained. It lets the action loop wait
  // for UI changes instead of sleeping for a fixed delay after every tap.
  private val screenRevision = AtomicLong(0L)

  fun currentScreenRevision(): Long = screenRevision.get()

  suspend fun awaitScreenChange(afterRevision: Long, timeoutMs: Long = 400L): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
    while (screenRevision.get() == afterRevision && SystemClock.elapsedRealtime() < deadline) {
      delay(25L)
    }
    return screenRevision.get() != afterRevision
  }

  private val overlayHandler = Handler(Looper.getMainLooper())
  private val screenSenseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  @Volatile var isLiveScreenSenseRunning: Boolean = false
    private set
  @Volatile private var screenSensePaused: Boolean = false
  private var screenSenseJob: Job? = null
  private var automationOverlay: View? = null
  private var overlayStatusView: android.widget.TextView? = null
  private var overlayDetailView: android.widget.TextView? = null
  private var overlayPauseButton: android.widget.Button? = null
  private var overlayHideRunnable: Runnable? = null

  /** Small floating status card shown only during explicitly requested automation. */
  fun showAutomationOverlay(title: String, status: String, detail: String, autoHideMs: Long = 0L) {
    overlayHandler.post {
      val manager = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@post
      overlayHideRunnable?.let(overlayHandler::removeCallbacks)
      overlayHideRunnable = null
      var card = automationOverlay
      if (card == null) {
        val density = resources.displayMetrics.density
        val root = android.widget.LinearLayout(this).apply {
          orientation = android.widget.LinearLayout.VERTICAL
          setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
          background = GradientDrawable().apply {
            setColor(Color.rgb(18, 23, 38))
            setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.rgb(0, 229, 255))
            cornerRadius = 18 * density
          }
          elevation = 20 * density
        }
        val heading = android.widget.TextView(this).apply {
          text = "◈ THEO · LIVE TASK"
          setTextColor(Color.rgb(0, 229, 255))
          textSize = 12f
          typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val taskView = android.widget.TextView(this).apply {
          setTextColor(Color.WHITE)
          textSize = 14f
          typeface = android.graphics.Typeface.DEFAULT_BOLD
          maxLines = 2
        }
        val statusText = android.widget.TextView(this).apply {
          setTextColor(Color.rgb(190, 202, 225))
          textSize = 12f
          setPadding(0, (6 * density).toInt(), 0, 0)
          maxLines = 3
        }
        val detailText = android.widget.TextView(this).apply {
          setTextColor(Color.rgb(135, 151, 180))
          textSize = 11f
          setPadding(0, (5 * density).toInt(), 0, (8 * density).toInt())
          maxLines = 4
        }
        val buttons = android.widget.LinearLayout(this).apply {
          orientation = android.widget.LinearLayout.HORIZONTAL
          gravity = Gravity.END
        }
        val close = android.widget.Button(this).apply {
          text = "Hide"
          isAllCaps = false
          setOnClickListener { hideAutomationOverlay() }
        }
        val pause = android.widget.Button(this).apply {
          text = "Pause"
          isAllCaps = false
          setOnClickListener {
            val nowPaused = overlayPauseToggleHandler?.invoke() ?: false
            setAutomationPaused(nowPaused)
          }
        }
        val stop = android.widget.Button(this).apply {
          text = "Stop"
          isAllCaps = false
          setTextColor(Color.WHITE)
          setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(165, 48, 70)))
          setOnClickListener {
            hideAutomationOverlay()
            overlayStopHandler?.invoke()
          }
        }
        buttons.addView(close, android.widget.LinearLayout.LayoutParams(-2, (42 * density).toInt()))
        buttons.addView(pause, android.widget.LinearLayout.LayoutParams(-2, (42 * density).toInt()))
        buttons.addView(stop, android.widget.LinearLayout.LayoutParams(-2, (42 * density).toInt()))
        root.addView(heading)
        root.addView(taskView)
        root.addView(statusText)
        root.addView(detailText)
        root.addView(buttons)
        val params = WindowManager.LayoutParams(
          (300 * density).toInt(),
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT
        ).apply {
          gravity = Gravity.TOP or Gravity.END
          x = (10 * density).toInt()
          y = (92 * density).toInt()
        }
        try {
          manager.addView(root, params)
          automationOverlay = root
          overlayStatusView = statusText
          overlayDetailView = detailText
          overlayPauseButton = pause
          root.tag = taskView
          card = root
        } catch (error: Exception) {
          TheoDiagnosticLogger.warn("SCREEN_AUTOMATION", "Live overlay could not be displayed", error.javaClass.simpleName)
          return@post
        }
      }
      val taskText = card?.tag as? android.widget.TextView
      taskText?.text = title
      overlayStatusView?.text = status
      overlayDetailView?.text = detail
      if (autoHideMs > 0L) {
        val target = automationOverlay
        overlayHideRunnable = Runnable {
          if (automationOverlay === target) hideAutomationOverlay()
        }
        overlayHandler.postDelayed(overlayHideRunnable!!, autoHideMs)
      }
    }
  }

  fun setAutomationPaused(paused: Boolean) {
    overlayHandler.post {
      overlayPauseButton?.text = if (paused) "Resume" else "Pause"
    }
  }

  fun hideAutomationOverlay() {
    overlayHandler.post {
      overlayHideRunnable?.let(overlayHandler::removeCallbacks)
      overlayHideRunnable = null
      val card = automationOverlay ?: return@post
      runCatching { (getSystemService(WINDOW_SERVICE) as? WindowManager)?.removeView(card) }
      automationOverlay = null
      overlayStatusView = null
      overlayDetailView = null
      overlayPauseButton = null
    }
  }

  /**
   * Opt-in periodic screen understanding. Android's accessibility screenshot API is not
   * a continuous video stream, so frames are sampled about every five seconds.
   */
  fun startLiveScreenSense(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      showAutomationOverlay("Screen Sense unavailable", "Android 11+ required", "Native accessibility screenshots require Android 11 or newer.", 5000L)
      return false
    }
    if (isLiveScreenSenseRunning) return true
    val prefs = getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)
    val geminiKeys = parseGeminiApiKeys(prefs.getString("gemini_api_key", "").orEmpty())
    val groqKey = prefs.getString("groq_api_key", "").orEmpty().trim()
    if (geminiKeys.isEmpty() && groqKey.isBlank()) {
      showAutomationOverlay("Screen Sense unavailable", "No vision API configured", "Add a Gemini API key in Theo Settings first.", 6000L)
      return false
    }
    isLiveScreenSenseRunning = true
    screenSensePaused = false
    screenSenseJob?.cancel()
    screenSenseJob = screenSenseScope.launch {
      TheoDiagnosticLogger.info("SCREEN_SENSE", "Live screen sense started", "intervalMs=5000")
      val memoryEnabled = PersistentScreenMemoryStore.hasFolder(this@TheoAccessibilityService)\n      showAutomationOverlay("Live Screen Sense", "Watching is active", if (memoryEnabled) "Screen descriptions are remembered in your selected folder; raw screenshots are not saved." else "No memory folder selected: screen sense works, but long-term learning is off.", 0L)
      while (isActive && isLiveScreenSenseRunning) {
        if (screenSensePaused) {
          delay(500L)
          continue
        }
        val activePackage = getActiveWindowPackageName()
        if (activePackage.isNullOrBlank() || activePackage == packageName) {
          showAutomationOverlay("Live Screen Sense", "Paused while Theo is open", "Switch to another app to let Theo inspect its visible screen.", 0L)
          delay(1500L)
          continue
        }
        val capture = captureActiveScreenJpeg()
        val jpeg = capture.jpegBytes
        if (jpeg == null) {
          showAutomationOverlay("Live Screen Sense", "Capture unavailable", capture.error ?: "Android did not provide a screenshot.", 0L)
          delay(5000L)
          continue
        }
        val latestPrefs = getSharedPreferences("theo_prefs", Context.MODE_PRIVATE)
        val keys = parseGeminiApiKeys(latestPrefs.getString("gemini_api_key", "").orEmpty())
        val groq = latestPrefs.getString("groq_api_key", "").orEmpty()
        if (keys.isEmpty() && groq.isBlank()) {
          showAutomationOverlay("Live Screen Sense", "Paused · no vision key", "Add a Gemini key in Settings, then restart Screen Sense.", 0L)
          isLiveScreenSenseRunning = false
          break
        }
        val memoryContext = PersistentScreenMemoryStore.loadRecentContext(this@TheoAccessibilityService, activePackage)\n        val result = runCatching { ScreenVisionAnalyzer().describe(jpeg, groq, keys, memoryContext) }
          .getOrElse { VisionDescription(null, "none", it.localizedMessage ?: "Vision analysis failed.") }
        if (!isLiveScreenSenseRunning || !isActive) break
        if (result.text != null) {
          showAutomationOverlay("Live Screen Sense", "Updated · ${result.provider}", result.text.take(700), 0L)
          TheoDiagnosticLogger.info("SCREEN_SENSE", "Screen analysis updated", "provider=${result.provider}; chars=${result.text.length}")
        } else {
          showAutomationOverlay("Live Screen Sense", "Vision unavailable", result.error ?: "No description returned.", 0L)
          TheoDiagnosticLogger.warn("SCREEN_SENSE", "Screen analysis failed", result.error ?: "unknown")
        }
        delay(5000L)
      }
      TheoDiagnosticLogger.info("SCREEN_SENSE", "Live screen sense loop ended")
    }
    return true
  }

  fun toggleLiveScreenSensePause(): Boolean {
    if (!isLiveScreenSenseRunning) return false
    screenSensePaused = !screenSensePaused
    setAutomationPaused(screenSensePaused)
    return screenSensePaused
  }

  fun stopLiveScreenSense() {
    isLiveScreenSenseRunning = false
    screenSensePaused = false
    screenSenseJob?.cancel()
    screenSenseJob = null
    TheoDiagnosticLogger.info("SCREEN_SENSE", "Live screen sense stopped")
    hideAutomationOverlay()
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
  }

  /**
   * Briefly outlines the exact control Theo is about to activate. Accessibility overlays
   * don't require the general draw-over-other-apps permission and don't intercept touches.
   */
  private fun showTapIndicator(node: AccessibilityNodeInfo) {
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    if (bounds.isEmpty || !node.isVisibleToUser) return
    showTapIndicator(bounds)
  }

  private fun showTapIndicatorAt(centerX: Int, centerY: Int) {
    val size = (48 * resources.displayMetrics.density).toInt().coerceAtLeast(48)
    showTapIndicator(Rect(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2))
  }

  private fun showTapIndicator(bounds: Rect) {
    val windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
    val indicator = View(this).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke((2 * resources.displayMetrics.density).toInt().coerceAtLeast(2), Color.rgb(0, 229, 255))
        cornerRadius = 10 * resources.displayMetrics.density
      }
      elevation = 24 * resources.displayMetrics.density
      contentDescription = "Theo tap indicator"
    }
    val params = WindowManager.LayoutParams(
      bounds.width().coerceAtLeast(12),
      bounds.height().coerceAtLeast(12),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = bounds.left.coerceAtLeast(0)
      y = bounds.top.coerceAtLeast(0)
    }
    try {
      windowManager.addView(indicator, params)
      overlayHandler.postDelayed({
        runCatching { windowManager.removeView(indicator) }
      }, 360L)
    } catch (error: Exception) {
      TheoDiagnosticLogger.warn("SCREEN_AUTOMATION", "Tap indicator could not be displayed", error.javaClass.simpleName)
    }
  }

  private fun dispatchCoordinateTap(normalizedX: Int, normalizedY: Int): Boolean {
    if (normalizedX !in 0..1000 || normalizedY !in 0..1000) return false
    val metrics = resources.displayMetrics
    val x = (normalizedX / 1000f * metrics.widthPixels).toInt().coerceIn(1, metrics.widthPixels - 1)
    val y = (normalizedY / 1000f * metrics.heightPixels).toInt().coerceIn(1, metrics.heightPixels - 1)
    showTapIndicatorAt(x, y)
    val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0L, 70L))
      .build()
    return dispatchGesture(gesture, null, null)
  }

  // Count meaningful target-app changes only. Ignore Theo's own overlay events and
  // the immediate click event, which can fire before the app has actually transitioned.
  // Event text/content is never retained.
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val eventPackage = event.packageName?.toString().orEmpty()
    if (eventPackage == packageName) return
    when (event.eventType) {
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
      AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
      AccessibilityEvent.TYPE_VIEW_SCROLLED -> screenRevision.incrementAndGet()
    }
  }
  override fun onInterrupt() = Unit

  override fun onDestroy() {
    stopLiveScreenSense()
    screenSenseScope.coroutineContext[Job]?.cancel()
    hideAutomationOverlay()
    if (instance === this) instance = null
    super.onDestroy()
  }

  private fun findExternalWindowRoot(): AccessibilityNodeInfo? {
    val direct = runCatching { rootInActiveWindow }.getOrNull()
    if (direct != null) {
      val pkg = runCatching { direct.packageName?.toString().orEmpty() }.getOrDefault("")
      if (pkg.isNotBlank() && pkg != packageName) return direct
      runCatching { direct.recycle() }
    }
    val currentWindows = runCatching { windows }.getOrNull().orEmpty()
    var chosen: AccessibilityNodeInfo? = null
    var chosenScore = Int.MIN_VALUE
    currentWindows.forEach { window ->
      val root = runCatching { window.root }.getOrNull()
      if (root != null) {
        val pkg = runCatching { root.packageName?.toString().orEmpty() }.getOrDefault("")
        val score = (if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) 100 else 0) + (if (window.isActive) 20 else 0)
        if (pkg.isBlank() || pkg == packageName || score < chosenScore) runCatching { root.recycle() }
        else {
          runCatching { chosen?.recycle() }
          chosen = root
          chosenScore = score
        }
      }
      runCatching { window.recycle() }
    }
    return chosen
  }

  fun getActiveWindowPackageName(): String? {
    val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
    return try {
      root.packageName?.toString()?.takeIf { it.isNotBlank() }
    } finally {
      runCatching { root.recycle() }
    }
  }

  fun captureActiveScreen(): ScreenSnapshot? {
    if (SystemUiAutomationBridge.consumeNotificationShadeRequest()) {
      runCatching { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
      SystemClock.sleep(300L)
    }
    val root = findExternalWindowRoot() ?: return null
    val activePackage = root.packageName?.toString().orEmpty()
    if (activePackage.isBlank() || activePackage == packageName) {
      root.recycle()
      return null
    }
    val elements = mutableListOf<ScreenElement>()
    try {
      collectElements(root, "0", 0, elements)
    } finally {
      root.recycle()
    }
    return ScreenSnapshot(activePackage, elements)
  }

  /**
   * Captures a one-off screenshot through Android Accessibility. The bitmap exists only in
   * memory and is returned to the caller for immediate compression; callers must not persist it.
   * Android only exposes this API on API 30+.
   */
  suspend fun captureActiveScreenJpeg(): ScreenCaptureResult {
    val first = captureActiveScreenJpegOnce()
    if (first.succeeded || first.error?.contains("internal screenshot error", ignoreCase = true) != true) return first
    // A single bounded retry can recover transient OEM screenshot failures.
    kotlinx.coroutines.delay(350L)
    val retry = captureActiveScreenJpegOnce()
    if (retry.succeeded) {
      TheoDiagnosticLogger.info("SCREEN_VISION", "Screenshot retry recovered")
      return retry
    }
    TheoDiagnosticLogger.warn("SCREEN_VISION", "Screenshot retry also failed", retry.error ?: "No reason returned")
    return ScreenCaptureResult(null, (first.error ?: "Screenshot capture failed") + " A single retry also failed: " + (retry.error ?: "unknown screenshot error"))
  }

  private suspend fun captureActiveScreenJpegOnce(): ScreenCaptureResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return ScreenCaptureResult(null, "Native accessibility screenshots require Android 11 or newer.")
    }
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
      try {
        takeScreenshot(
          android.view.Display.DEFAULT_DISPLAY,
          mainExecutor,
          object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
              var bitmap: android.graphics.Bitmap? = null
              var scaled: android.graphics.Bitmap? = null
              try {
                val hardwareBuffer = result.hardwareBuffer
                try {
                  bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                } finally {
                  hardwareBuffer.close()
                }
                val source = bitmap
                if (source == null) {
                  if (continuation.isActive) continuation.resume(
                    ScreenCaptureResult(null, "Android returned a screenshot buffer that could not be converted to a bitmap.")
                  ) {}
                  return
                }
                // Downscale before encoding to reduce upload size and data usage.
                val maxWidth = 1280
                scaled = if (source.width > maxWidth) {
                  val height = (source.height * (maxWidth.toFloat() / source.width)).toInt().coerceAtLeast(1)
                  android.graphics.Bitmap.createScaledBitmap(source, maxWidth, height, true)
                } else source
                val output = java.io.ByteArrayOutputStream()
                val encoded = scaled!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, output)
                if (!encoded || output.size() == 0) {
                  if (continuation.isActive) continuation.resume(
                    ScreenCaptureResult(null, "Android captured a frame but JPEG encoding returned no image data.")
                  ) {}
                  return
                }
                if (continuation.isActive) continuation.resume(ScreenCaptureResult(output.toByteArray())) {}
              } catch (error: Exception) {
                val reason = "Screenshot conversion failed: ${error.localizedMessage ?: error.javaClass.simpleName}"
                TheoDiagnosticLogger.warn("SCREEN_VISION", reason)
                if (continuation.isActive) continuation.resume(ScreenCaptureResult(null, reason)) {}
              } finally {
                if (scaled != null && scaled !== bitmap) runCatching { scaled?.recycle() }
                runCatching { bitmap?.recycle() }
              }
            }

            override fun onFailure(errorCode: Int) {
              val reason = when (errorCode) {
                ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Android reported an internal screenshot error."
                ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Android denied screenshot access to the accessibility service. Check that Theo Screen Automation remains enabled."
                ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Android rate-limited screenshot capture because the previous capture was too recent."
                ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Android rejected the requested display for screenshot capture."
                ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> "Android rejected the accessibility window selected for screenshot capture."
                ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "Android blocked screenshot capture because this window contains secure content."
                else -> "Android screenshot capture failed with system error code $errorCode."
              }
              TheoDiagnosticLogger.warn("SCREEN_VISION", reason, "code=$errorCode")
              if (continuation.isActive) continuation.resume(ScreenCaptureResult(null, reason)) {}
            }
          }
        )
      } catch (error: Exception) {
        val reason = "Android screenshot capture could not start: ${error.localizedMessage ?: error.javaClass.simpleName}"
        TheoDiagnosticLogger.warn("SCREEN_VISION", reason)
        if (continuation.isActive) continuation.resume(ScreenCaptureResult(null, reason)) {}
      }
    }
  }

  private fun collectElements(
    node: AccessibilityNodeInfo,
    path: String,
    depth: Int,
    destination: MutableList<ScreenElement>
  ) {
    if (destination.size >= MAX_NODES) return
    if (node.isVisibleToUser) {
      val label = safeLabel(node).take(140)
      val resourceId = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("").take(100)
      if (label.isNotBlank() || node.isClickable || node.isEditable || node.isScrollable) {
        destination.add(ScreenElement(
          id = path,
          label = label,
          role = node.className?.toString()?.substringAfterLast('.')?.take(60).orEmpty(),
          resourceId = resourceId,
          clickable = node.isClickable,
          editable = node.isEditable,
          scrollable = node.isScrollable,
          enabled = node.isEnabled
        ))
      }
    }
    if (depth >= MAX_DEPTH || destination.size >= MAX_NODES) return
    for (index in 0 until node.childCount) {
      if (destination.size >= MAX_NODES) break
      val child = node.getChild(index) ?: continue
      try {
        collectElements(child, "$path.$index", depth + 1, destination)
      } finally {
        child.recycle()
      }
    }
  }

  private fun safeLabel(node: AccessibilityNodeInfo): String {
    if (node.isPassword) return "Password field"
    // Editable widget values and descriptions can contain the user's current input; only use its hint.
    if (node.isEditable) {
      val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString().orEmpty() else ""
      return ScreenTextSanitizer.sanitize(hint)
    }
    val description = node.contentDescription?.toString()?.trim().orEmpty()
    if (description.isNotBlank()) return ScreenTextSanitizer.sanitize(description)
    return ScreenTextSanitizer.sanitize(node.text?.toString().orEmpty())
  }

  internal fun performScreenAction(action: ScreenAction, expectedPackageName: String): Boolean {
    val activeRoot = findExternalWindowRoot() ?: return false
    val activePackage = try {
      activeRoot.packageName?.toString().orEmpty()
    } finally {
      activeRoot.recycle()
    }
    // The UI may have changed while the selected AI provider planned its next step.
    // Never dispatch an action into a different app than the one shown to the planner.
    if (activePackage.isBlank() || activePackage == packageName || activePackage != expectedPackageName) return false

    return when (action.kind) {
      "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
      "wait" -> true
      "tap" -> {
        val x = action.x ?: return false
        val y = action.y ?: return false
        dispatchCoordinateTap(x, y)
      }
      "click", "type", "scroll" -> {
        val path = action.nodePath
        if (path == null) {
          false
        } else {
          performOnPath(path, expectedPackageName) { chain ->
            val target = chain.lastOrNull() ?: return@performOnPath false
            when (action.kind) {
              "click" -> {
                val button = chain.asReversed().firstOrNull { it.isEnabled && it.isClickable } ?: target
                if (!button.isEnabled) {
                  false
                } else {
                  // Make the target visible to the user before Android receives the click.
                  showTapIndicator(button)
                  button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
              }
              "type" -> {
                val field = chain.asReversed().firstOrNull { it.isEnabled && it.isEditable } ?: return@performOnPath false
                val value = action.text ?: return@performOnPath false
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                  putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
              }
              "scroll" -> {
                val scrollable = chain.asReversed().firstOrNull { it.isEnabled && it.isScrollable } ?: return@performOnPath false
                val operation = if (action.direction == "backward") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                scrollable.performAction(operation)
              }
              else -> false
            }
          }
        }
      }
      else -> false
    }
  }

  private fun performOnPath(
    path: String,
    expectedPackageName: String,
    action: (List<AccessibilityNodeInfo>) -> Boolean
  ): Boolean {
    if (!Regex("^\\d+(?:\\.\\d+){0,12}$").matches(path)) return false
    val root = findExternalWindowRoot() ?: return false
    val activePackage = root.packageName?.toString().orEmpty()
    if (activePackage == packageName || activePackage != expectedPackageName) {
      root.recycle()
      return false
    }
    val chain = mutableListOf(root)
    try {
      var current = root
      for (part in path.split('.').drop(1)) {
        val index = part.toIntOrNull() ?: return false
        val child = current.getChild(index) ?: return false
        chain.add(child)
        current = child
      }
      return action(chain)
    } catch (_: Exception) {
      return false
    } finally {
      chain.asReversed().forEach { runCatching { it.recycle() } }
    }
  }
}
