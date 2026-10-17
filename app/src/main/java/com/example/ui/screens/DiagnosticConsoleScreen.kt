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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TheoDiagnosticLogger
import com.example.service.TheoLogEntry
import com.example.service.TheoLogLevel
import com.example.ui.components.hudScanlines
import com.example.ui.components.neonBorder
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoErrorRed
import com.example.ui.theme.TheoOnlineGreen
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary

@Composable
fun DiagnosticConsoleScreen(modifier: Modifier = Modifier) {
  val entries by TheoDiagnosticLogger.entries.collectAsState()
  val clipboard = LocalClipboardManager.current
  val errors = entries.count { it.level == TheoLogLevel.ERROR }
  val warnings = entries.count { it.level == TheoLogLevel.WARN }
  Column(
    modifier
      .fillMaxSize()
      .background(TheoDarkBackground)
      .hudScanlines()
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.weight(1f)) {
        Text("DIAGNOSTIC CONSOLE", color = TheoTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text("// STARTUP TRACE · ERRORS · ENGINE EVENTS", color = TheoCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
      }
      Icon(Icons.Default.Terminal, contentDescription = null, tint = TheoCyan, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
      StatChip("TOTAL", entries.size.toString(), TheoCyan, Modifier.weight(1f))
      StatChip("WARN", warnings.toString(), Color(0xFFFFB74D), Modifier.weight(1f))
      StatChip("ERROR", errors.toString(), if (errors > 0) TheoErrorRed else TheoOnlineGreen, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      Button(onClick = { TheoDiagnosticLogger.info("DIAGNOSTICS", "Manual refresh requested") }, colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceGlass), modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.Refresh, null, tint = TheoCyan, modifier = Modifier.size(15.dp)); Text("Refresh", color = TheoCyan, fontSize = 10.sp)
      }
      Button(onClick = { clipboard.setText(AnnotatedString(TheoDiagnosticLogger.exportText())) }, colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceGlass), modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.ContentCopy, null, tint = TheoCyan, modifier = Modifier.size(15.dp)); Text("Copy logs", color = TheoCyan, fontSize = 10.sp)
      }
      Button(onClick = { TheoDiagnosticLogger.clear() }, colors = ButtonDefaults.buttonColors(containerColor = TheoSurfaceGlass), modifier = Modifier.weight(1f).testTag("clear_diagnostics")) {
        Icon(Icons.Default.DeleteSweep, null, tint = TheoErrorRed, modifier = Modifier.size(15.dp)); Text("Clear", color = TheoErrorRed, fontSize = 10.sp)
      }
    }
    Spacer(Modifier.height(8.dp))
    Text("LOCAL CONSOLE • newest entries at bottom • max 500 entries", color = TheoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    Spacer(Modifier.height(6.dp))
    if (entries.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No events yet. App lifecycle and engine activity will appear here.", color = TheoTextMuted, fontSize = 12.sp) }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF080D13))
          .neonBorder(RoundedCornerShape(10.dp), strokeWidth = 1.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        items(entries, key = { it.id }) { entry -> ConsoleEntry(entry) }
        item { Spacer(Modifier.height(14.dp)) }
      }
    }
  }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Column(
    modifier
      .clip(RoundedCornerShape(8.dp))
      .background(TheoSurfaceGlass)
      .neonBorder(RoundedCornerShape(8.dp), strokeWidth = 1.dp)
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Text(label, color = TheoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ConsoleEntry(entry: TheoLogEntry) {
  val color = when (entry.level) {
    TheoLogLevel.DEBUG -> TheoTextMuted
    TheoLogLevel.INFO -> TheoCyan
    TheoLogLevel.WARN -> Color(0xFFFFB74D)
    TheoLogLevel.ERROR -> TheoErrorRed
  }
  Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(if (entry.level == TheoLogLevel.ERROR) TheoErrorRed.copy(alpha = 0.07f) else Color.Transparent).padding(horizontal = 5.dp, vertical = 4.dp)) {
    Text(entry.consoleLine(), color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
  }
}
