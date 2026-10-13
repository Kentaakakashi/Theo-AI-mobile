package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TheoDao {
  // Memories
  @Query("SELECT * FROM memories ORDER BY createdAt DESC")
  fun getAllMemories(): Flow<List<MemoryEntity>>

  @Query("SELECT * FROM memories WHERE category = :category ORDER BY createdAt DESC")
  fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMemory(memory: MemoryEntity)

  @Update
  suspend fun updateMemory(memory: MemoryEntity)

  @Delete
  suspend fun deleteMemory(memory: MemoryEntity)

  @Query("DELETE FROM memories WHERE id = :id")
  suspend fun deleteMemoryById(id: String)

  // Messages
  @Query("SELECT * FROM messages ORDER BY timestamp ASC")
  fun getAllMessages(): Flow<List<MessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageEntity)

  @Query("DELETE FROM messages")
  suspend fun clearMessages()

  // Activities
  @Query("SELECT * FROM activities ORDER BY timestamp DESC LIMIT 50")
  fun getAllActivities(): Flow<List<ActivityEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertActivity(activity: ActivityEntity)

  @Query("DELETE FROM activities")
  suspend fun clearActivities()
}
