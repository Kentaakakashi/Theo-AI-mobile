package com.theoai.mobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Neutral liquid-glass navigation dock.
 *
 * The dock itself is intentionally almost colourless. The backdrop is allowed
 * to define the appearance of the glass; the active lens is only a transparent
 * white optical highlight. This keeps the material glass-like instead of
 * turning it into a blue/purple filled navigation bar.
 */
@Composable
fun TheoLiquidDock(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember {
        listOf(
            DockItem("Home", "✦"),
            DockItem("Memory", "☷"),
            DockItem("Activity", "⌁"),
            DockItem("Logs", "▣"),
            DockItem("Settings", "⚙"),
        )
    }

    val density = LocalDensity.current
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }

    val animatedIndexOffset by animateFloatAsState(
        targetValue = if (dragging) dragIndex.toFloat() else selectedIndex.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockSelection"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Transparent neutral dock material. No blue/purple fill is painted here.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.055f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            dragIndex = selectedIndex
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffset = 0f
                            dragIndex = selectedIndex
                        },
                        onDragEnd = {
                            dragging = false
                            onSelectedIndexChange(dragIndex)
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.x
                            val itemWidthPx = with(density) { 72.dp.toPx() }
                            val steps = (dragOffset / itemWidthPx).roundToInt()
                            dragIndex = (selectedIndex + steps).coerceIn(0, items.lastIndex)
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val distance = kotlin.math.abs(animatedIndexOffset - index)
                val active = distance < 0.45f
                val lensAlpha = (1f - distance).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // The active lens is neutral white/clear, never a theme colour.
                    if (lensAlpha > 0f) {
                        androidx.compose.foundation.Canvas(
                            Modifier
                                .size(70.dp, 64.dp)
                                .offset {
                                    IntOffset(
                                        x = ((animatedIndexOffset - index) * with(density) { 72.dp.toPx() }).roundToInt(),
                                        y = 0
                                    )
                                }
                        ) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.035f + 0.075f * lensAlpha),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.16f * lensAlpha),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                            )
                        }
                    }

                    ColumnLikeDockItem(item, active)
                }
            }
        }
    }
}

private data class DockItem(val label: String, val glyph: String)

@Composable
private fun ColumnLikeDockItem(item: DockItem, active: Boolean) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = item.glyph,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.72f),
            fontSize = if (active) 26.sp else 24.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp
        )
        Text(
            text = item.label,
            color = Color.White.copy(alpha = if (active) 0.95f else 0.62f),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 14.sp
        )
    }
}
