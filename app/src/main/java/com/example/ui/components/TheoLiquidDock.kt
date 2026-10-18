package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary

private data class DockDestination(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

/**
 * Floating liquid-glass navigation built on Theo's existing Kyant Backdrop renderer.
 * Only the dock and selected island opt into optical refraction; the rest of the scrolling UI
 * uses the lighter surface tier so the effect does not become a device-wide blur workload.
 */
@Composable
fun TheoBottomNavigation(
  currentTab: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val tabs = remember {
    listOf(
      DockDestination("Home", Icons.Default.AutoAwesome, "nav_core_tab"),
      DockDestination("Memory", Icons.Default.Storage, "nav_memory_tab"),
      DockDestination("Activity", Icons.Default.Timeline, "nav_activity_tab"),
      DockDestination("Logs", Icons.Default.Terminal, "nav_logs_tab"),
      DockDestination("Settings", Icons.Default.Settings, "nav_settings_tab")
    )
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 460.dp)
      .navigationBarsPadding()
      .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    val itemWidth = maxWidth / tabs.size
    val safeTab = currentTab.coerceIn(tabs.indices)
    val indicatorOffset by animateDpAsState(
      targetValue = itemWidth * safeTab + 5.dp,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = 600f),
      label = "TheoDockLiquidSlide"
    )
    val dockShape = RoundedCornerShape(36.dp)
    val islandShape = RoundedCornerShape(30.dp)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .clip(dockShape)
        .liquidGlassSurface(
          shape = dockShape,
          tint = Color(0x2B111D31),
          gloss = 0.14f,
          refraction = 10.dp,
          blurRadius = 6.dp,
          opticalGlass = true
        )
        .then(
          Modifier.background(Color.White.copy(alpha = 0.012f), dockShape)
        )
        .border(0.8.dp, Color.White.copy(alpha = 0.20f), dockShape)
    ) {
      Box(
        modifier = Modifier
          .offset(x = indicatorOffset)
          .width((itemWidth - 10.dp).coerceAtLeast(40.dp))
          .fillMaxHeight()
          .padding(vertical = 4.dp)
          .clip(islandShape)
          .liquidGlassSurface(
            shape = islandShape,
            tint = Color(0x3573BDEB),
            gloss = 0.17f,
            refraction = 12.dp,
            blurRadius = 6.dp,
            opticalGlass = true
          )
          .border(0.65.dp, Color.White.copy(alpha = 0.22f), islandShape)
      )

      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          DockDestinationItem(
            destination = tab,
            selected = index == safeTab,
            onClick = { onTabSelected(index) },
            modifier = Modifier.weight(1f).fillMaxHeight()
          )
        }
      }
    }
  }
}

@Composable
private fun DockDestinationItem(
  destination: DockDestination,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val iconScale by animateFloatAsState(
    targetValue = when {
      pressed -> 0.94f
      selected -> 1.04f
      else -> 0.97f
    },
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
    label = "TheoDockIconScale"
  )
  val iconTint = if (selected) Color(0xFFF7FBFF) else Color(0xFFB0BCD0)
  val labelTint = if (selected) Color(0xFFF2F8FF) else Color(0xC5C0CCDD)

  Column(
    modifier = modifier
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = onClick
      )
      .semantics { this.selected = selected }
      .padding(top = 3.dp, bottom = 3.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = destination.icon,
      contentDescription = destination.label,
      tint = iconTint,
      modifier = Modifier
        .size(21.dp)
        .graphicsLayer {
          scaleX = iconScale
          scaleY = iconScale
        }
    )
    Spacer(Modifier.height(3.dp))
    Text(
      text = destination.label,
      color = labelTint,
      fontSize = 10.5.sp,
      lineHeight = 12.sp,
      fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
      maxLines = 1,
      letterSpacing = 0.sp
    )
  }
}
