package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.builditcode.glass.LocalBackdropLayerManager
import com.builditcode.glass.layeredBackdropSource
import com.builditcode.glass.rememberBackdropManager
import com.example.service.TheoDiagnosticLogger
import com.example.ui.components.LocalTheoGlassBackdrop
import com.example.ui.components.THEO_DOCK_BACKDROP_LAYER
import com.example.ui.screens.ActivityHistoryScreen
import com.example.ui.screens.CoreAssistantScreen
import com.example.ui.screens.DiagnosticConsoleScreen
import com.example.ui.screens.MemoryMatrixScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoTheme
import com.example.viewmodel.TheoViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {

  private val viewModel: TheoViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    TheoDiagnosticLogger.initialize(applicationContext)
    TheoDiagnosticLogger.info("LIFECYCLE", "MainActivity onCreate", "savedInstanceState=${savedInstanceState != null}")
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      TheoDiagnosticLogger.error("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
      previousHandler?.uncaughtException(thread, throwable)
    }
    setContent {
      TheoTheme {
        TheoMainContent(viewModel = viewModel)
      }
    }
  }

  override fun onStart() { super.onStart(); TheoDiagnosticLogger.info("LIFECYCLE", "MainActivity onStart") }
  override fun onResume() {
    super.onResume()
    TheoDiagnosticLogger.info("LIFECYCLE", "MainActivity onResume")
    viewModel.refreshScreenAutomationStatus()
  }
  override fun onPause() { TheoDiagnosticLogger.info("LIFECYCLE", "MainActivity onPause"); super.onPause() }
  override fun onDestroy() { TheoDiagnosticLogger.info("LIFECYCLE", "MainActivity onDestroy", "changingConfigurations=$isChangingConfigurations"); super.onDestroy() }
}

@Composable
fun TheoMainContent(viewModel: TheoViewModel) {
  val ambientBackdrop = rememberLayerBackdrop(onDraw = {
    val glowA = Offset(size.width * 0.88f, size.height * 0.18f)
    val glowB = Offset(size.width * 0.08f, size.height * 0.70f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF102039), Color(0xFF070B18), Color(0xFF090D1D))))
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x6652BDEB), Color(0x2A286CBA), Color.Transparent),
        center = glowA,
        radius = size.minDimension * 0.82f
      ),
      radius = size.minDimension * 0.82f,
      center = glowA
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x4A8164E8), Color(0x208C6DE0), Color.Transparent),
        center = glowB,
        radius = size.minDimension * 0.76f
      ),
      radius = size.minDimension * 0.76f,
      center = glowB
    )
  })
  val dockBackdropManager = rememberBackdropManager(defaultScaleFactor = 0.5f, defaultDebounceMs = 16L)
  val uiState by viewModel.uiState.collectAsState()
  val messages by viewModel.messages.collectAsState()
  val memories by viewModel.memories.collectAsState()
  val activities by viewModel.activities.collectAsState()

  // Handle hardware back navigation: return to Core tab if on secondary tab
  BackHandler(enabled = uiState.currentTab != 0) {
    viewModel.setTab(0)
  }

  CompositionLocalProvider(
    LocalBackdropLayerManager provides dockBackdropManager,
    LocalTheoGlassBackdrop provides ambientBackdrop
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(TheoDarkBackground)
    ) {
      // Keep the captured scene full-height beneath the floating dock. A Scaffold bottomBar
      // shortens the source area and leaves the glass dock trying to refract an empty strip.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .layeredBackdropSource(THEO_DOCK_BACKDROP_LAYER)
          .layerBackdrop(ambientBackdrop)
      ) {
      Canvas(Modifier.fillMaxSize()) {
        val glowA = Offset(size.width * 0.88f, size.height * 0.18f)
        val glowB = Offset(size.width * 0.08f, size.height * 0.70f)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF102039), Color(0xFF070B18), Color(0xFF090D1D))))
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x6652BDEB), Color(0x2A286CBA), Color.Transparent),
            center = glowA,
            radius = size.minDimension * 0.82f
          ),
          radius = size.minDimension * 0.82f,
          center = glowA
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x4A8164E8), Color(0x208C6DE0), Color.Transparent),
            center = glowB,
            radius = size.minDimension * 0.76f
          ),
          radius = size.minDimension * 0.76f,
          center = glowB
        )
      }
      AnimatedContent(
        modifier = Modifier.fillMaxSize().padding(bottom = 108.dp),
        targetState = uiState.currentTab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "TabContentAnimation"
      ) { targetTab ->
        when (targetTab) {
          0 -> CoreAssistantScreen(
            uiState = uiState,
            messages = messages,
            onSendMessage = { viewModel.sendMessage(it) },
            onInputChange = { viewModel.setInputText(it) },
            onOrbClick = { viewModel.onOrbClicked() },
            onToggleSpeech = { viewModel.toggleSpeech() },
            onOpenSettings = { viewModel.setTab(4) },
            onExecuteAction = { viewModel.executeDeviceAction(it) },
            onDismissAction = { viewModel.dismissPendingAction() },
            onSelectApp = { viewModel.selectAppForPendingRequest(it) },
            onCancelAppSelection = { viewModel.dismissAppSelection() }
          )

          1 -> MemoryMatrixScreen(
            memories = memories,
            searchQuery = uiState.memorySearchQuery,
            selectedCategory = uiState.selectedCategoryFilter,
            onSearchChange = { viewModel.setMemorySearchQuery(it) },
            onCategorySelect = { viewModel.setCategoryFilter(it) },
            onAddMemory = { cat, k, v -> viewModel.addMemory(cat, k, v) },
            onDeleteMemory = { viewModel.deleteMemory(it) }
          )

          2 -> ActivityHistoryScreen(
            activities = activities,
            onClearHistory = { viewModel.clearActivities() }
          )

          3 -> DiagnosticConsoleScreen()

          4 -> SettingsScreen(
            uiState = uiState,
            onSaveSettings = { model, anim, key, groqKey ->
              viewModel.updateSettings(model, anim, key, groqKey)
            },
            onTestQwen = { viewModel.prepareLocalQwen() },
            onTestGemini = { viewModel.testGeminiConnection() },
            onTestGroq = { viewModel.testGroqConnection() },
            onClearChat = { viewModel.clearChatHistory() },
            onToggleSpeech = { viewModel.toggleSpeech() },
            isScreenAutomationEnabled = uiState.isScreenAutomationEnabled,
            isNotificationAccessEnabled = uiState.isNotificationAccessEnabled,
            isScreenAutomationRunning = uiState.isScreenAutomationRunning,
            onConfigureScreenAutomation = { viewModel.openAccessibilitySettings() },
            onConfigureNotificationAccess = { viewModel.openNotificationAccessSettings() },
            onStopScreenAutomation = { viewModel.stopScreenAutomation() }
          )
        }
      }
    }
      TheoBottomNavigation(
        currentTab = uiState.currentTab,
        onTabSelected = { viewModel.setTab(it) },
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}
