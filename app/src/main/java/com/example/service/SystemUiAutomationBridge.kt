package com.example.service

import java.util.concurrent.atomic.AtomicBoolean

internal object SystemUiAutomationBridge {
  private val openNotificationShade = AtomicBoolean(false)
  fun requestNotificationShade() { openNotificationShade.set(true) }
  fun consumeNotificationShadeRequest(): Boolean = openNotificationShade.compareAndSet(true, false)
}
