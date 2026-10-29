package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fio.data.model.ChatMessage
import com.example.fio.data.model.EventType
import com.example.fio.data.model.HabitItem
import com.example.fio.data.model.ScheduleEvent
import com.example.fio.data.storage.FioRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("fio_storage_v2", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fio", appName)
  }

  @Test
  fun `verify bare initial state without mock data`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = FioRepository(context)

    // Verify completely bare initially (user request: no mock data until used)
    assertEquals(0, repository.events.value.size)
    assertEquals(0, repository.habits.value.size)
    assertEquals(0, repository.mealLogs.value.size)
    assertEquals(0, repository.sleepLogs.value.size)
    assertEquals(0, repository.courses.value.size)

    val initialScore = repository.calculateLifeScore()
    assertEquals(0, initialScore.overallScore)

    // When user logs an event and habit, timestamps are preserved
    val now = System.currentTimeMillis()
    repository.addEvent(
      ScheduleEvent(
        title = "Study Math",
        startTimeMillis = now,
        endTimeMillis = now + 3600000L,
        type = EventType.STUDY
      )
    )
    repository.addHabit(
      name = "Deep Work",
      description = "Focus daily"
    )

    assertEquals(1, repository.events.value.size)
    assertEquals(1, repository.habits.value.size)
    assertTrue("Event should have loggedAt timestamp", repository.events.value.first().loggedAtMillis > 0)
    assertTrue("Habit should have loggedAt timestamp", repository.habits.value.first().loggedAtMillis > 0)

    // Chat history functionality
    repository.addChatMessage(
      ChatMessage(
        sender = "You",
        text = "How do cells divide?",
        courseId = "bio_101"
      )
    )
    assertEquals(1, repository.chatHistory.value.size)
    assertEquals("How do cells divide?", repository.chatHistory.value.first().text)
  }

  @Test
  fun `verify profile name and full export import including books documents and progress`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = FioRepository(context)

    // Initially profile name is empty
    assertEquals("", repository.profileName.value)

    // User sets their profile name
    repository.setProfileName("Mike")
    assertEquals("Mike", repository.profileName.value)

    // Add document / book
    repository.addDocument(
      com.example.fio.data.model.StudyDocument(
        id = "doc_test_1",
        courseId = "cs_50",
        title = "Algorithms and Data Structures",
        author = "Knuth",
        format = "PDF",
        content = "Chapter 1: Sorting. Quicksort and Mergesort algorithms.",
        readingProgressPercent = 45,
        lastReadPosition = 1200
      )
    )

    // Add goal
    repository.addGoal(
      com.example.fio.data.model.GoalItem(
        title = "Read 5 Books",
        targetValue = 5.0,
        currentValue = 2.0,
        unit = "books"
      )
    )

    // Add habit with streak progress
    repository.addHabit("Night Reading", "Read 30 mins before sleep")
    val habit = repository.habits.value.first { it.name == "Night Reading" }
    repository.toggleHabitToday(habit.id)

    // Export complete data
    val exportedJson = repository.exportAllToJson()
    assertTrue(exportedJson.contains("\"profileName\": \"Mike\""))
    assertTrue(exportedJson.contains("Algorithms and Data Structures"))
    assertTrue(exportedJson.contains("Read 5 Books"))
    assertTrue(exportedJson.contains("Night Reading"))

    // Create a new clean repository and restore
    val newRepo = FioRepository(context)
    val importSuccess = newRepo.importFromJson(exportedJson)
    assertTrue("Import should succeed", importSuccess)

    // Verify all restored
    assertEquals("Mike", newRepo.profileName.value)
    assertEquals(1, newRepo.documents.value.size)
    assertEquals("Algorithms and Data Structures", newRepo.documents.value.first().title)
    assertEquals(45, newRepo.documents.value.first().readingProgressPercent)
    assertEquals(1, newRepo.goals.value.size)
    assertEquals("Read 5 Books", newRepo.goals.value.first().title)
    assertEquals(2.0, newRepo.goals.value.first().currentValue, 0.01)
  }
}
