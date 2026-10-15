package com.example.service

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Android-native speech output tuned for a composed, British JARVIS-like delivery.
 *
 * Uses voices already installed in Android's TTS engine; no bundled voice model,
 * extra API key, or third-party voice-cloning service is required.
 */
class TheoSpeechManager(
  private val context: Context,
  private val onSpeakingStateChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  private var isInitialized = false
  var isSpeechEnabled: Boolean = true

  init {
    tts = TextToSpeech(context.applicationContext, this)
  }

  override fun onInit(status: Int) {
    if (status != TextToSpeech.SUCCESS) return

    val engine = tts ?: return
    val britishLocale = Locale.UK
    val languageResult = engine.setLanguage(britishLocale)
    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
      languageResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
      // Keep speech functional if the device has no British English voice data.
      val fallbackResult = engine.setLanguage(Locale.US)
      if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
        fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
      ) return
    }

    // Prefer an installed British English male voice when the engine exposes one.
    // Voice names vary by manufacturer, so use locale + quality rather than a
    // hard-coded vendor-specific voice identifier.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val britishMale = engine.voices
        ?.filter { voice ->
          voice.locale.language == "en" &&
            voice.locale.country == "GB" &&
            !voice.isNetworkConnectionRequired &&
            !voice.name.contains("female", ignoreCase = true)
        }
        ?.maxByOrNull { it.quality }

      val britishAny = engine.voices
        ?.filter { voice ->
          voice.locale.language == "en" && voice.locale.country == "GB"
        }
        ?.maxByOrNull { it.quality }

      (britishMale ?: britishAny)?.let { engine.voice = it }
    }

    isInitialized = true
    // Calm, slightly lower and measured; this is a voice style, not a cloned actor.
    engine.setPitch(0.86f)
    engine.setSpeechRate(0.94f)
    setupListener()
  }

  private fun setupListener() {
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        onSpeakingStateChanged(true)
      }

      override fun onDone(utteranceId: String?) {
        onSpeakingStateChanged(false)
      }

      override fun onError(utteranceId: String?) {
        onSpeakingStateChanged(false)
      }
    })
  }

  fun speak(text: String) {
    if (!isSpeechEnabled || !isInitialized) return

    // Clean markdown and avoid speaking raw link destinations.
    val cleanText = text
      .replace(Regex("\\[([^\\]]+)]\\((?:[^()]|\\([^()]*\\))*\\)"), "$1")
      .replace(Regex("https?://\\S+"), "")
      .replace(Regex("[*#`_~]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()
      .take(700)

    if (cleanText.isBlank()) return
    tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "THEO_UTTERANCE_${System.currentTimeMillis()}")
  }

  fun stop() {
    tts?.stop()
    onSpeakingStateChanged(false)
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
  }
}
