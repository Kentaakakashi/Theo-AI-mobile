package com.example.service

import android.content.Context
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Native Qwen model manager. GGUF weights live in shared Download/qwen-model,
 * outside app-private storage so uninstalling Theo does not delete the model.
 * llama.cpp loads and runs inference in-process, without Termux or a localhost server.
 */
class LocalQwenRuntime(context: Context) {
  private val appContext = context.applicationContext
  private val persistentModelDir = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "qwen-model"
  )
  private val modelFile = File(persistentModelDir, "Qwen3-1.7B-Q4_K_M.gguf")
  private val partialFile = File(persistentModelDir, "Qwen3-1.7B-Q4_K_M.gguf.partial")
  private val legacyPrivateModelFile = File(appContext.filesDir, "models/qwen/Qwen3-1.7B-Q4_K_M.gguf")
  private val mutex = Mutex()
  @Volatile private var loadedModel: LlamaModel? = null

  private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(0, TimeUnit.MILLISECONDS)
    .followRedirects(true)
    .build()

  fun isDownloaded(): Boolean =
    modelFile.isFile && modelFile.length() >= MINIMUM_MODEL_BYTES && hasGgufHeader(modelFile)

  /**
   * Android 11+ requires the user to grant All files access before Theo can manage a large
   * GGUF in shared Downloads and reopen it by a stable filesystem path after reinstall.
   */
  private fun ensurePersistentStorageAccess() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      throw IllegalStateException(
        "Persistent shared model storage currently requires Android 11 or newer. This device's Android version needs a separate storage-access implementation."
      )
    }
    if (!Environment.isExternalStorageManager()) {
      val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${appContext.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { appContext.startActivity(intent) }
      throw IllegalStateException(
        "Allow Theo to manage files in Android Settings, then return to Theo and tap Download & Load again. The model will be stored in Download/qwen-model and survive app reinstall."
      )
    }
  }

  /**
   * Downloads the model once, verifies its known SHA-256 and GGUF magic, loads it,
   * and runs a tiny real inference request before reporting success.
   */
  suspend fun prepare(onStatus: (String) -> Unit = {}): String = mutex.withLock {
    requireSupportedAbi()
    ensurePersistentStorageAccess()
    migrateLegacyPrivateModel(onStatus)
    val file = ensureDownloaded(onStatus)
    val readyModel = loadedModel ?: run {
      onStatus("Loading Qwen3-1.7B into memory · CPU mode · 2K context…")
      Llama.loadModel(
        modelPath = file.absolutePath,
        config = LlamaConfig(
          contextSize = 2048,
          threads = 2,
          gpuLayers = 0,
          temperature = 0.2f,
          topP = 0.9f,
          topK = 40
        )
      ).also { loadedModel = it }
    }
    onStatus("Running a short on-device inference check…")
    val probe = Llama.complete(
      model = readyModel,
      prompt = "Reply with exactly: Qwen is ready.\n/no_think",
      systemPrompt = "You are a local inference self-test. Follow the requested output exactly.",
      maxTokens = 16
    )
    if (probe.text.isBlank()) throw IOException("Qwen loaded but returned an empty test response.")
    "Ready · Qwen3-1.7B generated a test response inside Theo. CPU inference; no Termux server or cloud call."
  }

  suspend fun generate(
    prompt: String,
    systemPrompt: String,
    maxTokens: Int = 512
  ): String = mutex.withLock {
    requireSupportedAbi()
    ensurePersistentStorageAccess()
    val model = loadedModel ?: run {
      if (!isDownloaded()) {
        throw IllegalStateException("The local Qwen model is not downloaded yet. Open Settings → On-device Qwen Runtime and tap Download & Load Qwen.")
      }
      // A downloaded model is reusable after Theo restarts; load it lazily when needed.
      Llama.loadModel(
        modelPath = modelFile.absolutePath,
        config = LlamaConfig(
          contextSize = 2048,
          threads = 2,
          gpuLayers = 0,
          temperature = 0.7f,
          topP = 0.9f,
          topK = 40
        )
      ).also { loadedModel = it }
    }
    val result = Llama.complete(
      model = model,
      prompt = prompt.trimEnd() + "\n/no_think",
      systemPrompt = systemPrompt,
      maxTokens = maxTokens.coerceIn(16, 768)
    )
    result.text.trim().ifBlank { throw IOException("Native Qwen inference returned no answer text.") }
  }

  /** Free native model memory when Theo's view model is permanently cleared. */
  fun release() {
    // Don't free native memory while another coroutine is inside llama.cpp.
    // If inference is still active, defer cleanup to process exit rather than risking native use-after-free.
    if (!mutex.tryLock()) return
    try {
      val model = loadedModel ?: return
      loadedModel = null
      runCatching { Llama.releaseModel(model) }
    } finally {
      mutex.unlock()
    }
  }

  private suspend fun migrateLegacyPrivateModel(onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
    if (isDownloaded() || !legacyPrivateModelFile.isFile ||
      legacyPrivateModelFile.length() < MINIMUM_MODEL_BYTES ||
      !hasGgufHeader(legacyPrivateModelFile)
    ) return@withContext

    onStatus("Moving your existing Qwen model into persistent Download/qwen-model storage…")
    persistentModelDir.mkdirs()
    val migrationFile = File(persistentModelDir, "Qwen3-1.7B-Q4_K_M.gguf.migrating")
    migrationFile.delete()
    legacyPrivateModelFile.inputStream().use { input ->
      FileOutputStream(migrationFile).use { output ->
        input.copyTo(output, 1024 * 1024)
        output.fd.sync()
      }
    }
    if (migrationFile.length() != legacyPrivateModelFile.length() || !hasGgufHeader(migrationFile)) {
      migrationFile.delete()
      throw IOException("Could not safely move the existing Qwen model into persistent storage.")
    }
    if (modelFile.exists() && !modelFile.delete()) {
      migrationFile.delete()
      throw IOException("Could not replace an invalid persistent Qwen model file.")
    }
    if (!migrationFile.renameTo(modelFile)) {
      migrationFile.delete()
      throw IOException("Could not finalize the Qwen model migration.")
    }
    // Only remove the old private copy after the persistent copy is complete.
    legacyPrivateModelFile.delete()
  }

  private suspend fun ensureDownloaded(onStatus: (String) -> Unit): File = withContext(Dispatchers.IO) {
    modelFile.parentFile?.mkdirs()
    if (isDownloaded()) {
      onStatus("Model file verified · loading local Qwen…")
      return@withContext modelFile
    }
    if (modelFile.exists()) modelFile.delete()
    partialFile.delete()
    onStatus("Connecting to Hugging Face to download the 1.11 GB Qwen model…")
    val request = Request.Builder()
      .url(MODEL_URL)
      .header("Accept", "application/octet-stream")
      .build()
    try {
      downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Qwen model download failed: HTTP ${response.code}")
        val body = response.body ?: throw IOException("Hugging Face returned an empty model response.")
        val total = body.contentLength().takeIf { it > 0L }
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        var lastReportedMb = -1L
        body.byteStream().use { input ->
          FileOutputStream(partialFile).use { rawOutput ->
            rawOutput.buffered(256 * 1024).use { output ->
              val buffer = ByteArray(256 * 1024)
              while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                downloaded += count
                val downloadedMb = downloaded / (1024L * 1024L)
                if (downloadedMb != lastReportedMb && (downloadedMb % 8L == 0L || (total != null && downloaded == total))) {
                  lastReportedMb = downloadedMb
                  val progress = if (total != null) {
                    val percent = (downloaded * 100L / total).coerceIn(0L, 100L)
                    "Downloading Qwen model · $percent% · ${formatMb(downloaded)} / ${formatMb(total)}"
                  } else {
                    "Downloading Qwen model · ${formatMb(downloaded)} received"
                  }
                  onStatus(progress)
                }
              }
              output.flush()
              rawOutput.fd.sync()
            }
          }
        }
        if (downloaded < MINIMUM_MODEL_BYTES) throw IOException("Downloaded Qwen file is too small ($downloaded bytes).")
        if (!hasGgufHeader(partialFile)) throw IOException("The downloaded file is not a valid GGUF model.")
        onStatus("Verifying Qwen model checksum…")
        val actualHash = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        if (!actualHash.equals(EXPECTED_SHA256, ignoreCase = true)) {
          throw IOException("Qwen model checksum did not match the published file. The partial download was discarded.")
        }
        if (modelFile.exists() && !modelFile.delete()) throw IOException("Could not replace the previous Qwen model file.")
        if (!partialFile.renameTo(modelFile)) throw IOException("Could not finalize the verified Qwen model download.")
        onStatus("Qwen model download verified · loading into memory…")
      }
    } catch (error: Exception) {
      partialFile.delete()
      throw error
    }
    modelFile
  }

  private fun requireSupportedAbi() {
    if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
      throw IllegalStateException("This native Qwen runtime requires an arm64-v8a Android device. Theo can still use Gemini on this device.")
    }
  }

  private fun hasGgufHeader(file: File): Boolean = runCatching {
    FileInputStream(file).use { input ->
      val bytes = ByteArray(4)
      input.read(bytes) == 4 && String(bytes, Charsets.US_ASCII) == "GGUF"
    }
  }.getOrDefault(false)

  private fun formatMb(bytes: Long): String = "%.0f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))

  companion object {
    // Official Unsloth-hosted Qwen3-1.7B Q4_K_M GGUF. SHA-256 pins the expected file contents.
    private const val MODEL_URL =
      "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
    private const val EXPECTED_SHA256 =
      "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897"
    private const val MINIMUM_MODEL_BYTES = 1_000_000_000L
  }
}
