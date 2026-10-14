package com.example

import com.example.service.ActionIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionIntentParserTest {
  @Test
  fun compoundAppMediaRequestUsesLiveScreenTask() {
    assertEquals(
      "screen:app:Bitchord|task:play the song \"My House Is Not a Home\"",
      ActionIntentParser.parse("open Bitchord and play the song \"My House Is Not a Home\"")
    )
  }

  @Test
  fun parsesStandaloneMusicQueryAndPreservesQuotedTitle() {
    assertEquals("music:My House Is Not a Home", ActionIntentParser.parse("play \"My House Is Not a Home\""))
  }

  @Test
  fun parsesAppLaunchWithoutAddingActionWordsToName() {
    assertEquals("app:Bitchord", ActionIntentParser.parse("open Bitchord"))
  }

  @Test
  fun recognizesNativeSettingsCommands() {
    assertEquals("wifi", ActionIntentParser.parse("open Wi-Fi settings"))
    assertEquals("settings", ActionIntentParser.parse("please open settings"))
  }

  @Test
  fun currentScreenRequestUsesRuntimePlanning() {
    assertEquals("screen:current|task:scroll down", ActionIntentParser.parse("scroll down"))
    assertEquals("screen:current|task:tap the next button", ActionIntentParser.parse("tap the next button"))
  }

  @Test
  fun compoundRequestSeparatesAppFromGoal() {
    assertEquals(
      "screen:app:Discord|task:search for Kenta",
      ActionIntentParser.parse("open Discord and search for Kenta")
    )
    assertEquals(
      "screen:app:Telegram|task:send a message to Kavi",
      ActionIntentParser.parse("launch Telegram and send a message to Kavi")
    )
  }

  @Test
  fun ordinaryQuestionsAreNotNativeActions() {
    assertNull(ActionIntentParser.parse("what is the best music app?"))
    assertNull(ActionIntentParser.parse("explain how Wi-Fi works"))
  }

  @Test
  fun shoppingCommandsRemainExplicitHandoffs() {
    assertEquals("order:one large pizza", ActionIntentParser.parse("order one large pizza"))
  }
}
