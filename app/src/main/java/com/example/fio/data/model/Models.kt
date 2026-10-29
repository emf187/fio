package com.example.fio.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Core data models for Fio: personal life planner and AI study companion.
 * All models support timestamping for logged time vs edited time.
 */

fun currentTimestamp(): Long = System.currentTimeMillis()

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatTimeOnly(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatDateOnly(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

enum class EventType {
    STUDY,
    CLASS,
    GYM,
    JOG,
    REST,
    HABIT,
    OTHER
}

data class ScheduleEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val type: EventType = EventType.STUDY,
    val courseId: String? = null,
    val loggedAtMillis: Long = currentTimestamp(),
    val editedAtMillis: Long? = null,
    val isCompleted: Boolean = false
)

data class HabitItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val frequencyDays: Int = 1, // 1 = daily
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val completedDates: List<String> = emptyList(), // "YYYY-MM-DD"
    val loggedAtMillis: Long = currentTimestamp(),
    val lastCompletedAtMillis: Long? = null
)

data class GoalItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val category: String = "Study", // Study, Health, Personal
    val targetValue: Double = 100.0,
    val currentValue: Double = 0.0,
    val unit: String = "%",
    val deadlineMillis: Long? = null,
    val loggedAtMillis: Long = currentTimestamp(),
    val editedAtMillis: Long? = null
)

enum class SleepType {
    NIGHT,
    NAP
}

data class SleepEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SleepType = SleepType.NIGHT,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val qualityRating: Int = 4, // 1..5
    val loggedAtMillis: Long = currentTimestamp(),
    val editedAtMillis: Long? = null
) {
    val durationHours: Double
        get() = ((endTimeMillis - startTimeMillis) / (1000.0 * 60 * 60)).coerceAtLeast(0.1)
}

data class MealEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMillis: Long = currentTimestamp(), // when the meal occurred
    val note: String = "Meal",
    val loggedAtMillis: Long = currentTimestamp(), // when it was tapped/recorded
    val editedAtMillis: Long? = null
)

enum class SectionType {
    STUDY,
    LEISURE,
    RESEARCH
}

data class CourseSection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val sectionType: SectionType = SectionType.STUDY,
    val description: String = "",
    val createdAtMillis: Long = currentTimestamp()
)

data class DocumentChunk(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val text: String
)

data class StudyDocument(
    val id: String = java.util.UUID.randomUUID().toString(),
    val courseId: String,
    val title: String,
    val author: String = "",
    val format: String = "TXT", // TXT, EPUB, PDF, MD
    val content: String,
    val chunks: List<DocumentChunk> = emptyList(),
    val readingProgressPercent: Int = 0,
    val lastReadPosition: Int = 0,
    val highlights: List<String> = emptyList(),
    val loggedAtMillis: Long = currentTimestamp(),
    val lastAccessedMillis: Long = currentTimestamp()
)

data class Flashcard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val courseId: String,
    val front: String,
    val back: String,
    val timesReviewed: Int = 0,
    val correctCount: Int = 0,
    val lastReviewedMillis: Long? = null
)

data class QuizQuestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class TutorSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val courseId: String,
    val questionPrompt: String,
    val studentAnswer: String,
    val aiGrade: String = "B+", // A+, A, B, C, Needs Work
    val scorePercentage: Int = 85,
    val feedback: String,
    val weakTopics: List<String> = emptyList(),
    val recommendedRevision: String = "",
    val photoUri: String? = null,
    val loggedAtMillis: Long = currentTimestamp()
)

data class NoteItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String, // Obsidian Markdown with [[links]] and #tags
    val courseId: String? = null,
    val tags: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val createdAtMillis: Long = currentTimestamp(),
    val updatedAtMillis: Long = currentTimestamp()
)

enum class SessionStatus {
    COMPLETED,
    BROKEN,
    PAUSED,
    ACTIVE
}

data class FocusSessionLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Study Session",
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val isLockdownEnabled: Boolean = false,
    val startingStrikes: Int = 0,
    val strikesUsed: Int = 0,
    val breaksTaken: Int = 0,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val loggedAtMillis: Long = currentTimestamp()
)

data class AppThemeOption(
    val name: String,
    val id: String
)

data class CategoryWeights(
    val study: Double = 0.25,
    val sleep: Double = 0.20,
    val meals: Double = 0.15,
    val habits: Double = 0.15,
    val activity: Double = 0.15,
    val goals: Double = 0.10
)

data class AppSettings(
    val aiProvider: String = "OpenRouter",
    val aiBaseUrl: String = "https://openrouter.ai/api/v1",
    val aiApiKey: String = "",
    val aiModelName: String = "google/gemini-2.0-flash-001",
    val theme: String = "OBSIDIAN_PURPLE", // OBSIDIAN_PURPLE, OLED_BLACK, HIGH_CONTRAST, OBSIDIAN_LIGHT
    val fontScale: Float = 1.0f,
    val reduceMotion: Boolean = false,
    val gracePeriodSeconds: Int = 15,
    val breakFrequencyMinutes: Int = 50,
    val breakDurationMinutes: Int = 10,
    val sleepTargetHours: Double = 8.0,
    val mealTargetCount: Int = 3,
    val categoryWeights: CategoryWeights = CategoryWeights()
)

data class LifeScoreBreakdown(
    val overallScore: Int,
    val previousWeekScore: Int,
    val trend: Int, // difference
    val studyScore: Int,
    val sleepScore: Int,
    val mealsScore: Int,
    val habitsScore: Int,
    val activityScore: Int,
    val goalsScore: Int,
    val note: String
)

data class ProposedScheduleChange(
    val title: String,
    val action: String, // "ADD", "MOVE", "REMOVE"
    val reason: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val eventType: EventType = EventType.STUDY,
    val existingEventId: String? = null
)

data class AgentAction(
    val type: String, // "ADD_EVENT", "LOG_MEAL", "LOG_SLEEP", "TOGGLE_HABIT", "CREATE_NOTE", "ADD_GOAL"
    val summary: String,
    val payload: Map<String, String> = emptyMap()
)

data class AgentExecutionResult(
    val replyText: String,
    val actions: List<AgentAction> = emptyList(),
    val attachedLocalPhotoPath: String? = null
)
