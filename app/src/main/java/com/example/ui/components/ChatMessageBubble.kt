package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageBubble(
  message: MessageEntity,
  onExecuteAction: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

  AnimatedVisibility(
    visible = true,
    enter = fadeIn() + slideInVertically { it / 2 },
    modifier = modifier.fillMaxWidth()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp, horizontal = 12.dp),
      contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
      if (message.isUser) {
        // Operator Message Bubble
        Column(
          horizontalAlignment = Alignment.End,
          modifier = Modifier.widthIn(max = 290.dp)
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    Color(0xA62F326A),
                    Color(0x74121D34)
                  )
                )
              )
              .liquidGlassSurface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                tint = Color(0x561C2850),
                gloss = 0.22f,
                refraction = 8.dp,
                blurRadius = 5.dp
              )
              .neonBorder(
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = listOf(TheoViolet, TheoCyan, TheoViolet),
                strokeWidth = 1.dp
              )
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag("user_chat_bubble")
          ) {
            Text(
              text = message.text,
              color = TheoTextPrimary,
              fontSize = 14.sp,
              lineHeight = 20.sp
            )
          }

          Row(
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "You",
              color = TheoTextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = timeStr,
              color = TheoTextMuted,
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp
            )
          }
        }
      } else {
        // Theo Assistant Message Bubble
        Column(
          horizontalAlignment = Alignment.Start,
          modifier = Modifier.widthIn(max = 320.dp)
        ) {
          // Metadata badge row
          Row(
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(TheoCyan)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = message.modelBadge.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
              color = TheoCyan,
              fontFamily = FontFamily.SansSerif,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold
            )
            if (message.latencyMs > 0L) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${message.latencyMs}ms",
                color = TheoTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
              )
            }
          }

          // Card content
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
              .background(TheoSurfaceGlass)
                .liquidGlassSurface(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp), tint = TheoSurfaceGlass, gloss = 0.12f)
              .neonBorder(
                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
              )
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .testTag("theo_chat_bubble")
          ) {
            Column {
              Text(
                text = message.text,
                color = TheoTextPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp
              )

              // Action trigger chip if available
              message.actionTag?.let { actionKey ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TheoCyan.copy(alpha = 0.15f))
                    .border(1.dp, TheoCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { onExecuteAction(actionKey) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = "Execute Intent",
                    tint = TheoCyan,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = when {
                      actionKey.startsWith("music:", ignoreCase = true) -> run {
                        val payload = actionKey.substringAfter(':')
                        val provider = payload.substringBefore('|')
                        val query = payload.substringAfter('|', payload).trim()
                        val service = if (provider.equals("spotify", ignoreCase = true)) "Spotify" else "YouTube Music"
                        "Search ${query.take(20)} on $service"
                      }
                      actionKey.startsWith("order:", ignoreCase = true) ->
                        "Find order options (no purchase)"
                      actionKey.startsWith("setup:accessibility", ignoreCase = true) ->
                        "Enable Screen Automation"
                      actionKey.startsWith("screen:", ignoreCase = true) ->
                        "Run screen task"
                      actionKey.startsWith("app:", ignoreCase = true) ->
                        "Open ${actionKey.substringAfter(':').take(28)}"
                      else -> "Run ${actionKey.replaceFirstChar { it.uppercase() }}"
                    },
                    color = TheoCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              // Footer with copy button & timestamp
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = timeStr,
                  color = TheoTextMuted,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 9.sp
                )

                Row(
                  modifier = Modifier
                    .clickable {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      val clip = ClipData.newPlainText("Theo Response", message.text)
                      clipboard.setPrimaryClip(clip)
                      Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .padding(2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    tint = TheoTextMuted,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "COPY",
                    color = TheoTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
