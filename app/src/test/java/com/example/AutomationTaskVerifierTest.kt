package com.example

import com.example.service.AutomationTaskVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTaskVerifierTest {
  @Test
  fun extractsQuotedTrackTitleFromAutomationTask() {
    assertEquals(
      "Creep",
      AutomationTaskVerifier.requestedTrack(
        """Find the song "Creep" in the current music app and start playback."""
      )
    )
  }

  @Test
  fun extractsUnquotedTrackTitleWithoutGuessingOtherWords() {
    assertEquals("My House Is Not a Home", AutomationTaskVerifier.requestedTrack("play My House Is Not a Home"))
    assertEquals("Creep", AutomationTaskVerifier.requestedTrack("play the song Creep on BitChord"))
    assertEquals("Creep", AutomationTaskVerifier.requestedTrack("play Creep now"))
    assertNull(AutomationTaskVerifier.requestedTrack("open BitChord"))
  }

  @Test
  fun extractsRequestedMessageWithCommonPhrasings() {
    val direct = AutomationTaskVerifier.requestedMessage("Open Discord and message Kenta saying Hi")
    assertEquals("Kenta", direct?.recipient)
    assertEquals("Hi", direct?.body)

    val formal = AutomationTaskVerifier.requestedMessage("""send a message to "Kenta" saying "Hi there"""")
    assertEquals("Kenta", formal?.recipient)
    assertEquals("Hi there", formal?.body)

    val alternate = AutomationTaskVerifier.requestedMessage("send Kenta a message saying hello")
    assertEquals("Kenta", alternate?.recipient)
    assertEquals("hello", alternate?.body)
  }

  @Test
  fun declinesToInventMissingRecipientOrMessageBody() {
    assertNull(AutomationTaskVerifier.requestedMessage("send a message"))
    assertNull(AutomationTaskVerifier.requestedMessage("message Kenta"))
    assertNull(AutomationTaskVerifier.requestedMessage("play Creep"))
  }

  @Test
  fun visibleTextNormalizationIgnoresCaseAndExtraWhitespace() {
    assertEquals(
      "hi kenta",
      AutomationTaskVerifier.normalizeVisibleText("  HI   Kenta \n")
    )
    assertTrue(
      AutomationTaskVerifier.normalizeVisibleText("Hello\n THERE")
        .contains(AutomationTaskVerifier.normalizeVisibleText("hello there"))
    )
  }
}
