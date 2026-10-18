package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

private data class DockDestination(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

/**
 * Lightweight floating chrome. The dock is intentionally narrow and quiet: the content behind it
 * remains visible while the selected tab becomes a soft optical island instead of a solid pill.
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
      .widthIn(max = 430.dp)
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 7.dp),
    contentAlignment = Alignment.Center
  ) {
    val itemWidth = maxWidth / tabs.size
    val safeTab = currentTab.coerceIn(tabs.indices)
    val selectedWidth = (itemWidth - 8.dp).coerceAtLeast(48.dp)
    val indicatorOffset by animateDpAsState(
      targetValue = itemWidth * safeTab + 4.dp,
      animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
      label = "TheoDockLiquidSlide"
    )
    val dockShape = RoundedCornerShape(31.dp)
    val islandShape = RoundedCornerShape(25.dp)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .clip(dockShape)
        .liquidGlassSurface(
          shape = dockShape,
          tint = Color(0x20111D31),
          gloss = 0.13f,
          refraction = 10.dp,
          blurRadius = 7.dp,
          opticalGlass = true
        )
    ) {
      Box(
        modifier = Modifier
          .offset(x = indicatorOffset)
          .width(selectedWidth)
          .fillMaxHeight()
          .padding(vertical = 5.dp)
          .clip(islandShape)
          .liquidGlassSurface(
            shape = islandShape,
            tint = Color(0x2B73BDEB),
            gloss = 0.16f,
            refraction = 11.dp,
            blurRadius = 6.dp,
            opticalGlass = true
          )
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
      pressed -> 0.92f
      selected -> 1.06f
      else -> 1f
    },
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
    label = "TheoDockIconScale"
  )
  val iconTint = if (selected) Color.White else Color(0xC9B9C5D6)
  val labelTint = if (selected) Color.White else Color(0xAAB9C5D6)

  Column(
    modifier = modifier
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = onClick
      )
      .semantics { this.selected = selected }
      .padding(vertical = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = destination.icon,
      contentDescription = destination.label,
      tint = iconTint,
      modifier = Modifier
        .size(20.dp)
        .graphicsLayer {
          scaleX = iconScale
          scaleY = iconScale
        }
    )
    Text(
      text = destination.label,
      color = labelTint,
      fontSize = 9.5.sp,
      lineHeight = 11.sp,
      maxLines = 1,
      letterSpacing = 0.sp
    )
  }
}
