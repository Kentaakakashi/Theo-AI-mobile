package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MemoryEntity
import com.example.data.TheoDatabase
import com.example.model.AiModelType
import com.example.model.AssistantState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: TheoDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, TheoDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Theo", appName)
  }

  @Test
  fun `verify assistant model types and states`() {
    assertEquals("Gemini 2.5 Flash", AiModelType.GEMINI_FLASH.displayName)
    assertEquals("Qwen3-1.7B Local", AiModelType.LOCAL_QWEN.displayName)
    assertEquals("ONLINE — STANDBY", AssistantState.IDLE.label)
  }

  @Test
  fun `verify theo memory insertion and retrieval`() = runBlocking {
    val dao = db.theoDao()
    val testMemory = MemoryEntity(
      id = "test-mem-1",
      category = "Identity",
      memoryKey = "Commander",
      memoryValue = "Operator Prime",
      createdAt = 1000L
    )

    dao.insertMemory(testMemory)
    val memories = dao.getAllMemories().first()

    assertEquals(1, memories.size)
    assertEquals("Commander", memories[0].memoryKey)
    assertEquals("Operator Prime", memories[0].memoryValue)
  }
}
