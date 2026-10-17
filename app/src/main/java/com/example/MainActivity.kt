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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.example.ui.components.LocalTheoGlassBackdrop
import com.example.ui.components.liquidGlassSurface
import com.example.ui.screens.ActivityHistoryScreen
import com.example.ui.screens.CoreAssistantScreen
import com.example.ui.screens.DiagnosticConsoleScreen
import com.example.service.TheoDiagnosticLogger
import com.example.ui.screens.MemoryMatrixScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTheme
import com.example.ui.theme.TheoViolet
import com.example.viewmodel.TheoViewModel

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
  val screenBackdrop = rememberLayerBackdrop()
  val ambientBackdrop = rememberLayerBackdrop(onDraw = {
    val glowA = Offset(size.width * 0.88f, size.height * 0.18f)
    val glowB = Offset(size.width * 0.08f, size.height * 0.70f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1728), Color(0xFF050810), Color(0xFF090D1A))))
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x443A8DCC), Color(0x1B2860B0), Color.Transparent),
        center = glowA,
        radius = size.minDimension * 0.82f
      ),
      radius = size.minDimension * 0.82f,
      center = glowA
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x2E7862D8), Color(0x128C6DE0), Color.Transparent),
        center = glowB,
        radius = size.minDimension * 0.76f
      ),
      radius = size.minDimension * 0.76f,
      center = glowB
    )
  })
  val uiState by viewModel.uiState.collectAsState()
  val messages by viewModel.messages.collectAsState()
  val memories by viewModel.memories.collectAsState()
  val activities by viewModel.activities.collectAsState()

  // Handle hardware back navigation: return to Core tab if on secondary tab
  BackHandler(enabled = uiState.currentTab != 0) {
    viewModel.setTab(0)
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = TheoDarkBackground,
    bottomBar = {
      TheoBottomNavigation(
        currentTab = uiState.currentTab,
        backdrop = screenBackdrop,
        onTabSelected = { viewModel.setTab(it) }
      )
    }
  ) { paddingValues ->
    CompositionLocalProvider(LocalTheoGlassBackdrop provides ambientBackdrop) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = paddingValues.calculateBottomPadding())
        .layerBackdrop(screenBackdrop)
        .layerBackdrop(ambientBackdrop)
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val glowA = Offset(size.width * 0.88f, size.height * 0.18f)
        val glowB = Offset(size.width * 0.08f, size.height * 0.70f)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1728), Color(0xFF050810), Color(0xFF090D1A))))
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x443A8DCC), Color(0x1B2860B0), Color.Transparent),
            center = glowA,
            radius = size.minDimension * 0.82f
          ),
          radius = size.minDimension * 0.82f,
          center = glowA
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x2E7862D8), Color(0x128C6DE0), Color.Transparent),
            center = glowB,
            radius = size.minDimension * 0.76f
          ),
          radius = size.minDimension * 0.76f,
          center = glowB
        )
      }
      AnimatedContent(
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
    }
  }
}

data class NavTabItem(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

@Composable
fun TheoBottomNavigation(
  currentTab: Int,
  backdrop: Backdrop,
  onTabSelected: (Int) -> Unit
) {
  val tabs = listOf(
    NavTabItem("Home", Icons.Default.AutoAwesome, "nav_core_tab"),
    NavTabItem("Memory", Icons.Default.Storage, "nav_memory_tab"),
    NavTabItem("Activity", Icons.Default.Timeline, "nav_activity_tab"),
    NavTabItem("Logs", Icons.Default.Terminal, "nav_logs_tab"),
    NavTabItem("Settings", Icons.Default.Settings, "nav_settings_tab")
  )

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 14.dp, vertical = 7.dp),
    contentAlignment = Alignment.Center
  ) {
    val itemWidth = maxWidth / tabs.size
    val indicatorX by animateDpAsState(
      targetValue = itemWidth * currentTab,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
      label = "LiquidTabIndicatorOffset"
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
        .drawBackdrop(
          backdrop = backdrop,
          shape = { RoundedCornerShape(30.dp) },
          effects = {
            vibrancy()
            blur(13.dp.toPx())
            lens(
              refractionHeight = 18.dp.toPx(),
              refractionAmount = 23.dp.toPx(),
              depthEffect = true,
              chromaticAberration = false
            )
          },
          highlight = { Highlight.Default.copy(alpha = 0.82f) },
          shadow = { Shadow.Default },
          onDrawSurface = { drawRect(Color(0x64101829)) }
        )
        .clip(RoundedCornerShape(30.dp))
        .border(
          1.dp,
          Brush.linearGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.34f),
              TheoCyan.copy(alpha = 0.28f),
              Color.White.copy(alpha = 0.075f)
            )
          ),
          RoundedCornerShape(30.dp)
        )
    ) {
      // A second lens rides beneath the selected destination, echoing Backdrop's liquid-tab demo.
      Box(
        modifier = Modifier
          .offset(x = indicatorX)
          .width(itemWidth)
          .fillMaxHeight()
          .padding(4.dp)
          .drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(25.dp) },
            effects = {
              vibrancy()
              blur(7.dp.toPx())
              lens(
                refractionHeight = 23.dp.toPx(),
                refractionAmount = 28.dp.toPx(),
                depthEffect = true,
                chromaticAberration = true
              )
            },
            highlight = { Highlight.Default.copy(alpha = 0.95f) },
            shadow = { Shadow.Default },
            onDrawSurface = {
              drawRect(
                Brush.verticalGradient(
                  listOf(Color(0x662C5276), Color(0x5522314D), Color(0x48202940))
                )
              )
            }
          )
          .border(
            1.dp,
            Brush.linearGradient(
              listOf(Color.White.copy(alpha = 0.30f), TheoCyan.copy(alpha = 0.24f), Color.Transparent)
            ),
            RoundedCornerShape(25.dp)
          )
      )

      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tabItem ->
          val selected = currentTab == index
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable { onTabSelected(index) }
              .testTag(tabItem.testTag)
              .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = tabItem.icon,
              contentDescription = tabItem.label,
              tint = if (selected) TheoCyan else TheoTextMuted,
              modifier = Modifier.size(if (selected) 21.dp else 19.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
              text = tabItem.label,
              color = if (selected) TheoTextPrimary else TheoTextMuted,
              fontSize = 9.sp,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              letterSpacing = 0.sp,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}
