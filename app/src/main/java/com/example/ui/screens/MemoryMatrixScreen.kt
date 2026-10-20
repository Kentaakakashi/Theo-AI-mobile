package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MemoryEntity
import com.example.ui.components.liquidGlassSurface
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoErrorRed
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoSurfaceBorderSubtle
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceDeep
import com.example.ui.theme.TheoSurfaceGlass
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.ui.theme.TheoViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryMatrixScreen(
  memories: List<MemoryEntity>,
  searchQuery: String,
  selectedCategory: String,
  onSearchChange: (String) -> Unit,
  onCategorySelect: (String) -> Unit,
  onAddMemory: (String, String, String) -> Unit,
  onDeleteMemory: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var newCategory by remember { mutableStateOf("Personal") }
  var newKey by remember { mutableStateOf("") }
  var newValue by remember { mutableStateOf("") }

  val categories = listOf("All", "Identity", "Preferences", "Architecture", "Hardware", "Personal", "Captured")

  // Filter memories
  val filteredMemories = memories.filter { item ->
    val matchesCategory = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
    val matchesSearch = searchQuery.isBlank() ||
      item.memoryKey.contains(searchQuery, ignoreCase = true) ||
      item.memoryValue.contains(searchQuery, ignoreCase = true)
    matchesCategory && matchesSearch
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Transparent)
      .statusBarsPadding()

  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp)
    ) {
      // Screen Title & HUD Banner
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Memory",
            color = TheoTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
          )
          Text(
            text = "${memories.size} saved memories · available across chats",
            color = TheoCyan,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
          )
        }

        IconButton(
          onClick = { showAddDialog = true },
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .liquidGlassSurface(CircleShape, tint = Color(0x29BEBEBE), gloss = 0.13f)
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .testTag("add_memory_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add memory",
            tint = TheoCyan,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // Memory info card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp)
          .clip(RoundedCornerShape(20.dp))
          .liquidGlassSurface(RoundedCornerShape(20.dp), tint = TheoSurfaceGlass, gloss = 0.10f)
          .border(0.7.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(20.dp))
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = TheoViolet,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Saved on this phone and reused in future chats, whether Theo is using Gemini or local Qwen.",
            color = TheoTextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
          )
        }
      }

      // Search Bar
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .liquidGlassSurface(RoundedCornerShape(20.dp), tint = TheoSurfaceGlass, gloss = 0.10f)
          .border(0.7.dp, Color.White.copy(alpha = 0.095f), RoundedCornerShape(20.dp))
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search Memories",
          tint = TheoTextMuted,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier.weight(1f),
          textStyle = TextStyle(color = TheoTextPrimary, fontSize = 13.sp),
          cursorBrush = SolidColor(TheoCyan),
          decorationBox = { innerTextField ->
            if (searchQuery.isEmpty()) {
              Text("Search memories...", color = TheoTextMuted, fontSize = 13.sp)
            }
            innerTextField()
          }
        )
        if (searchQuery.isNotEmpty()) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear Search",
            tint = TheoTextMuted,
            modifier = Modifier
              .size(16.dp)
              .clickable { onSearchChange("") }
          )
        }
      }

      // Category Pill Filters
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.forEach { cat ->
          val isSelected = selectedCategory == cat
          val pillShape = RoundedCornerShape(50.dp)
          Box(
            modifier = Modifier
              .clip(pillShape)
              .liquidGlassSurface(
                pillShape,
                tint = if (isSelected) Color(0x3B7A7A7A) else Color(0x202A2A2A),
                gloss = 0.08f
              )
              .border(
                0.7.dp,
                if (isSelected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.065f),
                pillShape
              )
              .clickable { onCategorySelect(cat) }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              text = cat,
              color = if (isSelected) TheoCyan else TheoTextMuted,
              fontFamily = FontFamily.SansSerif,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }

      // Memory Items List
      Spacer(modifier = Modifier.height(12.dp))
      if (filteredMemories.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Bookmark,
              contentDescription = null,
              tint = TheoTextMuted,
              modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No memories match this view.",
              color = TheoTextMuted,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Say \"Remember that: [key] is [value]\" or tap + above.",
              color = TheoCyan,
              fontFamily = FontFamily.SansSerif,
              fontSize = 11.sp
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredMemories, key = { it.id }) { memory ->
            MemoryCardItem(
              memory = memory,
              onDelete = { onDeleteMemory(memory.id) }
            )
          }
          item {
            Spacer(modifier = Modifier.height(14.dp))
          }
        }
      }
    }

    // Dialog to add a memory manually
    if (showAddDialog) {
      AlertDialog(
        onDismissRequest = { showAddDialog = false },
        containerColor = TheoSurfaceCard,
        title = {
          Text(
            text = "Add a memory",
            color = TheoTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = newKey,
              onValueChange = { newKey = it },
              label = { Text("Memory Key (e.g. Preferred Language)") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TheoCyan,
                unfocusedBorderColor = TheoSurfaceBorder
              ),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = newValue,
              onValueChange = { newValue = it },
              label = { Text("Value / Content") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TheoCyan,
                unfocusedBorderColor = TheoSurfaceBorder
              ),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = newCategory,
              onValueChange = { newCategory = it },
              label = { Text("Category (e.g. Identity, Preference)") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TheoCyan,
                unfocusedBorderColor = TheoSurfaceBorder
              ),
              modifier = Modifier.fillMaxWidth()
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (newKey.isNotBlank() && newValue.isNotBlank()) {
                onAddMemory(newCategory, newKey, newValue)
                newKey = ""
                newValue = ""
                showAddDialog = false
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TheoCyan)
          ) {
            Text("Save memory", color = TheoDarkBackground, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showAddDialog = false }) {
            Text("Cancel", color = TheoTextMuted)
          }
        }
      )
    }
  }
}

@Composable
private fun MemoryCardItem(
  memory: MemoryEntity,
  onDelete: () -> Unit
) {
  val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(memory.createdAt))

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Color.Transparent)
                .liquidGlassSurface(RoundedCornerShape(20.dp), tint = TheoSurfaceGlass, gloss = 0.12f)
      .border(0.7.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(20.dp))
      .padding(14.dp)
      .testTag("memory_item_card")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Category chip
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TheoViolet.copy(alpha = 0.12f))
            .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = memory.category.uppercase(),
            color = TheoViolet,
            fontFamily = FontFamily.SansSerif,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete Memory",
            tint = TheoTextMuted,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = memory.memoryKey,
        color = TheoCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = memory.memoryValue,
        color = TheoTextPrimary,
        fontSize = 13.sp,
        lineHeight = 18.sp
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "ENCODED: $dateStr",
        color = TheoTextMuted,
        fontFamily = FontFamily.SansSerif,
        fontSize = 8.sp
      )
    }
  }
}
