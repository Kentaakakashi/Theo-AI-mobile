package com.example

import com.example.service.ScreenActionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenActionBatchParserTest {
  @Test
  fun parsesShortSemanticActionBatchWithTargetLabels() {
    val plan = ScreenActionParser.parsePlan(
      """{"actions":[{"action":"click","node":"0.2","target":"Search","reason":"Open search"},{"action":"type","node":"0.3","target":"Search field","text":"Creep","reason":"Enter requested song"}]}"""
    )
    assertEquals(2, plan?.size)
    assertEquals("click", plan?.get(0)?.kind)
    assertEquals("Search", plan?.get(0)?.targetLabel)
    assertEquals("0.3", plan?.get(1)?.nodePath)
    assertEquals("Search field", plan?.get(1)?.targetLabel)
    assertEquals("Creep", plan?.get(1)?.text)
  }

  @Test
  fun acceptsSingleActionAsAPlanOfOne() {
    val plan = ScreenActionParser.parsePlan(
      """{"action":"click","node":"0.2","target":"Search","reason":"Open search"}"""
    )
    assertEquals(1, plan?.size)
    assertEquals("click", plan?.singleOrNull()?.kind)
  }

  @Test
  fun rejectsTooManyTooFewAndMalformedBatchActions() {
    assertNull(ScreenActionParser.parsePlan("""{"actions":[{"action":"click","node":"0.1","target":"A"}]}"""))
    assertNull(ScreenActionParser.parsePlan("""{"actions":[{"action":"click","node":"0.1","target":"A"},{"action":"click","node":"0.2","target":"B"},{"action":"click","node":"0.3","target":"C"},{"action":"click","node":"0.4","target":"D"}]}"""))
    assertNull(ScreenActionParser.parsePlan("""{"actions":[{"action":"click","node":"0.1","target":"A"},{"action":"execute_shell","node":"0.2","target":"B"}]}"""))
  }

  @Test
  fun candidateBatchParserDoesNotPretendItWasValidatedForExecution() {
    val plan = ScreenActionParser.parsePlan(
      """{"actions":[{"action":"click","node":"0.1","target":"Delete account"},{"action":"click","node":"0.2","target":"Confirm"}]}"""
    )
    assertEquals(2, plan?.size)
    assertTrue(plan!!.all { it.nodePath != null && it.targetLabel.isNotBlank() })
  }
}
