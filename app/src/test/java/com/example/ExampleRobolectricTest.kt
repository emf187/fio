package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fio.data.storage.FioRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fio", appName)
  }

  @Test
  fun `verify repository initial data and life score calculation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = FioRepository(context)
    val score = repository.calculateLifeScore()

    assertTrue("Life score should be between 0 and 100", score.overallScore in 0..100)
    assertTrue("Study score should be valid", score.studyScore in 0..100)
    assertTrue("Habits should be pre-seeded", repository.habits.value.isNotEmpty())
    assertTrue("Events should have loggedAt timestamps", repository.events.value.all { it.loggedAtMillis > 0 })
  }
}
