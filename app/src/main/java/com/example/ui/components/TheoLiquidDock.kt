package com.example.ui.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt

private data class DockDestination(
  val label: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val testTag: String
)

/**
 * Theo's navigation dock.
 *
 * The dock is deliberately achromatic. The whole dock gets a quiet liquid-glass surface while
 * the moving selection lens is the stronger optical element. The lens must not become a bright
 * white blob: it uses low-alpha edge light and refraction rather than a painted highlight.
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

  val tabContentBackdrop = rememberLayerBackdrop()

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 430.dp)
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 7.dp),
    contentAlignment = Alignment.Center
  ) {
    val safeTab = currentTab.coerceIn(tabs.indices)
    val itemWidth = (maxWidth - 8.dp) / tabs.size
    val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
    val lensWidth = (itemWidth - 6.dp).coerceAtLeast(54.dp)
    val dockShape = RoundedCornerShape(36.dp)
    val lensShape = RoundedCornerShape(31.dp)

    val selectedPosition by animateFloatAsState(
      targetValue = safeTab.toFloat(),
      animationSpec = spring(dampingRatio = 0.68f, stiffness = 330f),
      label = "TheoGlassSelectionTravel"
    )

    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(safeTab.toFloat()) }
    var dragStrain by remember { mutableFloatStateOf(0f) }
    var dragDirection by remember { mutableFloatStateOf(0f) }

    val lift by animateFloatAsState(
      targetValue = if (dragging) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
      label = "TheoGlassLensLift"
    )

    val livePosition = if (dragging) dragPosition else selectedPosition
    val liveLeft = 4.dp + itemWidth * livePosition + (itemWidth - lensWidth) / 2f

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        // A restrained neutral glass body. This is intentionally translucent instead of black
        // painted chrome, so the dock reads as one liquid surface with the lens inside it.
        .liquidGlassSurface(
          shape = dockShape,
          tint = Color.White.copy(alpha = 0.055f),
          gloss = 0.075f,
          refraction = 7.dp,
          blurRadius = 0.dp,
          opticalGlass = false
        )
        .pointerInput(safeTab, itemWidthPx) {
          detectDragGesturesAfterLongPress(
            onDragStart = {
              dragging = true
              dragPosition = selectedPosition.coerceIn(0f, tabs.lastIndex.toFloat())
              dragStrain = 0f
              dragDirection = 0f
            },
            onDragCancel = {
              dragging = false
              dragPosition = safeTab.toFloat()
              dragStrain = 0f
              dragDirection = 0f
            },
            onDragEnd = {
              val destination = dragPosition.roundToInt().coerceIn(tabs.indices)
              dragging = false
              dragStrain = 0f
              dragDirection = 0f
              if (destination != safeTab) onTabSelected(destination)
              else dragPosition = safeTab.toFloat()
            },
            onDrag = { change, amount ->
              change.consume()
              if (itemWidthPx > 0f) {
                dragPosition = (dragPosition + amount.x / itemWidthPx)
                  .coerceIn(0f, tabs.lastIndex.toFloat())
                dragStrain = (abs(amount.x) / itemWidthPx * 3.2f).coerceIn(0f, 1f)
                if (amount.x != 0f) dragDirection = if (amount.x > 0f) 1f else -1f
              }
            }
          )
        },
      contentAlignment = Alignment.CenterStart
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp)
          .layerBackdrop(tabContentBackdrop),
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          val proximity = (1f - abs(livePosition - index)).coerceIn(0f, 1f)
          DockDestinationItem(
            destination = tab,
            selected = index == safeTab,
            activeAmount = proximity,
            dragging = dragging && proximity > 0.45f,
            onClick = { onTabSelected(index) },
            modifier = Modifier.weight(1f).fillMaxHeight()
          )
        }
      }

      // Optical lens only. Keep the specular edge extremely restrained; the refracted content is
      // the glass, not a white fill painted over the destination.
      Box(
        modifier = Modifier
          .offset(x = liveLeft)
          .width(lensWidth)
          .height(62.dp)
          .padding(vertical = 3.dp)
          .graphicsLayer {
            scaleX = 1f + lift * 0.08f + dragStrain * 0.08f
            scaleY = 1f + lift * 0.035f - dragStrain * 0.02f
            rotationZ = -dragDirection * dragStrain * 0.8f
          }
          .drawBackdrop(
            backdrop = tabContentBackdrop,
            shape = { lensShape },
            effects = {
              lens(
                refractionHeight = (30f + lift * 8f).dp.toPx(),
                refractionAmount = (42f + lift * 14f).dp.toPx(),
                depthEffect = true,
                chromaticAberration = false
              )
            },
            highlight = {
              // BitChord-style optical edge, not a white glossy disk.
              Highlight.Default.copy(alpha = 0.055f + lift * 0.025f)
            },
            shadow = { Shadow(alpha = 0.035f + lift * 0.025f) }
          )
      )
    }
  }
}

@Composable
private fun DockDestinationItem(
  destination: DockDestination,
  selected: Boolean,
  activeAmount: Float,
  dragging: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val emphasis = maxOf(activeAmount, if (pressed) 1f else 0f)
  val iconScale by animateFloatAsState(
    targetValue = 0.96f + emphasis * 0.075f + if (dragging) 0.035f else 0f,
    animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
    label = "TheoDockIconScale"
  )

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
      tint = Color.White.copy(alpha = 0.60f + emphasis * 0.38f),
      modifier = Modifier
        .size(22.dp)
        .graphicsLayer {
          scaleX = iconScale
          scaleY = iconScale
        }
    )
    Text(
      text = destination.label,
      color = Color.White.copy(alpha = 0.53f + emphasis * 0.43f),
      fontSize = 10.sp,
      lineHeight = 12.sp,
      maxLines = 1,
      letterSpacing = 0.sp
    )
  }
}
