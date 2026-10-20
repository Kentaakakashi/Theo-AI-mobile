package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.example.data.MessageEntity
import com.example.model.AssistantState
import com.example.viewmodel.TheoUiState
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TheoWebChatScreen(
  uiState: TheoUiState,
  messages: List<MessageEntity>,
  onSendMessage: (String) -> Unit,
  onInputChange: (String) -> Unit,
  onOrbClick: () -> Unit,
  onToggleSpeech: () -> Unit,
  onOpenSettings: () -> Unit,
  onExecuteAction: (String) -> Unit,
  onDismissAction: () -> Unit,
  onSelectApp: (String) -> Unit,
  onCancelAppSelection: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var pageLoaded by remember { mutableStateOf(false) }
  val assetLoader = remember {
    WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
      .build()
  }
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  val bridge = remember {
    object {
      @JavascriptInterface fun sendMessage(text: String) { mainHandler.post { onSendMessage(text) } }
      @JavascriptInterface fun setInputText(text: String) { mainHandler.post { onInputChange(text) } }
      @JavascriptInterface fun orbClick() { mainHandler.post { onOrbClick() } }
      @JavascriptInterface fun toggleSpeech() { mainHandler.post { onToggleSpeech() } }
      @JavascriptInterface fun openSettings() { mainHandler.post { onOpenSettings() } }
      @JavascriptInterface fun executeAction(action: String) { mainHandler.post { onExecuteAction(action) } }
      @JavascriptInterface fun dismissAction() { mainHandler.post { onDismissAction() } }
      @JavascriptInterface fun selectApp(packageName: String) { mainHandler.post { onSelectApp(packageName) } }
      @JavascriptInterface fun cancelAppSelection() { mainHandler.post { onCancelAppSelection() } }
    }
  }
  val webView = remember {
    WebView(context).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      settings.setSupportZoom(false)
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false
      setBackgroundColor(android.graphics.Color.TRANSPARENT)
      addJavascriptInterface(bridge, "TheoBridge")
      webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
          Log.e("TheoWebView", "${message.message()} at ${message.sourceId()}:${message.lineNumber()}")
          return true
        }
      }
      webViewClient = object : WebViewClient() {
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
          super.onReceivedError(view, request, error)
          if (request.isForMainFrame) Log.e("TheoWebView", "Main page failed: ${error.errorCode} ${error.description} url=${request.url}")
        }
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
          super.onReceivedHttpError(view, request, response)
          Log.e("TheoWebView", "HTTP error ${response.statusCode} url=${request.url}")
        }
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
          assetLoader.shouldInterceptRequest(request.url)

        override fun onPageFinished(view: WebView, url: String) {
          pageLoaded = true
        }
      }
      loadUrl("https://appassets.androidplatform.net/assets/theo-web/index.html")
    }
  }

  val payload = remember(uiState, messages) {
    val messageArray = JSONArray()
    messages.forEach { message ->
      messageArray.put(JSONObject().apply {
        put("id", message.id)
        put("text", message.text)
        put("isUser", message.isUser)
        put("modelBadge", message.modelBadge)
        put("timestamp", message.timestamp)
        put("actionTag", message.actionTag)
      })
    }
    val candidates = JSONArray()
    uiState.pendingAppSelection?.candidates?.forEach { candidate ->
      candidates.put(JSONObject().apply {
        put("label", candidate.label)
        put("packageName", candidate.packageName)
      })
    }
    JSONObject().apply {
      put("state", when (uiState.assistantState) {
        AssistantState.IDLE -> "IDLE"
        AssistantState.LISTENING -> "LISTENING"
        AssistantState.THINKING -> "THINKING"
        AssistantState.SPEAKING -> "SPEAKING"
        AssistantState.OFFLINE -> "OFFLINE"
        AssistantState.ERROR -> "ERROR"
      })
      put("model", uiState.activeModel.shortBadge)
      put("input", uiState.inputText)
      put("streamingText", uiState.streamingText)
      put("speechEnabled", uiState.isSpeechEnabled)
      put("messages", messageArray)
      put("pendingAction", uiState.pendingActionTag ?: JSONObject.NULL)
      put("pendingActionLabel", uiState.pendingScreenTargetLabel ?: JSONObject.NULL)
      put("pendingAppName", uiState.pendingAppSelection?.requestedName ?: JSONObject.NULL)
      put("pendingAppCandidates", candidates)
      put("errorText", uiState.errorText ?: JSONObject.NULL)
    }.toString()
  }

  LaunchedEffect(payload, pageLoaded) {
    if (pageLoaded) {
      val quoted = JSONObject.quote(payload)
      webView.evaluateJavascript("window.theoUpdate && window.theoUpdate(JSON.parse($quoted));", null)
    }
  }
  DisposableEffect(webView) {
    onDispose { webView.removeJavascriptInterface("TheoBridge"); webView.destroy() }
  }
  AndroidView(
    factory = { webView },
    modifier = modifier.fillMaxSize(),
    update = { view ->
      val quoted = JSONObject.quote(payload)
      view.evaluateJavascript("window.theoUpdate && window.theoUpdate(JSON.parse($quoted));", null)
    }
  )
}
