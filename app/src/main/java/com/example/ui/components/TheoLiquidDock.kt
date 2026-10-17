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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.builditcode.glass.BackdropFilter
import com.builditcode.glass.glassBorder
import com.builditcode.glass.layeredBackdropCapture

internal const val THEO_DOCK_BACKDROP_LAYER = "theo_dock_background"

private data class DockDestination(
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

/**
 * Theo's floating navigation dock.
 *
 * The dock captures the full-screen content source behind it, rather than trying to refract
 * a Scaffold content area that has already been shortened by a bottomBar. The active island
 * uses the same live source, so its glass moves with the selected destination instead of
 * behaving like a second unrelated neon border.
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
      .padding(horizontal = 14.dp, bottom = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    val itemWidth = maxWidth / tabs.size
    val safeTab = currentTab.coerceIn(tabs.indices)
    val indicatorOffset by animateDpAsState(
      targetValue = itemWidth * safeTab + 4.dp,
      animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
      label = "TheoDockLiquidSlide"
    )

    val dockShape = RoundedCornerShape(29.dp)
    val islandShape = RoundedCornerShape(24.dp)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(76.dp)
        .layeredBackdropCapture(
          layerName = THEO_DOCK_BACKDROP_LAYER,
          shape = dockShape,
          filter = BackdropFilter.Glass(
            shape = dockShape,
            blurRadiusIntensity = 3.2f,
            refraction = 0.18f,
            dispersion = 0.08f,
            edge = 0.24f,
            tint = Color(0x35101B2C)
          )
        )
        .glassBorder(
          shape = dockShape,
          borderColor = Color.White.copy(alpha = 0.34f),
          borderWidth = 1.dp,
          gapSize = 0.14f,
          softness = 0.045f,
          overlayBrush = Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.055f),
              Color.Transparent,
              Color(0xFF08111D).copy(alpha = 0.12f)
            )
          )
        )
    ) {
      // A single selected island glides below the tabs. Both the island and dock sample the
      // content layer, avoiding recursive/self-sampling and keeping the refraction consistent.
      Box(
        modifier = Modifier
          .offset(x = indicatorOffset)
          .width((itemWidth - 8.dp).coerceAtLeast(42.dp))
          .fillMaxHeight()
          .padding(vertical = 5.dp)
          .layeredBackdropCapture(
            layerName = THEO_DOCK_BACKDROP_LAYER,
            shape = islandShape,
            filter = BackdropFilter.Glass(
              shape = islandShape,
              blurRadiusIntensity = 2.4f,
              refraction = 0.23f,
              dispersion = 0.12f,
              edge = 0.32f,
              tint = Color(0x405B91C6)
            )
          )
          .glassBorder(
            shape = islandShape,
            borderColor = Color.White.copy(alpha = 0.28f),
            borderWidth = 0.8.dp,
            gapSize = 0.18f,
            softness = 0.04f,
            overlayBrush = Brush.verticalGradient(
              listOf(
                Color.White.copy(alpha = 0.09f),
                Color(0x1C6CB6E9),
                Color(0x1008101B)
              )
            )
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
      pressed -> 0.91f
      selected -> 1.06f
      else -> 0.96f
    },
    animationSpec = spring(dampingRatio = 0.56f, stiffness = 520f),
    label = "TheoDockIconScale"
  )

  val iconTint = if (selected) Color(0xFFF4FAFF) else Color(0xFF9DADC3)
  val labelTint = if (selected) Color(0xFFF0F7FF) else Color(0xB9B9C7D8)

  Column(
    modifier = modifier
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Tab,
        onClick = onClick
      )
      .semantics {
        this.selected = selected
      }
      .padding(top = 5.dp, bottom = 4.dp)
      .then(Modifier),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = destination.icon,
      contentDescription = destination.label,
      tint = iconTint,
      modifier = Modifier
        .size(22.dp)
        .graphicsLayer {
          scaleX = iconScale
          scaleY = iconScale
        }
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = destination.label,
      color = labelTint,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
      maxLines = 1,
      letterSpacing = 0.05.sp
    )
  }
}
