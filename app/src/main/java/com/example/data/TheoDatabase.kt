package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [MemoryEntity::class, MessageEntity::class, ActivityEntity::class],
  version = 1,
  exportSchema = false
)
abstract class TheoDatabase : RoomDatabase() {
  abstract fun theoDao(): TheoDao

  companion object {
    @Volatile
    private var INSTANCE: TheoDatabase? = null

    fun getDatabase(context: Context): TheoDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          TheoDatabase::class.java,
          "theo_assistant_database"
        )
          .addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
              super.onCreate(db)
              CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).theoDao()
                seedInitialData(dao)
              }
            }
          })
          .build()
        INSTANCE = instance
        instance
      }
    }

    private suspend fun seedInitialData(dao: TheoDao) {
      val now = System.currentTimeMillis()
      dao.insertMemory(
        MemoryEntity(
          id = "mem-1",
          category = "Identity",
          memoryKey = "User Codename",
          memoryValue = "Operator / Commander",
          createdAt = now - 86400000L * 3
        )
      )
      dao.insertMemory(
        MemoryEntity(
          id = "mem-2",
          category = "Preferences",
          memoryKey = "Response Architecture",
          memoryValue = "Concise, highly technical, futuristic tone, no filler words",
          createdAt = now - 86400000L * 2
        )
      )
      dao.insertMemory(
        MemoryEntity(
          id = "mem-3",
          category = "Architecture",
          memoryKey = "Termux Bridge Port",
          memoryValue = "http://127.0.0.1:8080/v1 (Local Qwen3-1.7B GGUF)",
          createdAt = now - 86400000L
        )
      )
      dao.insertMemory(
        MemoryEntity(
          id = "mem-4",
          category = "Hardware",
          memoryKey = "Target Environment",
          memoryValue = "Android Mobile native device, 6GB RAM profile, zero cloud bloat",
          createdAt = now - 43200000L
        )
      )

      dao.insertActivity(
        ActivityEntity(
          id = "act-init",
          title = "Neural Subsystem Booted",
          subtitle = "Theo Core initialized with local memory matrix and dual bridge",
          timestamp = now,
          type = "SYSTEM"
        )
      )
    }
  }
}
