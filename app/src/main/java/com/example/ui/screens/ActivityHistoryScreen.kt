package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityEntity
import com.example.ui.components.hudScanlines
import com.example.ui.components.neonBorder
import com.example.ui.components.liquidGlassSurface
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoOnlineGreen
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import com.example.ui.theme.TheoWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityHistoryScreen(
  activities: List<ActivityEntity>,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(colors = listOf(Color(0xFF0A1423), TheoDarkBackground, Color(0xFF07101D))))
      .hudScanlines()
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      // Top HUD Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "SYNAPSE ACTIVITY",
            color = TheoTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
          )
          Text(
            text = "// ${activities.size} TELEMETRY EVENTS RECORDED",
            color = TheoCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        if (activities.isNotEmpty()) {
          IconButton(
            onClick = onClearHistory,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(TheoSurfaceGlass)
                .liquidGlassSurface(RoundedCornerShape(14.dp), tint = TheoSurfaceGlass, gloss = 0.12f)
              .testTag("clear_activity_btn")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = "Clear Activities",
              tint = TheoTextMuted,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (activities.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Timeline,
              contentDescription = null,
              tint = TheoTextMuted,
              modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No neural events logged yet.",
              color = TheoTextMuted,
              fontSize = 13.sp
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(activities, key = { it.id }) { activity ->
            ActivityTimelineItem(activity = activity)
          }
          item {
            Spacer(modifier = Modifier.height(80.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ActivityTimelineItem(activity: ActivityEntity) {
  val timeStr = SimpleDateFormat("HH:mm:ss · dd MMM", Locale.getDefault()).format(Date(activity.timestamp))

  val (icon, tintColor) = when (activity.type) {
    "CHAT" -> Pair(Icons.Default.ChatBubbleOutline, TheoCyan)
    "MEMORY" -> Pair(Icons.Default.Memory, TheoViolet)
    "DEVICE" -> Pair(Icons.Default.PhoneAndroid, TheoOnlineGreen)
    else -> Pair(Icons.Default.Security, TheoWarningAmber)
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(TheoSurfaceGlass)
                .liquidGlassSurface(RoundedCornerShape(14.dp), tint = TheoSurfaceGlass, gloss = 0.12f)
      .neonBorder(RoundedCornerShape(12.dp), strokeWidth = 1.dp)
      .padding(12.dp)
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(tintColor.copy(alpha = 0.15f))
          .border(1.dp, tintColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = activity.type,
          tint = tintColor,
          modifier = Modifier.size(16.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = activity.title,
            color = TheoTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = activity.type,
            color = tintColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
          text = activity.subtitle,
          color = TheoTextSecondary,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = timeStr,
          color = TheoTextMuted,
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp
        )
      }
    }
  }
}
