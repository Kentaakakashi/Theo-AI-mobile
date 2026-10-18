package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private data class DockDestination(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

/**
 * Theo's bottom dock follows the BitChord-style interaction model:
 *
 * - inactive destinations remain visually open; they do not get a colored pill
 * - the active destination is represented by one translucent optical glass island
 * - holding the dock and dragging horizontally moves that island with the finger
 * - releasing snaps to the destination under the island
 * - tapping a destination still navigates normally
 *
 * The implementation is native to Theo rather than copying BitChord source code.
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
    val islandWidth = (itemWidth - 8.dp).coerceAtLeast(48.dp)
    val restingOffset = itemWidth * safeTab + 4.dp

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragTarget by remember { mutableIntStateOf(safeTab) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val islandWidthPx = with(density) { islandWidth.toPx() }
    val maxDragPx = with(density) { (itemWidth * (tabs.lastIndex)).toPx() }

    val targetOffset = restingOffset + with(density) { dragOffsetPx.toDp() }
    val indicatorOffset by animateDpAsState(
      targetValue = if (dragTarget == safeTab) targetOffset else itemWidth * dragTarget + 4.dp,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
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
          tint = Color.Transparent,
          gloss = 0.11f,
          refraction = 12.dp,
          blurRadius = 9.dp,
          opticalGlass = true
        )
        .pointerInput(safeTab, itemWidthPx) {
          detectDragGesturesAfterLongPress(
            onDragStart = {
              dragOffsetPx = 0f
              dragTarget = safeTab
            },
            onDragCancel = {
              dragOffsetPx = 0f
              dragTarget = safeTab
            },
            onDragEnd = {
              val shift = if (itemWidthPx > 0f) {
                (dragOffsetPx / itemWidthPx).roundToInt()
              } else 0
              val newTab = (safeTab + shift).coerceIn(tabs.indices)
              dragOffsetPx = 0f
              dragTarget = newTab
              if (newTab != safeTab) onTabSelected(newTab)
            },
            onDrag = { change, dragAmount ->
              change.consume()
              val raw = dragOffsetPx + dragAmount.x
              val minPx = if (safeTab == 0) -itemWidthPx * 0.25f else -itemWidthPx * safeTab
              val maxPx = if (safeTab == tabs.lastIndex) itemWidthPx * 0.25f else itemWidthPx * (tabs.lastIndex - safeTab)
              dragOffsetPx = raw.coerceIn(minPx, maxPx)

              val projected = safeTab + if (itemWidthPx > 0f) {
                (dragOffsetPx / itemWidthPx).roundToInt()
              } else 0
              dragTarget = projected.coerceIn(tabs.indices)
            }
          )
        }
    ) {
      Box(
        modifier = Modifier
          .offset(x = indicatorOffset)
          .width(islandWidth)
          .fillMaxHeight()
          .padding(vertical = 5.dp)
          .clip(islandShape)
          .liquidGlassSurface(
            shape = islandShape,
            tint = Color.Transparent,
            gloss = 0.22f,
            refraction = 16.dp,
            blurRadius = 10.dp,
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
            dragging = dragTarget == index && dragOffsetPx != 0f,
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
  dragging: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val iconScale by animateFloatAsState(
    targetValue = when {
      pressed -> 0.92f
      selected || dragging -> 1.07f
      else -> 1f
    },
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
    label = "TheoDockIconScale"
  )

  // Inactive destinations intentionally have no filled background. The only
  // material is the single moving glass island above the row.
  val iconTint = when {
    selected || dragging -> Color.White
    else -> Color(0xD0F2F2F4)
  }
  val labelTint = when {
    selected || dragging -> Color.White
    else -> Color(0xA8D6D6DA)
  }

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
