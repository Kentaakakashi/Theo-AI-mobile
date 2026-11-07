package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TheoDao {
  // Memories
  @Query("SELECT * FROM memories ORDER BY createdAt DESC")
  fun getAllMemories(): Flow<List<MemoryEntity>>

  @Query("SELECT * FROM memories WHERE category = :category ORDER BY createdAt DESC")
  fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

  @Query("""
    SELECT * FROM memories
    WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))
      AND LOWER(TRIM(memoryKey)) = LOWER(TRIM(:memoryKey))
    ORDER BY createdAt DESC
    LIMIT 1
  """)
  suspend fun findMemoryByCategoryAndKey(category: String, memoryKey: String): MemoryEntity?

  @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
  suspend fun getMemoryById(id: String): MemoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMemory(memory: MemoryEntity)

  @Update
  suspend fun updateMemory(memory: MemoryEntity)

  /**
   * Save a new memory or correct the latest matching category/key pair.
   * Existing IDs and createdAt values are retained when a fact is corrected.
   */
  @Transaction
  suspend fun upsertMemory(memory: MemoryEntity): MemoryEntity {
    val existing = findMemoryByCategoryAndKey(memory.category, memory.memoryKey)
    if (existing == null) {
      insertMemory(memory)
      return memory
    }
    val corrected = existing.copy(memoryValue = memory.memoryValue)
    updateMemory(corrected)
    return corrected
  }

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
