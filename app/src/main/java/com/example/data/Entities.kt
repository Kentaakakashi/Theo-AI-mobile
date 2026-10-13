package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
  @PrimaryKey val id: String,
  val category: String,
  val memoryKey: String,
  val memoryValue: String,
  val createdAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
  @PrimaryKey val id: String,
  val text: String,
  val isUser: Boolean,
  val timestamp: Long,
  val modelBadge: String,
  val latencyMs: Long,
  val actionTag: String?
)

@Entity(tableName = "activities")
data class ActivityEntity(
  @PrimaryKey val id: String,
  val title: String,
  val subtitle: String,
  val timestamp: Long,
  val type: String
)
