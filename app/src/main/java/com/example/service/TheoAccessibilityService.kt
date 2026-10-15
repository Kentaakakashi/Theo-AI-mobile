package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
  }

  // Do not collect events: a task explicitly requests a fresh snapshot when needed.
  override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
  override fun onInterrupt() = Unit

  override fun onDestroy() {
    if (instance === this) instance = null
    super.onDestroy()
  }

  fun captureActiveScreen(): ScreenSnapshot? {
    val root = rootInActiveWindow ?: return null
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
  suspend fun captureActiveScreenJpeg(): ByteArray? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
      try {
        takeScreenshot(
          android.view.Display.DEFAULT_DISPLAY,
          mainExecutor,
          object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
              var bitmap: android.graphics.Bitmap? = null
              try {
                val hardwareBuffer = result.hardwareBuffer
                bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                hardwareBuffer.close()
                val source = bitmap ?: run {
                  if (continuation.isActive) continuation.resume(null) {}
                  return
                }
                // Downscale before encoding to reduce upload size and data usage.
                val maxWidth = 1280
                val scaled = if (source.width > maxWidth) {
                  val height = (source.height * (maxWidth.toFloat() / source.width)).toInt().coerceAtLeast(1)
                  android.graphics.Bitmap.createScaledBitmap(source, maxWidth, height, true)
                } else source
                val output = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, output)
                if (scaled !== source) scaled.recycle()
                if (continuation.isActive) continuation.resume(output.toByteArray()) {}
              } catch (error: Exception) {
                TheoDiagnosticLogger.warn("SCREEN_VISION", "Screenshot could not be encoded", error.javaClass.simpleName)
                if (continuation.isActive) continuation.resume(null) {}
              } finally {
                bitmap?.recycle()
              }
            }

            override fun onFailure(errorCode: Int) {
              TheoDiagnosticLogger.warn("SCREEN_VISION", "Android screenshot capture failed", "code=$errorCode")
              if (continuation.isActive) continuation.resume(null) {}
            }
          }
        )
      } catch (error: Exception) {
        TheoDiagnosticLogger.warn("SCREEN_VISION", "Android screenshot capture unavailable", error.javaClass.simpleName)
        if (continuation.isActive) continuation.resume(null) {}
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
    val activeRoot = rootInActiveWindow ?: return false
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
                button.isEnabled && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
    val root = rootInActiveWindow ?: return false
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
