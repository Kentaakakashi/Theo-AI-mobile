package com.example.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * User-owned, SAF-selected persistent screen memory.
 *
 * Data is written to the user's chosen document folder, not Theo's private app directory.
 * The selected folder and persisted URI permission survive app updates and normally survive
 * uninstall as long as the user does not delete the folder. No raw screenshots are stored.
 */
internal object PersistentScreenMemoryStore {
  private const val PREFS = "theo_prefs"
  private const val TREE_URI_KEY = "screen_memory_tree_uri"
  private const val FILE_NAME = "Theo_Screen_Memory.json"
  private const val MAX_OBSERVATIONS = 500
  private val lock = Any()

  fun hasFolder(context: Context): Boolean =
    !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(TREE_URI_KEY, null).isNullOrBlank()

  fun setFolderUri(context: Context, uri: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(TREE_URI_KEY, uri).apply()
  }

  fun folderUri(context: Context): Uri? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(TREE_URI_KEY, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

  /** Return a small, app-specific recall snippet; avoid feeding the entire history to the API. */
  fun loadRecentContext(context: Context, packageName: String, limit: Int = 5): String {
    val tree = folderUri(context) ?: return ""
    return synchronized(lock) {
      runCatching {
        val records = readRecords(context, tree)
        val matching = (0 until records.length())
          .mapNotNull { records.optJSONObject(it) }
          .filter { it.optString("package") == packageName }
          .takeLast(40)
          .asReversed()
          .distinctBy { it.optString("description").trim() }
          .take(limit)
          .asReversed()
        matching.joinToString("\n") { item ->
          "- " + item.optString("description").replace("\n", " ").take(500)
        }
      }.getOrDefault("")
    }
  }

  /** Record every successfully analyzed screenshot; keep a bounded rolling history. */
  fun recordObservation(context: Context, packageName: String, description: String): Boolean {
    val tree = folderUri(context) ?: return false
    if (packageName.isBlank() || description.isBlank()) return false
    return synchronized(lock) {
      runCatching {
        val records = readRecords(context, tree)
        records.put(JSONObject().apply {
          put("timestamp", System.currentTimeMillis())
          put("package", packageName)
          put("description", description.take(2500))
        })
        val trimmed = JSONArray()
        val start = (records.length() - MAX_OBSERVATIONS).coerceAtLeast(0)
        for (index in start until records.length()) trimmed.put(records.getJSONObject(index))
        writeRecords(context, tree, trimmed)
        true
      }.getOrDefault(false)
    }
  }

  private fun readRecords(context: Context, tree: Uri): JSONArray {
    val file = findMemoryFile(context, tree) ?: return JSONArray()
    return try {
      val raw = context.contentResolver.openInputStream(file)?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (raw.isBlank()) JSONArray() else JSONArray(raw)
    } catch (_: Exception) {
      // A damaged file is not overwritten silently; fail the caller so the original remains recoverable.
      throw IOException("Theo screen memory could not be read. The existing file was left untouched.")
    }
  }

  private fun writeRecords(context: Context, tree: Uri, records: JSONArray) {
    val file = findMemoryFile(context, tree)
      ?: DocumentsContract.createDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "application/json", FILE_NAME)
      ?: throw IOException("Android could not create Theo_Screen_Memory.json in the selected folder.")
    context.contentResolver.openOutputStream(file, "wt")?.bufferedWriter()?.use {
      it.write(records.toString(2))
    } ?: throw IOException("Android could not write to the selected memory folder.")
  }

  private fun findMemoryFile(context: Context, tree: Uri): Uri? {
    val treeId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
    context.contentResolver.query(
      children,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null, null, null
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (cursor.moveToNext()) {
        if (cursor.getString(nameColumn) == FILE_NAME) {
          return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
        }
      }
    }
    return null
  }
}
