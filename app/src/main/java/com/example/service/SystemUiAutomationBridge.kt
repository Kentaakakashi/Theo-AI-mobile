package com.example.service

import java.util.concurrent.atomic.AtomicBoolean

internal object SystemUiAutomationBridge {
  private val openQuickSettings = AtomicBoolean(false)

  /**
   * Requests the accessibility service to open Android's Quick Settings surface.
   * The bridge deliberately does not perform the system action itself because only the
   * user-enabled AccessibilityService has the authority to invoke global UI actions.
   */
  fun requestNotificationShade() { openQuickSettings.set(true) }

  fun consumeNotificationShadeRequest(): Boolean = openQuickSettings.compareAndSet(true, false)
}
