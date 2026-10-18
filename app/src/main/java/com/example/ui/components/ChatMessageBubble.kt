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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
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
    enter = fadeIn() + slideInVertically { it / 3 },
    modifier = modifier.fillMaxWidth()
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 4.dp),
      contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
      val bubbleWidth = (maxWidth * 0.90f).coerceAtMost(420.dp)
      if (message.isUser) {
        val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        Column(
          horizontalAlignment = Alignment.End,
          modifier = Modifier.widthIn(max = bubbleWidth)
        ) {
          Box(
            modifier = Modifier
              .clip(shape)
              .liquidGlassSurface(
                shape = shape,
                tint = Color(0x3D616161),
                gloss = 0.09f,
                refraction = 5.dp,
                blurRadius = 4.dp
              )
              .border(0.7.dp, Color.White.copy(alpha = 0.10f), shape)
              .padding(horizontal = 15.dp, vertical = 11.dp)
              .testTag("user_chat_bubble")
          ) {
            Text(
              text = message.text,
              color = TheoTextPrimary,
              fontSize = 14.sp,
              lineHeight = 21.sp
            )
          }
          Row(
            modifier = Modifier.padding(top = 3.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("You", color = TheoTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(timeStr, color = TheoTextMuted, fontSize = 10.sp)
          }
        }
      } else {
        val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        Column(
          horizontalAlignment = Alignment.Start,
          modifier = Modifier.widthIn(max = bubbleWidth)
        ) {
          Row(
            modifier = Modifier.padding(bottom = 5.dp, start = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(TheoCyan)
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = message.modelBadge.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
              color = TheoTextSecondary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium
            )
            if (message.latencyMs > 0L) {
              Spacer(Modifier.width(7.dp))
              Text(
                text = message.latencyMs.toString() + " ms",
                color = TheoTextMuted,
                fontSize = 9.sp
              )
            }
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(shape)
              .liquidGlassSurface(
                shape = shape,
                tint = Color(0x25232323),
                gloss = 0.07f,
                refraction = 4.dp,
                blurRadius = 3.dp
              )
              .border(0.6.dp, Color.White.copy(alpha = 0.085f), shape)
              .padding(horizontal = 15.dp, vertical = 12.dp)
              .testTag("theo_chat_bubble")
          ) {
            Text(
              text = message.text,
              color = TheoTextPrimary,
              fontSize = 14.sp,
              lineHeight = 21.sp
            )

            message.actionTag?.let { actionKey ->
              val actionLabel = when {
                actionKey.startsWith("music:", ignoreCase = true) -> {
                  val payload = actionKey.substringAfter(':')
                  val provider = payload.substringBefore('|')
                  val query = payload.substringAfter('|', payload).trim()
                  val service = if (provider.equals("spotify", ignoreCase = true)) "Spotify" else "YouTube Music"
                  "Search " + query.take(20) + " on " + service
                }
                actionKey.startsWith("order:", ignoreCase = true) -> "Find order options (no purchase)"
                actionKey.startsWith("setup:accessibility", ignoreCase = true) -> "Enable Screen Automation"
                actionKey.startsWith("screen:", ignoreCase = true) -> "Run screen task"
                actionKey.startsWith("app:", ignoreCase = true) -> "Open " + actionKey.substringAfter(':').take(28)
                else -> "Run " + actionKey.replaceFirstChar { it.uppercase() }
              }
              Spacer(Modifier.height(10.dp))
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(17.dp))
                  .background(Color.White.copy(alpha = 0.045f))
                  .border(0.7.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(17.dp))
                  .clickable { onExecuteAction(actionKey) }
                  .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Launch,
                  contentDescription = "Execute action",
                  tint = TheoCyan,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = actionLabel,
                  color = TheoTextPrimary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }

            Spacer(Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(timeStr, color = TheoTextMuted, fontSize = 10.sp)
              Row(
                modifier = Modifier
                  .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Theo Response", message.text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                  }
                  .padding(vertical = 2.dp, horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "Copy text",
                  tint = TheoTextMuted,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Copy", color = TheoTextMuted, fontSize = 10.sp)
              }
            }
          }
        }
      }
    }
  }
}
