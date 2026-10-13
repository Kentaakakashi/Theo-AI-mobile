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
    memories: List<MemoryItem>
  ): EngineResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    // 1. Detect if the user is asking Theo to remember something
    TheoDiagnosticLogger.info("AI_ENGINE", "Query started", "model=$activeModel, promptChars=${prompt.length}, memories=${memories.size}")
    val memoryExtraction = detectMemoryRequest(prompt)

    // 2. Detect device intent actions. Native actions are handled by Android,
    // never by model-generated terminal text or a simulated execution report.
    val actionIntent = detectActionIntent(prompt)
    if (actionIntent != null) {
      val actionLabel = if (actionIntent.startsWith("app:")) {
        actionIntent.substringAfter(':')
      } else {
        actionIntent.replaceFirstChar { it.uppercase() }
      }
      TheoDiagnosticLogger.info("DEVICE_ACTION", "Action request recognized", "action=$actionIntent")
      return@withContext EngineResult(
        responseText = "I understood your request to open $actionLabel. Use the launch control below to ask Android to open it. I'll only report success if Android accepts the launch request.",
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
          tryCallGemini(prompt, memoryContext, apiKeys, activeModel, startTime)
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
    startTime: Long
  ): EngineResult {
    return try {
      val modelName = "gemini-3.5-flash"
      val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

      val systemInstruction = "You are Theo, a premium cybernetic personal AI assistant and neural OS companion on Android. $memoryContext. Be concise, highly capable, elegant, and futuristic."

      val json = JSONObject().apply {
        val contents = JSONArray().apply {
          put(JSONObject().apply {
            val parts = JSONArray().apply {
              put(JSONObject().apply {
                put("text", "$systemInstruction\n\nUser request: $prompt")
              })
            }
            put("parts", parts)
          })
        }
        put("contents", contents)
      }

      val requestBody = json.toString().toRequestBody("application/json".toMediaType())
      var lastCode = 0
      var lastErrorBody = ""
      var answer = ""
      var usedKeyIndex = -1
      for ((index, key) in apiKeys.withIndex()) {
        val request = Request.Builder().url(url).header("x-goog-api-key", key).header("Content-Type", "application/json").post(requestBody).build()
        val response = client.newCall(request).execute()
        response.use { res ->
          val body = res.body?.string().orEmpty()
          if (res.isSuccessful) {
            val parsed = JSONObject(body)
            val parts = parsed.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            answer = (0 until (parts?.length() ?: 0)).mapNotNull { i -> parts?.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() } }.joinToString("\n").trim()
            usedKeyIndex = index
            if (answer.isBlank()) { lastCode = 200; lastErrorBody = "Gemini returned no text content." }
          } else { lastCode = res.code; lastErrorBody = body }
        }
        if (answer.isNotBlank()) break
        if (lastCode !in listOf(401, 403, 429)) break
      }
      val latency = System.currentTimeMillis() - startTime
      if (answer.isNotBlank()) {
        TheoDiagnosticLogger.info("GEMINI", "Generation succeeded", "keySlot=" + (usedKeyIndex + 1) + ", latencyMs=" + latency)
        EngineResult(answer, modelType.shortBadge, latency)
      } else {
        val detail = try { JSONObject(lastErrorBody).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { lastErrorBody.take(300) }
        val guidance = when (lastCode) { 429 -> "Gemini quota or rate limit reached for configured keys."; 401, 403 -> "Gemini rejected all available keys. Check key validity and API access."; 404 -> "Gemini model not found or unavailable to this key."; 400 -> "Gemini rejected the request format."; 500, 502, 503, 504 -> "Gemini service is temporarily unavailable."; else -> "Check your internet connection and Gemini API settings." }
        TheoDiagnosticLogger.error("GEMINI", "Generation failed", details = "HTTP " + lastCode + "; configured keys=" + apiKeys.size + "; " + detail)
        EngineResult("Gemini request failed (HTTP " + lastCode + "). " + guidance + if (detail.isNotBlank()) "\n\nGoogle details: " + detail else "", "GEMINI ERROR", latency)
      }
    } catch (e: Exception) {
      val latency = System.currentTimeMillis() - startTime
      EngineResult(
        responseText = "Gemini is unavailable right now: ${e.localizedMessage ?: "network or response error"}. No AI answer was generated. Check your connection and API settings, then retry.",
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

  private fun generateSmartOfflineResponse(
    prompt: String,
    memories: List<MemoryItem>,
    modelType: AiModelType,
    startTime: Long
  ): EngineResult {
    val latency = System.currentTimeMillis() - startTime
    val base = synthesizeKnowledge(prompt)
    val text = if (memories.isNotEmpty() && prompt.contains("remember", ignoreCase = true)) {
      "Offline demo response — this is scripted text, not an AI-generated answer.\n\n$base"
    } else {
      "Offline demo response — no Gemini API key is configured, so this is scripted text rather than an AI-generated answer. Add your key in Settings to enable Gemini.\n\n$base"
    }

    return EngineResult(
      responseText = text,
      modelBadge = "OFFLINE DEMO",
      latencyMs = latency.coerceAtLeast(180L)
    )
  }

  private fun synthesizeKnowledge(prompt: String): String {
    val lower = prompt.lowercase().trim()
    return when {
      lower.contains("who are you") || lower.contains("what are you") -> {
        "I am **Theo** — your cybernetic personal AI companion and neural operating interface. I run natively with dual-engine architecture: Gemini Cloud for high-reasoning tasks and local Qwen for private on-device edge execution."
      }
      lower.contains("plan my day") || lower.contains("schedule") -> {
        "📋 **Neural Schedule Matrix**\n\n" +
          "• **09:00** — System sync & telemetry verification\n" +
          "• **10:30** — Deep focus block: Kotlin & native architecture refinement\n" +
          "• **13:00** — Energy recharge cycle\n" +
          "• **14:30** — Core intelligence review & Termux model benchmarking\n" +
          "• **17:00** — Daily reflection & Memory Matrix indexing\n\n" +
          "Would you like me to log any priority reminders for today?"
      }
      lower.contains("remember") -> {
        "Synthesizing memory pattern into local encrypted storage. Your preference has been locked into the Memory Matrix."
      }
      lower.contains("battery") || lower.contains("storage") || lower.contains("ram") || lower.contains("status") -> {
        "Telemetry scan complete. System diagnostics are operational. You can view real-time battery and memory stats in the HUD above."
      }
      lower.contains("settings") || lower.contains("camera") || lower.contains("alarm") || lower.contains("clock") -> {
        "Detected device orchestration command. Native intent ready for execution."
      }
      lower.contains("qwen") || lower.contains("termux") -> {
        "Qwen3-1.7B integration is designed for zero-cloud latency and full privacy. Run the server on `127.0.0.1:8080/v1` in Termux, and Theo communicates seamlessly over local socket."
      }
      else -> {
        "Affirmative. Neural query received: *\"$prompt\"*.\n\n" +
          "Synaptic processing complete. All local memory registers are aligned. How would you like to proceed with this task, Commander?"
      }
    }
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
