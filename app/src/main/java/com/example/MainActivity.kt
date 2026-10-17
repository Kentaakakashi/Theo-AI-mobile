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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.ActivityHistoryScreen
import com.example.ui.screens.CoreAssistantScreen
import com.example.ui.screens.DiagnosticConsoleScreen
import com.example.service.TheoDiagnosticLogger
import com.example.ui.screens.MemoryMatrixScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
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
        onTabSelected = { viewModel.setTab(it) }
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = paddingValues.calculateBottomPadding())
    ) {
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

data class NavTabItem(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

@Composable
fun TheoBottomNavigation(
  currentTab: Int,
  onTabSelected: (Int) -> Unit
) {
  val tabs = listOf(
    NavTabItem("Home", Icons.Default.AutoAwesome, "nav_core_tab"),
    NavTabItem("Memory", Icons.Default.Storage, "nav_memory_tab"),
    NavTabItem("Activity", Icons.Default.Timeline, "nav_activity_tab"),
    NavTabItem("Logs", Icons.Default.Terminal, "nav_logs_tab"),
    NavTabItem("Settings", Icons.Default.Settings, "nav_settings_tab")
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color.Transparent,
            Color(0xEE090D14),
            Color(0xFF090D14)
          )
        )
      )
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 16.dp, vertical = 6.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(26.dp))
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xA83D5A80),
              Color(0xDA101A2B),
              Color(0xB8213049)
            )
          )
        )
        .border(
          1.dp,
          Brush.linearGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.36f),
              TheoCyan.copy(alpha = 0.35f),
              Color.White.copy(alpha = 0.08f)
            )
          ),
          RoundedCornerShape(26.dp)
        )
        .padding(horizontal = 6.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      tabs.forEachIndexed { index, tabItem ->
        val isSelected = currentTab == index
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) TheoCyan.copy(alpha = 0.15f) else Color.Transparent)
            .border(
              1.dp,
              if (isSelected) TheoCyan.copy(alpha = 0.5f) else Color.Transparent,
              RoundedCornerShape(16.dp)
            )
            .clickable { onTabSelected(index) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tabItem.testTag),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = tabItem.icon,
              contentDescription = tabItem.label,
              tint = if (isSelected) TheoCyan else TheoTextMuted,
              modifier = Modifier.size(16.dp)
            )
            if (isSelected) {
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = tabItem.label,
                color = TheoCyan,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
              )
            }
          }
        }
      }
    }
  }
}
