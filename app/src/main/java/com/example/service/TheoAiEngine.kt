package com.example.service

import com.example.model.AiModelType
import com.example.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TheoAiEngine {

  private val client = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .build()

  // Cloud generation can take longer than local requests, especially during
  // Gemini service load. Keep its timeout separate from the local bridge.
  private val geminiClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .build()

  // On-device Qwen inference can be slow on phones; don't treat a slow generation as offline.
  private val localQwenClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(210, TimeUnit.SECONDS)
    .build()

  data class EngineResult(
    val responseText: String,
    val modelBadge: String,
    val latencyMs: Long,
    val memoryExtracted: Pair<String, String>? = null, // key to value
    val actionSuggested: String? = null
  )

  suspend fun processQuery(
    prompt: String,
    activeModel: AiModelType,
    customApiKey: String,
    termuxHost: String,
    memories: List<MemoryItem>,
    onStreamChunk: (String) -> Unit = {}
  ): EngineResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    // 1. Detect if the user is asking Theo to remember something
    TheoDiagnosticLogger.info("AI_ENGINE", "Query started", "model=$activeModel, promptChars=${prompt.length}, memories=${memories.size}")
    val memoryExtraction = detectMemoryRequest(prompt)

    // 2. Detect device intent actions. Native actions are handled by Android,
    // never by model-generated terminal text or a simulated execution report.
    val actionIntent = detectActionIntent(prompt)
    if (actionIntent != null) {
      val actionDescription = when {
        actionIntent.startsWith("app:", ignoreCase = true) ->
          "open the app \"${actionIntent.substringAfter(':').trim()}\""
        actionIntent.startsWith("music:", ignoreCase = true) -> {
          val payload = actionIntent.substringAfter(':').trim()
          val provider = payload.substringBefore('|')
          val query = payload.substringAfter('|', payload).trim()
          val service = if (provider.equals("spotify", ignoreCase = true)) "Spotify" else "YouTube Music"
          "find \"$query\" in $service"
        }
        actionIntent.startsWith("order:", ignoreCase = true) ->
          "find ordering options for \"${actionIntent.substringAfter(':').trim()}\""
        else -> "open ${actionIntent.replaceFirstChar { it.uppercase() }}"
      }
      TheoDiagnosticLogger.info("DEVICE_ACTION", "Action request recognized", "action=$actionIntent")
      return@withContext EngineResult(
        responseText = "I can ${actionDescription}. Tap the action below to continue. For music, the service may ask you to choose a track before playback. For orders, I'll open real ordering/search options, but you must review the item, address, price, and confirm payment yourself.",
        modelBadge = "ANDROID ACTION",
        latencyMs = System.currentTimeMillis() - startTime,
        memoryExtracted = memoryExtraction,
        actionSuggested = actionIntent
      )
    }

    // Build memory context string
    val memoryContext = if (memories.isNotEmpty()) {
      "Known Operator Memory Context:\n" + memories.take(8).joinToString("\n") { "- ${it.key}: ${it.value} [${it.category}]" }
    } else {
      "No operator memory stored yet."
    }

    val result = when (activeModel) {
      AiModelType.LOCAL_QWEN -> {
        tryCallLocalQwen(prompt, memoryContext, termuxHost, startTime)
      }
      AiModelType.GEMINI_FLASH, AiModelType.GEMINI_PRO -> {
        val apiKeys = customApiKey.split(Regex("[\\r\\n,;]+")).map { it.trim() }.filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }.distinct()
        if (apiKeys.isEmpty()) {
          EngineResult("No Gemini API key is configured. Add at least one key in Settings. Theo will not generate a simulated answer.", "API KEY REQUIRED", System.currentTimeMillis() - startTime)
        } else {
          tryCallGemini(prompt, memoryContext, apiKeys, activeModel, startTime, onStreamChunk)
        }
      }
    }

    TheoDiagnosticLogger.info("AI_ENGINE", "Query finished", "modelBadge=${result.modelBadge}, latencyMs=${result.latencyMs}, responseChars=${result.responseText.length}")
    if (result.modelBadge.contains("ERROR") || result.modelBadge.contains("FALLBACK") || result.modelBadge.contains("EMPTY")) {
      TheoDiagnosticLogger.warn("AI_ENGINE", "Query returned a diagnostic/fallback result", "modelBadge=${result.modelBadge}")
    }

    // Attach memory and action metadata
    result.copy(
      memoryExtracted = memoryExtraction,
      actionSuggested = actionIntent
    )
  }

  suspend fun testGeminiConnection(apiKeyInput: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val apiKeys = apiKeyInput.split(Regex("[\\r\\n,;]+"))
      .map { it.trim() }
      .filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
      .distinct()
    if (apiKeys.isEmpty()) return@withContext Pair(false, "No Gemini API key saved. Add a key, then tap Test Gemini API.")

    // Keep this diagnostic separate from generation: list models to test DNS,
    // TLS, routing, and API-key recognition without waiting for model inference.
    val testClient = OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(8, TimeUnit.SECONDS)
      .callTimeout(12, TimeUnit.SECONDS)
      .build()
    val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"

    val start = System.currentTimeMillis()
    try {
      var lastStatus = ""
      for ((index, key) in apiKeys.withIndex()) {
        var tryNextKey = true
        val request = Request.Builder()
          .url(url)
          .header("x-goog-api-key", key)
          .get()
          .build()
        try {
          testClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val elapsed = System.currentTimeMillis() - start
            if (response.isSuccessful) {
              val parsed = JSONObject(responseBody)
              val models = parsed.optJSONArray("models")
              if (models != null) {
                TheoDiagnosticLogger.info("GEMINI_TEST", "API route test succeeded", "keySlot=" + (index + 1) + ", HTTP " + response.code + ", latencyMs=" + elapsed)
                return@withContext Pair(true, "Google API reachable · HTTP " + response.code + " · " + elapsed + " ms. The key can list models. This does not yet prove text generation works.")
              }
              lastStatus = "HTTP " + response.code + ", but Google returned an unexpected models response."
              tryNextKey = false
            } else {
              val detail = try { JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty() }
                catch (_: Exception) { responseBody.take(180).replace(Regex("\\s+"), " ") }
              lastStatus = "Key " + (index + 1) + ": HTTP " + response.code + if (detail.isNotBlank()) " · " + detail else ""
              tryNextKey = response.code in listOf(401, 403, 429)
            }
          }
          if (!tryNextKey) break
        } catch (e: Exception) {
          lastStatus = when (e) {
            is java.net.SocketTimeoutException -> "Key " + (index + 1) + ": the lightweight Google API check timed out after 12 seconds before a response completed. Since this check does not run generation, test on mobile data vs Wi-Fi and disable VPN/private DNS/ad-blocking temporarily."
            is java.net.UnknownHostException -> "Gemini host could not be resolved. Check internet or DNS."
            is javax.net.ssl.SSLException -> "Secure connection failed. Check VPN, filtering, and device date/time."
            else -> "Connection test failed: " + (e.localizedMessage ?: e.javaClass.simpleName)
          }
          TheoDiagnosticLogger.error("GEMINI_TEST", "API test failed", e, "keySlot=" + (index + 1))
          break
        }
      }
      TheoDiagnosticLogger.warn("GEMINI_TEST", "API test did not pass", lastStatus)
      Pair(false, lastStatus.ifBlank { "Gemini API test failed. Check the saved key and connection." })
    } catch (e: Exception) {
      TheoDiagnosticLogger.error("GEMINI_TEST", "Unexpected API test error", e)
      Pair(false, "API test error: " + (e.localizedMessage ?: e.javaClass.simpleName))
    }
  }

  suspend fun testQwenConnection(termuxHost: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    TheoDiagnosticLogger.info("QWEN_BRIDGE", "Socket ping started", "host=${termuxHost.substringBefore("/v1")}")
    try {
      val url = if (termuxHost.endsWith("/")) "${termuxHost}models" else "$termuxHost/models"
      val request = Request.Builder()
        .url(url)
        .get()
        .build()

      val response = client.newCall(request).execute()
      response.use {
        if (it.isSuccessful) {
          TheoDiagnosticLogger.info("QWEN_BRIDGE", "Socket ping successful", "HTTP ${it.code}")
          Pair(true, "Connected to Local Qwen server (HTTP ${it.code})")
        } else {
          TheoDiagnosticLogger.warn("QWEN_BRIDGE", "Socket ping failed", "HTTP ${it.code}")
          Pair(false, "Server responded with code: ${it.code}")
        }
      }
    } catch (e: Exception) {
      TheoDiagnosticLogger.error("QWEN_BRIDGE", "Socket ping threw an exception", e)
      Pair(false, "Connection refused: ${e.localizedMessage ?: "Endpoint unreachable"}")
    }
  }

  private fun tryCallLocalQwen(
    prompt: String,
    memoryContext: String,
    host: String,
    startTime: Long
  ): EngineResult {
    return try {
      val endpoint = if (host.endsWith("/")) "${host}chat/completions" else "$host/chat/completions"
      val systemPrompt = "You are Theo, an advanced cybernetic personal AI assistant running locally on Android. $memoryContext. Respond with precision, clarity, and futuristic intelligence."

      val json = JSONObject().apply {
        put("model", "qwen3-1.7b")
        val messages = JSONArray().apply {
          put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
          })
          put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
          })
        }
        put("messages", messages)
        put("temperature", 0.7)
        put("max_tokens", 512)
        put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
      }

      val requestBody = json.toString().toRequestBody("application/json".toMediaType())
      val request = Request.Builder()
        .url(endpoint)
        .post(requestBody)
        .build()

      val response = localQwenClient.newCall(request).execute()
      val latency = System.currentTimeMillis() - startTime

      response.use { result ->
        val bodyStr = result.body?.string().orEmpty()
        if (!result.isSuccessful) {
          val detail = try {
            JSONObject(bodyStr).optJSONObject("error")?.optString("message").orEmpty()
          } catch (_: Exception) { bodyStr.take(240).replace(Regex("\\s+"), " ") }
          val suffix = if (detail.isBlank()) "" else ": $detail"
          TheoDiagnosticLogger.error("QWEN_BRIDGE", "Chat completion returned HTTP ${result.code}", details = detail)
          return@use EngineResult(
            responseText = "Local Qwen API rejected the chat request (HTTP ${result.code})$suffix. The server is reachable, but generation failed. Check the model/server response and try again.",
            modelBadge = "QWEN HTTP ERROR",
            latencyMs = latency
          )
        }
        val respJson = try {
          JSONObject(bodyStr)
        } catch (e: Exception) {
          TheoDiagnosticLogger.error("QWEN_BRIDGE", "Could not parse chat completion JSON", e)
          return@use EngineResult(
            responseText = "Local Qwen returned HTTP ${result.code}, but its response was not valid JSON. Details: ${e.localizedMessage ?: "JSON parse error"}.",
            modelBadge = "QWEN PARSE ERROR",
            latencyMs = latency
          )
        }
        val choices = respJson.optJSONArray("choices")
        val choice = choices?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val content = message?.optString("content").orEmpty().trim()
        if (content.isNotBlank()) {
          EngineResult(content, "LOCAL QWEN 1.7B", latency)
        } else {
          val finishReason = choice?.optString("finish_reason").orEmpty().ifBlank { "not provided" }
          val usage = respJson.optJSONObject("usage")
          val completionTokens = usage?.optInt("completion_tokens", -1) ?: -1
          val tokenNote = if (completionTokens >= 0) " Completion tokens: $completionTokens." else ""
          val explanation = if (finishReason == "length") "Generation hit its token limit before producing final text." else "The server response contained no final text."
          EngineResult(
            "Qwen server responded successfully, but Theo received an empty final answer. Finish reason: $finishReason. $explanation$tokenNote Thinking/reasoning text is intentionally not shown as the answer. Try the direct chat-completion test or check llama.cpp chat-template support.",
            "QWEN EMPTY RESPONSE",
            latency
          )
        }
      }
    } catch (e: Exception) {
      val latency = System.currentTimeMillis() - startTime
      TheoDiagnosticLogger.error("QWEN_BRIDGE", "Local Qwen chat request threw an exception", e, "endpoint=${host.substringBefore("/v1")}")
      val reason = when (e) {
        is java.net.SocketTimeoutException -> "Local Qwen took too long to generate a response (timeout)"
        else -> e.localizedMessage ?: "Local Qwen request failed"
      }
      generateLocalFallback(prompt, reason, latency)
    }
  }

  private fun tryCallGemini(
    prompt: String,
    memoryContext: String,
    apiKeys: List<String>,
    modelType: AiModelType,
    startTime: Long,
    onStreamChunk: (String) -> Unit
  ): EngineResult {
    return try {
      val modelName = "gemini-3.5-flash"
      val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse"

      val systemInstruction = "You are Theo, a premium cybernetic personal AI assistant and neural OS companion on Android. $memoryContext. Be concise, highly capable, elegant, and futuristic."

      val json = JSONObject().apply {
        // Keep the user message separate from Theo's system instructions. Explicitly
        // request minimal thinking and cap output so short chat messages don't wait
        // for unnecessarily long reasoning before Gemini sends response headers.
        put("systemInstruction", JSONObject().apply {
          put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
        })
        put("contents", JSONArray().put(
          JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
          }
        ))
        put("generationConfig", JSONObject().apply {
          put("maxOutputTokens", 1024)
          put("thinkingConfig", JSONObject().put("thinkingLevel", "MINIMAL"))
        })
      }

      val requestBody = json.toString().toRequestBody("application/json".toMediaType())
      var lastCode = 0
      var lastErrorBody = ""
      var answer = ""
      var usedKeyIndex = -1
      for ((index, key) in apiKeys.withIndex()) {
        // Gemini can briefly return 5xx during service overload. Retry that same
        // configured key once after a short pause; never substitute scripted text.
        var attempt = 0
        do {
          val request = Request.Builder().url(url).header("x-goog-api-key", key).header("Content-Type", "application/json").post(requestBody).build()
          val response = geminiClient.newCall(request).execute()
          response.use { res ->
            if (res.isSuccessful) {
              val streamedText = StringBuilder()
              val source = res.body?.source()
              if (source == null) {
                lastCode = res.code
                lastErrorBody = "Gemini returned an empty streaming response body."
              } else {
                while (!source.exhausted()) {
                  val line = source.readUtf8Line() ?: break
                  if (!line.startsWith("data:")) continue
                  val eventData = line.removePrefix("data:").trim()
                  if (eventData.isBlank() || eventData == "[DONE]") continue
                  try {
                    val parsed = JSONObject(eventData)
                    val parts = parsed.optJSONArray("candidates")
                      ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val delta = (0 until (parts?.length() ?: 0))
                      .mapNotNull { partIndex -> parts?.optJSONObject(partIndex)?.optString("text")?.takeIf { it.isNotBlank() } }
                      .joinToString("")
                    if (delta.isNotEmpty()) {
                      streamedText.append(delta)
                      onStreamChunk(delta)
                    }
                  } catch (parseError: Exception) {
                    TheoDiagnosticLogger.warn("GEMINI", "Ignored an unparseable streaming event", parseError.javaClass.simpleName)
                  }
                }
                answer = streamedText.toString().trim()
                usedKeyIndex = index
                if (answer.isBlank()) { lastCode = 200; lastErrorBody = "Gemini stream completed without text content." }
              }
            } else {
              lastCode = res.code
              lastErrorBody = res.body?.string().orEmpty()
            }
          }
          if (answer.isNotBlank()) break
          val transientServerError = lastCode in listOf(500, 502, 503, 504)
          if (attempt == 0 && transientServerError) {
            attempt++
            TheoDiagnosticLogger.warn("GEMINI", "Transient server error; retrying once", "HTTP $lastCode, keySlot=${index + 1}")
            Thread.sleep(600L)
          } else {
            break
          }
        } while (attempt <= 1)
        if (answer.isNotBlank()) break
        // Try another configured key for authentication or rate-limit failures.
        // A server overload is not treated as an invalid key.
        if (lastCode !in listOf(401, 403, 429)) break
      }
      val latency = System.currentTimeMillis() - startTime
      if (answer.isNotBlank()) {
        TheoDiagnosticLogger.info("GEMINI", "Generation succeeded", "keySlot=" + (usedKeyIndex + 1) + ", latencyMs=" + latency)
        EngineResult(answer, modelType.shortBadge, latency)
      } else {
        val detail = try { JSONObject(lastErrorBody).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { lastErrorBody.take(300) }
        val guidance = when (lastCode) {
          429 -> "Gemini quota or rate limit reached for configured keys. Wait for the quota window to reset or check usage limits."
          401 -> "Gemini returned HTTP 401: the configured credential is not accepted. Paste a Gemini API key created in Google AI Studio (not an OAuth access token, Google login cookie, or service-account JSON). Check that the key belongs to the intended Google Cloud project and has not been deleted or restricted incorrectly."
          403 -> "Gemini returned HTTP 403: the credential may be recognized, but the project, API access, or key restrictions may block this request. Check Gemini API access and the key's API/application restrictions."
          404 -> "Gemini model not found or unavailable to this key. Check the selected model ID."
          400 -> "Gemini rejected the request format."
          500, 502, 503, 504 -> "Google's Gemini service is temporarily overloaded or unavailable. Theo retried this request once; no simulated answer was used. Please try again shortly."
          else -> "Check your internet connection and Gemini API settings."
        }
        TheoDiagnosticLogger.error("GEMINI", "Generation failed", details = "HTTP " + lastCode + "; configured keys=" + apiKeys.size + "; " + detail)
        EngineResult("Gemini request failed (HTTP " + lastCode + "). " + guidance + if (detail.isNotBlank()) "\n\nGoogle details: " + detail else "", "GEMINI ERROR", latency)
      }
    } catch (e: Exception) {
      val latency = System.currentTimeMillis() - startTime
      val exceptionName = e.javaClass.simpleName.ifBlank { "Exception" }
      val detail = e.localizedMessage ?: e.message ?: "No additional details"
      TheoDiagnosticLogger.error(
        "GEMINI",
        "Gemini request threw $exceptionName",
        e,
        "latencyMs=$latency; exception=$exceptionName; detail=$detail"
      )
      val guidance = when (e) {
        is java.net.SocketTimeoutException ->
          "Gemini did not send response headers before the 45-second read timeout, even with low-latency generation settings. This is usually a slow/unreachable Google API route, transient service delay, or network filtering—not an answer generated by Theo. Try switching Wi-Fi/mobile data or retry later."
        is java.net.UnknownHostException ->
          "The Gemini host could not be resolved. Check your internet connection or DNS."
        is javax.net.ssl.SSLException ->
          "The secure connection to Gemini failed. Check device date/time, network filtering, or VPN settings."
        else ->
          "Check your internet connection and Gemini API settings."
      }
      EngineResult(
        responseText = "Gemini request failed ($exceptionName). $guidance No AI answer was generated.\n\nDetails: $detail",
        modelBadge = "GEMINI ERROR",
        latencyMs = latency
      )
    }
  }

  private fun generateLocalFallback(prompt: String, reason: String, latency: Long): EngineResult {
    val lowerReason = reason.lowercase()
    val message = when {
      lowerReason.contains("timeout") ->
        "The local Qwen chat request timed out while waiting for generation. The server may still be reachable; try a shorter prompt and retry."
      lowerReason.contains("failed to connect") || lowerReason.contains("connection refused") || lowerReason.contains("unable to resolve") ->
        "Theo could not open a connection to the configured local Qwen endpoint. Confirm the server is running at the configured URL."
      else ->
        "Theo's local Qwen request failed: $reason. This does not necessarily mean the server is offline; Ping Socket checks only the models endpoint."
    }
    return EngineResult(
      responseText = "$message No Qwen-generated answer was produced.",
      modelBadge = "QWEN REQUEST ERROR",
      latencyMs = latency
    )
  }

  private fun detectMemoryRequest(prompt: String): Pair<String, String>? {
    val lower = prompt.trim()
    val rememberPrefixes = listOf("remember that ", "remember: ", "note that ", "keep in mind that ", "save fact: ")
    for (prefix in rememberPrefixes) {
      if (lower.startsWith(prefix, ignoreCase = true)) {
        val content = lower.substring(prefix.length).trim()
        val parts = content.split(" is ", " = ", " are ", limit = 2)
        return if (parts.size == 2) {
          Pair(parts[0].trim().capitalizeFirst(), parts[1].trim())
        } else {
          Pair("Personal Note", content)
        }
      }
    }
    return null
  }

  private fun detectActionIntent(prompt: String): String? {
    val trimmed = prompt.trim().trimEnd('.', '!', '?')
    val lower = trimmed.lowercase()
    when {
      lower.matches(Regex("^(please\\s+)?(open|launch)\\s+(the\\s+)?settings$")) -> return "settings"
      lower.matches(Regex("^(please\\s+)?(open|launch)\\s+(the\\s+)?camera$")) ||
        lower.matches(Regex("^(please\\s+)?take\\s+(a\\s+)?photo$")) -> return "camera"
      lower.matches(Regex("^(please\\s+)?(set|open)\\s+(an?\\s+)?alarm$")) ||
        lower.matches(Regex("^(please\\s+)?open\\s+(the\\s+)?clock$")) -> return "clock"
      lower.contains("wifi") || lower.contains("wireless") -> return "wifi"
      lower.contains("battery stats") || lower.contains("battery settings") -> return "battery"
    }

    // Multi-step request: "open Spotify and play <song>".
    // Keep the requested provider alongside the track title for Android.
    val appAndMusicMatch = Regex(
      "^(?:please\\s+)?(?:open|launch|start)\\s+(?:the\\s+)?(spotify|youtube\\s+music)\\s+and\\s+(?:play|put\\s+on|listen\\s+to)\\s+(.+)$",
      RegexOption.IGNORE_CASE
    ).matchEntire(trimmed)
    if (appAndMusicMatch != null) {
      val provider = appAndMusicMatch.groupValues[1].lowercase().replace(Regex("\\s+"), "")
      val query = appAndMusicMatch.groupValues[2].trim()
      if (query.isNotBlank()) return "music:$provider|$query"
    }

    // Music requests open a real music search. This avoids claiming that a track
    // started playing when a provider may require track selection or sign-in.
    val musicMatch = Regex(
      "^(?:please\\s+)?(?:play|put\\s+on|listen\\s+to)\\s+(.+)$",
      RegexOption.IGNORE_CASE
    ).matchEntire(trimmed)
    if (musicMatch != null) {
      val query = musicMatch.groupValues[1].trim()
        .replace(Regex("^(?:the\\s+)?song\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(?:please|now)$", RegexOption.IGNORE_CASE), "")
      if (query.isNotBlank() && query.lowercase() !in setOf("something", "a song", "some music")) {
        return "music:$query"
      }
      if (query.lowercase() == "some music" || query.lowercase() == "music") return "music:popular music"
    }

    // Food/product ordering is a handoff, not an automatic purchase. The user
    // must still select a provider and explicitly confirm the final order.
    val orderMatch = Regex(
      "^(?:please\\s+)?(?:order|buy|get\\s+me|place\\s+an\\s+order\\s+for|make\\s+(?:me\\s+)?an\\s+order\\s+for)\\s+(.+)$",
      RegexOption.IGNORE_CASE
    ).matchEntire(trimmed)
    if (orderMatch != null) {
      val query = orderMatch.groupValues[1].trim().removePrefix("me ").trim()
      if (query.isNotBlank()) return "order:$query"
    }

    // Handle direct natural-language app launch requests, e.g. "open Bitchord",
    // "launch Discord", or "open the app named Spotify".
    val match = Regex(
      "^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:(?:the\\s+)?app\\s+(?:named\\s+|called\\s+)?|the\\s+)?(.+?)(?:\\s+app)?$",
      RegexOption.IGNORE_CASE
    ).matchEntire(trimmed) ?: return null
    val appName = match.groupValues[1].trim().replace(Regex("^[\\\"']|[\\\"']$"), "")
    if (appName.isBlank()) return null
    val nonAppTargets = setOf("settings", "camera", "clock", "alarm", "wifi", "wireless", "display", "battery")
    if (appName.lowercase() in nonAppTargets) return null
    return "app:$appName"
  }

  private fun String.capitalizeFirst(): String =
    if (isNotEmpty()) this[0].uppercaseChar() + substring(1) else this

  private fun getBuildConfigApiKey(): String {
    return try {
      val clazz = Class.forName("com.example.BuildConfig")
      val field = clazz.getField("GEMINI_API_KEY")
      field.get(null) as? String ?: ""
    } catch (e: Exception) {
      ""
    }
  }
}
