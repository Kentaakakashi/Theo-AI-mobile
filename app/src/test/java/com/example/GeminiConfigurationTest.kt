package com.example

import com.example.model.AiModelType
import com.example.service.geminiModelConfiguration
import com.example.service.parseGeminiApiKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiConfigurationTest {
  @Test
  fun parsesKeysDelimitedByRealNewlinesCommasAndSemicolons() {
    assertEquals(listOf("alpha", "beta", "gamma", "delta"), parseGeminiApiKeys(" alpha\n beta, gamma;delta "))
  }

  @Test
  fun repairsKeysSavedWithTheOldEscapedNewlineDelimiter() {
    assertEquals(listOf("first-key", "second-key"), parseGeminiApiKeys("first-key\\nsecond-key"))
  }

  @Test
  fun selectedGeminiModelMapsToItsOwnEndpointAndThinkingLevel() {
    assertEquals("gemini-3.5-flash-lite", geminiModelConfiguration(AiModelType.GEMINI_FLASH).modelId)
    assertEquals("MINIMAL", geminiModelConfiguration(AiModelType.GEMINI_FLASH).thinkingLevel)
    assertEquals("gemini-3.1-pro-preview", geminiModelConfiguration(AiModelType.GEMINI_PRO).modelId)
    assertEquals("LOW", geminiModelConfiguration(AiModelType.GEMINI_PRO).thinkingLevel)
  }
}
