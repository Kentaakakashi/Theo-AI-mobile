package com.example

import com.example.service.ActionSafetyPolicy
import com.example.service.ScreenActionParser
import com.example.service.ScreenAutomationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenAutomationTest {
  @Test
  fun parsesSupportedClickNode() {
    val action = ScreenActionParser.parse("""{"action":"click","node":"0.2.1","reason":"Open search"}""")
    assertEquals("click", action?.kind)
    assertEquals("0.2.1", action?.nodePath)
  }

  @Test
  fun rejectsUnknownActionsInventedPathsAndMalformedJson() {
    assertNull(ScreenActionParser.parse("""{"action":"execute_shell","node":"0.1"}"""))
    assertNull(ScreenActionParser.parse("""{"action":"click","node":"unknown"}"""))
    assertNull(ScreenActionParser.parse("This is not structured output"))
  }

  @Test
  fun typeRequiresNodeAndNonBlankText() {
    assertNull(ScreenActionParser.parse("""{"action":"type","node":"0.1"}"""))
    assertNull(ScreenActionParser.parse("""{"action":"type","node":"0.1","text":""}"""))
    assertEquals(
      "hello",
      ScreenActionParser.parse("""{"action":"type","node":"0.1","text":"hello"}""")?.text
    )
  }

  @Test
  fun acceptsOnlySupportedScrollDirections() {
    assertEquals("forward", ScreenActionParser.parse("""{"action":"scroll","node":"0.0","direction":"forward"}""")?.direction)
    assertNull(ScreenActionParser.parse("""{"action":"scroll","node":"0.0","direction":"up"}"""))
  }

  @Test
  fun parsesNamedAppAndTaskWithoutAProviderTable() {
    val request = ScreenAutomationRequest.parse("screen:app:Bitchord|task:play My House Is Not a Home")
    assertEquals("Bitchord", request?.appName)
    assertEquals("play My House Is Not a Home", request?.task)
  }

  @Test
  fun currentScreenTasksDoNotRequireAnAppName() {
    val request = ScreenAutomationRequest.parse("screen:current|task:scroll down")
    assertEquals(null, request?.appName)
    assertEquals("scroll down", request?.task)
  }

  @Test
  fun sensitiveRequestsNeedConfirmationAndSafeNavigationDoesNot() {
    assertTrue(ActionSafetyPolicy.requiresConfirmation("screen:app:Mail|task:send a message"))
    assertTrue(ActionSafetyPolicy.requiresConfirmation("order:pizza"))
    assertFalse(ActionSafetyPolicy.requiresConfirmation("screen:current|task:scroll down"))
    assertFalse(ActionSafetyPolicy.requiresConfirmation("app:Calculator"))
  }

  @Test
  fun potentiallyDangerousControlsPauseBeforeClicking() {
    val click = ScreenActionParser.parse("""{"action":"click","node":"0.1"}""")!!
    assertTrue(ActionSafetyPolicy.targetRequiresConfirmation(click, "Send message"))
    assertTrue(ActionSafetyPolicy.targetRequiresConfirmation(click, "Delete"))
    assertFalse(ActionSafetyPolicy.targetRequiresConfirmation(click, "Search"))
    val scroll = ScreenActionParser.parse("""{"action":"scroll","node":"0.1","direction":"forward"}""")!!
    assertFalse(ActionSafetyPolicy.targetRequiresConfirmation(scroll, "Delete"))
  }
}
