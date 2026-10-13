package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

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
    if (status == TextToSpeech.SUCCESS) {
      val result = tts?.setLanguage(Locale.US)
      if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
        isInitialized = true
        tts?.setPitch(0.92f) // Slightly deeper, futuristic timbre
        tts?.setSpeechRate(1.02f)
        setupListener()
      }
    }
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

    // Clean text of markdown asterisks or code fences for cleaner speech synthesis
    val cleanText = text
      .replace(Regex("[*#`_~]"), "")
      .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
      .take(400) // Keep spoken response concise and snappy

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
