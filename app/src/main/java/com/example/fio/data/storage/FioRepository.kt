package com.example.fio.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.fio.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class FioRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fio_storage", Context.MODE_PRIVATE)

    private val _events = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val events: StateFlow<List<ScheduleEvent>> = _events.asStateFlow()

    private val _habits = MutableStateFlow<List<HabitItem>>(emptyList())
    val habits: StateFlow<List<HabitItem>> = _habits.asStateFlow()

    private val _goals = MutableStateFlow<List<GoalItem>>(emptyList())
    val goals: StateFlow<List<GoalItem>> = _goals.asStateFlow()

    private val _sleepLogs = MutableStateFlow<List<SleepEntry>>(emptyList())
    val sleepLogs: StateFlow<List<SleepEntry>> = _sleepLogs.asStateFlow()

    private val _mealLogs = MutableStateFlow<List<MealEntry>>(emptyList())
    val mealLogs: StateFlow<List<MealEntry>> = _mealLogs.asStateFlow()

    private val _courses = MutableStateFlow<List<CourseSection>>(emptyList())
    val courses: StateFlow<List<CourseSection>> = _courses.asStateFlow()

    private val _documents = MutableStateFlow<List<StudyDocument>>(emptyList())
    val documents: StateFlow<List<StudyDocument>> = _documents.asStateFlow()

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()

    private val _tutorSessions = MutableStateFlow<List<TutorSession>>(emptyList())
    val tutorSessions: StateFlow<List<TutorSession>> = _tutorSessions.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    private val _sessionLogs = MutableStateFlow<List<FocusSessionLog>>(emptyList())
    val sessionLogs: StateFlow<List<FocusSessionLog>> = _sessionLogs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val hasData = prefs.getBoolean("has_initialized_data", false)
        if (!hasData) {
            seedInitialData()
            saveAll()
            prefs.edit().putBoolean("has_initialized_data", true).apply()
        } else {
            loadFromDisk()
        }
    }

    private fun seedInitialData() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis

        // 3 Initial Courses across Study, Leisure, Research
        val c1 = CourseSection(id = "course_sys", name = "Computer Systems & OS", sectionType = SectionType.STUDY, description = "Memory hierarchy, caches, processes, and kernel architecture.")
        val c2 = CourseSection(id = "course_phil", name = "Stoic Philosophy & Meditations", sectionType = SectionType.LEISURE, description = "Classical texts on focus, inner peace, and discipline.")
        val c3 = CourseSection(id = "course_cog", name = "Cognitive Science & Spaced Repetition", sectionType = SectionType.RESEARCH, description = "Neuroscience of active recall, memory consolidation, and sleep.")
        _courses.value = listOf(c1, c2, c3)

        // Seed Documents
        val doc1 = StudyDocument(
            id = "doc_os_cache",
            courseId = c1.id,
            title = "Virtual Memory and Page Replacement Algorithms",
            author = "Andrew S. Tanenbaum",
            format = "TXT",
            content = """# Virtual Memory & Paging

Virtual memory is a storage allocation scheme in which secondary memory can be addressed as though it were part of main memory. The addresses a program may use to reference memory are distinguished from the addresses the memory system uses to identify physical storage sites.

## Paging Principles
In paging, the virtual address space is divided into fixed-size units called pages. The corresponding units in physical memory are called page frames. Pages and page frames are always exactly the same size, typically 4 KB.

### Page Faults
When a process references a page not present in RAM, the Memory Management Unit (MMU) raises a trap to the operating system known as a page fault. The OS selects a victim page, writes it to disk if dirty, loads the desired page from disk, and resumes execution.

### Optimal Page Replacement (OPT)
The optimal algorithm replaces the page that will not be used for the longest period in the future. While impossible to implement practically (as it requires future knowledge), it serves as the theoretical benchmark.

### Least Recently Used (LRU)
LRU approximates OPT by replacing the page that has not been referenced for the longest period. Implementation typically utilizes a doubly-linked list or hardware timestamp counter.
""".trimIndent(),
            chunks = listOf(
                DocumentChunk("chunk_1", "Virtual Memory Overview", "Virtual memory allows addressing secondary storage as main memory. Pages (virtual) map to frames (physical)."),
                DocumentChunk("chunk_2", "Page Faults", "MMU raises a page fault trap when a page is not loaded. OS handles eviction and loading from disk."),
                DocumentChunk("chunk_3", "Replacement Algorithms", "LRU replaces the oldest unused page. OPT is theoretical benchmark.")
            ),
            readingProgressPercent = 35
        )

        val doc2 = StudyDocument(
            id = "doc_meditations",
            courseId = c2.id,
            title = "Meditations: Book IV",
            author = "Marcus Aurelius",
            format = "EPUB",
            content = """# Meditations - Book IV

Men seek retreats for themselves, houses in the country, seashores, and mountains; and thou too art wont to desire such things very much. But this is altogether a mark of the most common sort of men, for it is in thy power whenever thou shalt choose to retire into thyself.

For nowhere either with more quiet or more freedom from trouble does a man retire than into his own soul, particularly when he has within him such thoughts that by looking into them he is immediately in perfect tranquility; and I affirm that tranquility is nothing else than the good ordering of the mind.

Constantly then give to thyself this retreat, and renew thyself; and let thy rules be short and fundamental, which as soon as they are thought of will serve to filter out all sorrow, and send thee back without any discontent to the things to which thou must return.
""".trimIndent(),
            chunks = listOf(
                DocumentChunk("chunk_m1", "The Inner Citadel", "Retreat within one's own soul rather than physical retreats. Tranquility is the good ordering of the mind.")
            ),
            readingProgressPercent = 60
        )
        _documents.value = listOf(doc1, doc2)

        // Seed Flashcards
        _flashcards.value = listOf(
            Flashcard(courseId = c1.id, front = "What is a Page Fault?", back = "An interrupt raised by the MMU when a process accesses a page not mapped in physical RAM.", timesReviewed = 4, correctCount = 4),
            Flashcard(courseId = c1.id, front = "Explain the difference between Paging and Segmentation.", back = "Paging uses fixed-sized blocks (frames). Segmentation uses variable-sized logical units (e.g., functions, data segments).", timesReviewed = 3, correctCount = 2),
            Flashcard(courseId = c1.id, front = "Why is the Optimal Page Replacement (OPT) algorithm impossible in real systems?", back = "It requires knowing future memory reference strings in advance.", timesReviewed = 2, correctCount = 2),
            Flashcard(courseId = c3.id, front = "What is Spaced Repetition?", back = "A learning technique where reviews are spaced at increasing intervals according to the forgetting curve.", timesReviewed = 5, correctCount = 5)
        )

        // Seed Tutor Past Questions and Session
        _tutorSessions.value = listOf(
            TutorSession(
                courseId = c1.id,
                questionPrompt = "A system has 4 page frames. Initially empty. Page references: 1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5. Calculate page faults under LRU.",
                studentAnswer = "Frames fill up with 1,2,3,4 (4 faults). Next 1 and 2 hit. When 5 arrives, 3 is the least recently used, so 5 replaces 3 (fault 5). Next 1 and 2 hit. Then 3 comes in, replacing 4 (fault 6). Total is 8 faults.",
                aiGrade = "A",
                scorePercentage = 95,
                feedback = "Excellent step-by-step trace of LRU queue replacement. Correct identification of least recently used frames and total fault count.",
                weakTopics = listOf("Page replacement corner cases"),
                recommendedRevision = "Practice FIFO Belady's Anomaly and Clock Algorithm variants."
            )
        )

        // Seed Today's Events (Calendar & Planner)
        _events.value = listOf(
            ScheduleEvent(
                title = "Virtual Memory & Paging Review",
                description = "Focus on LRU algorithm and dirty page writebacks",
                startTimeMillis = todayStart + (9 * 3600 * 1000L),
                endTimeMillis = todayStart + (10 * 3600 * 1000L) + (30 * 60 * 1000L),
                type = EventType.STUDY,
                courseId = c1.id,
                isCompleted = true
            ),
            ScheduleEvent(
                title = "Afternoon Rest & Mindful Nap",
                description = "Dedicated rest block - disconnect and reset",
                startTimeMillis = todayStart + (14 * 3600 * 1000L),
                endTimeMillis = todayStart + (14 * 3600 * 1000L) + (40 * 60 * 1000L),
                type = EventType.REST,
                isCompleted = true
            ),
            ScheduleEvent(
                title = "Evening Gym & Strength Session",
                description = "Compound movements: Deadlifts & Upper Body pull",
                startTimeMillis = todayStart + (17 * 3600 * 1000L),
                endTimeMillis = todayStart + (18 * 3600 * 1000L) + (15 * 60 * 1000L),
                type = EventType.GYM,
                isCompleted = false
            ),
            ScheduleEvent(
                title = "Marcus Aurelius Meditations Reading",
                description = "Book IV study session with annotations",
                startTimeMillis = todayStart + (20 * 3600 * 1000L),
                endTimeMillis = todayStart + (21 * 3600 * 1000L),
                type = EventType.STUDY,
                courseId = c2.id,
                isCompleted = false
            )
        )

        // Seed Habits
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        _habits.value = listOf(
            HabitItem(name = "Deep Work (2 Hours)", description = "Uninterrupted focus with lockdown mode", streak = 6, bestStreak = 14, completedDates = listOf(todayStr)),
            HabitItem(name = "Active Reading (30 min)", description = "Annotate and extract [[concepts]] into notes", streak = 4, bestStreak = 9, completedDates = listOf(todayStr)),
            HabitItem(name = "Daily Movement (Gym/Jog)", description = "At least 30 minutes cardio or resistance", streak = 11, bestStreak = 18, completedDates = emptyList()),
            HabitItem(name = "Night Wind-down (No screens 30m)", description = "Sleep hygiene preparation", streak = 5, bestStreak = 12, completedDates = emptyList())
        )

        // Seed Goals
        _goals.value = listOf(
            GoalItem(title = "Complete Operating Systems Module", category = "Study", targetValue = 100.0, currentValue = 72.0, unit = "%"),
            GoalItem(title = "Read 5 Philosophical Works", category = "Leisure", targetValue = 5.0, currentValue = 3.0, unit = "books"),
            GoalItem(title = "Maintain 8h Sleep Average", category = "Health", targetValue = 8.0, currentValue = 7.7, unit = "hrs/night")
        )

        // Seed Sleep logs
        val yesterday = todayStart - (24 * 3600 * 1000L)
        _sleepLogs.value = listOf(
            SleepEntry(
                type = SleepType.NIGHT,
                startTimeMillis = yesterday + (23 * 3600 * 1000L),
                endTimeMillis = todayStart + (7 * 3600 * 1000L) + (15 * 60 * 1000L),
                qualityRating = 5,
                loggedAtMillis = todayStart + (7 * 3600 * 1000L) + (20 * 60 * 1000L)
            ),
            SleepEntry(
                type = SleepType.NAP,
                startTimeMillis = todayStart + (14 * 3600 * 1000L),
                endTimeMillis = todayStart + (14 * 3600 * 1000L) + (35 * 60 * 1000L),
                qualityRating = 4,
                loggedAtMillis = todayStart + (14 * 3600 * 1000L) + (40 * 60 * 1000L)
            )
        )

        // Seed Meals
        _mealLogs.value = listOf(
            MealEntry(
                timestampMillis = todayStart + (8 * 3600 * 1000L) + (15 * 60 * 1000L),
                note = "Breakfast: Oats & Green tea",
                loggedAtMillis = todayStart + (8 * 3600 * 1000L) + (16 * 60 * 1000L)
            ),
            MealEntry(
                timestampMillis = todayStart + (13 * 3600 * 1000L),
                note = "Lunch: Rice bowl with avocado and eggs",
                loggedAtMillis = todayStart + (13 * 3600 * 1000L) + (5 * 60 * 1000L)
            )
        )

        // Seed Notes (Obsidian markdown with [[links]] and #tags)
        _notes.value = listOf(
            NoteItem(
                title = "Virtual Memory Concepts",
                content = """# Virtual Memory Concepts
Linked to course: [[Computer Systems & OS]]
Tags: #cs #operating-systems #memory

Virtual memory decouples the programmer's view of memory from physical RAM.
See also: [[Page Replacement Algorithms]] and [[Marcus Aurelius Meditations]].

### Key Advantages
- Larger address space than physical RAM.
- Protection through hardware segment/page flags.
- Efficient memory sharing between processes.

> "A process believes it owns the entire universe of memory."
""".trimIndent(),
                tags = listOf("cs", "operating-systems", "memory"),
                createdAtMillis = todayStart - (48 * 3600 * 1000L),
                updatedAtMillis = todayStart - (2 * 3600 * 1000L)
            ),
            NoteItem(
                title = "Page Replacement Algorithms",
                content = """# Page Replacement Algorithms
Related: [[Virtual Memory Concepts]]
Tags: #algorithms #caching

When a page fault occurs, an existing page frame must be chosen for replacement.

### Algorithms:
1. **FIFO**: Simplest, but suffers from Belady's Anomaly.
2. **LRU**: Least Recently Used. Stack/linked list tracking.
3. **Clock (Second Chance)**: Approximate LRU using reference bit.
""".trimIndent(),
                tags = listOf("algorithms", "caching"),
                createdAtMillis = todayStart - (24 * 3600 * 1000L),
                updatedAtMillis = todayStart - (1 * 3600 * 1000L)
            ),
            NoteItem(
                title = "Marcus Aurelius Meditations",
                content = """# Marcus Aurelius Meditations
Tags: #philosophy #stoicism #focus

Key takeaway from Book IV:
The inner retreat. Real peace does not depend on changing geographical location, but in the tranquil ordering of your mind.
Practice: When overwhelmed by deadlines, pause for 2 minutes and observe without judgment.
""".trimIndent(),
                tags = listOf("philosophy", "stoicism", "focus"),
                createdAtMillis = todayStart - (36 * 3600 * 1000L),
                updatedAtMillis = todayStart - (5 * 3600 * 1000L)
            )
        )

        // Seed Session Logs
        _sessionLogs.value = listOf(
            FocusSessionLog(
                title = "Morning Deep Work",
                plannedMinutes = 90,
                actualMinutes = 90,
                isLockdownEnabled = true,
                startingStrikes = 3,
                strikesUsed = 0,
                breaksTaken = 1,
                status = SessionStatus.COMPLETED,
                loggedAtMillis = todayStart + (10 * 3600 * 1000L) + (35 * 60 * 1000L)
            )
        )
    }

    // --- Life Score Engine (Section 12 of spec) ---
    fun calculateLifeScore(): LifeScoreBreakdown {
        val s = _settings.value
        val weights = s.categoryWeights

        // 1. Study Score (0-100)
        // Focus time vs goal (target 2h = 120m daily), tutor session success, strike ratio
        val totalFocusMinutes = _sessionLogs.value.filter { it.status == SessionStatus.COMPLETED }.sumOf { it.actualMinutes }
        val focusComponent = ((totalFocusMinutes / 120.0) * 80).coerceIn(20.0, 80.0)
        val tutorSuccess = if (_tutorSessions.value.isNotEmpty()) _tutorSessions.value.map { it.scorePercentage }.average() * 0.2 else 15.0
        val studyScore = (focusComponent + tutorSuccess).coerceIn(0.0, 100.0).roundToInt()

        // 2. Sleep Score (0-100)
        // Hours slept vs target (default 8h)
        val recentSleep = _sleepLogs.value.firstOrNull { it.type == SleepType.NIGHT }
        val sleepScore = if (recentSleep != null) {
            val ratio = (recentSleep.durationHours / s.sleepTargetHours).coerceAtMost(1.0)
            ((ratio * 80) + (recentSleep.qualityRating * 4)).coerceIn(10.0, 100.0).roundToInt()
        } else 75

        // 3. Meals Score (0-100)
        val todayMealCount = _mealLogs.value.size
        val mealScore = if (todayMealCount > 0) {
            val ratio = (todayMealCount.toDouble() / s.mealTargetCount).coerceAtMost(1.0)
            (ratio * 100).roundToInt()
        } else 50

        // 4. Habits Score (0-100)
        val totalHabits = _habits.value.size
        val completedHabits = _habits.value.count { it.completedDates.isNotEmpty() }
        val habitScore = if (totalHabits > 0) {
            ((completedHabits.toDouble() / totalHabits) * 100).roundToInt()
        } else 70

        // 5. Activity & Rest Score (0-100)
        val completedActivity = _events.value.count { (it.type == EventType.GYM || it.type == EventType.JOG || it.type == EventType.REST) && it.isCompleted }
        val scheduledActivity = _events.value.count { it.type == EventType.GYM || it.type == EventType.JOG || it.type == EventType.REST }
        val activityScore = if (scheduledActivity > 0) {
            ((completedActivity.toDouble() / scheduledActivity) * 100).coerceAtLeast(30.0).roundToInt()
        } else 80

        // 6. Goals Score (0-100)
        val goals = _goals.value
        val goalsScore = if (goals.isNotEmpty()) {
            val avg = goals.map { (it.currentValue / it.targetValue.coerceAtLeast(1.0)) * 100 }.average()
            avg.coerceIn(0.0, 100.0).roundToInt()
        } else 70

        // Weighted overall score
        var totalWeight = 0.0
        var weightedSum = 0.0

        if (studyScore > 0) { weightedSum += studyScore * weights.study; totalWeight += weights.study }
        if (sleepScore > 0) { weightedSum += sleepScore * weights.sleep; totalWeight += weights.sleep }
        if (mealScore > 0) { weightedSum += mealScore * weights.meals; totalWeight += weights.meals }
        if (habitScore > 0) { weightedSum += habitScore * weights.habits; totalWeight += weights.habits }
        if (activityScore > 0) { weightedSum += activityScore * weights.activity; totalWeight += weights.activity }
        if (goalsScore > 0) { weightedSum += goalsScore * weights.goals; totalWeight += weights.goals }

        val overall = if (totalWeight > 0) (weightedSum / totalWeight).roundToInt() else 75
        val previousWeek = 78
        val trend = overall - previousWeek

        // Determine weakest / strongest area
        val scores = listOf("Study" to studyScore, "Sleep" to sleepScore, "Meals" to mealScore, "Habits" to habitScore, "Activity & Rest" to activityScore, "Goals" to goalsScore)
        val weakest = scores.minByOrNull { it.second }
        val strongest = scores.maxByOrNull { it.second }

        val note = if (trend >= 0) {
            "Strongest area is ${strongest?.first} (${strongest?.second}%). Life Score is up by +$trend vs last week."
        } else {
            "${weakest?.first} was your weakest area this week (${weakest?.second}%). Consider scheduling an extra recovery block."
        }

        return LifeScoreBreakdown(
            overallScore = overall,
            previousWeekScore = previousWeek,
            trend = trend,
            studyScore = studyScore,
            sleepScore = sleepScore,
            mealsScore = mealScore,
            habitsScore = habitScore,
            activityScore = activityScore,
            goalsScore = goalsScore,
            note = note
        )
    }

    // --- Schedule & Calendar Operations ---
    fun addEvent(event: ScheduleEvent) {
        _events.value = _events.value + event
        saveAll()
    }

    fun updateEvent(event: ScheduleEvent) {
        val updated = event.copy(editedAtMillis = currentTimestamp())
        _events.value = _events.value.map { if (it.id == event.id) updated else it }
        saveAll()
    }

    fun toggleEventCompleted(id: String) {
        _events.value = _events.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted, editedAtMillis = currentTimestamp()) else it
        }
        saveAll()
    }

    fun removeEvent(id: String) {
        _events.value = _events.value.filterNot { it.id == id }
        saveAll()
    }

    // --- Habits Operations ---
    fun toggleHabitToday(id: String) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _habits.value = _habits.value.map { habit ->
            if (habit.id == id) {
                val isCompletedToday = habit.completedDates.contains(todayStr)
                val newDates = if (isCompletedToday) {
                    habit.completedDates - todayStr
                } else {
                    habit.completedDates + todayStr
                }
                val newStreak = if (isCompletedToday) (habit.streak - 1).coerceAtLeast(0) else habit.streak + 1
                habit.copy(
                    completedDates = newDates,
                    streak = newStreak,
                    bestStreak = maxOf(habit.bestStreak, newStreak),
                    lastCompletedAtMillis = if (!isCompletedToday) currentTimestamp() else habit.lastCompletedAtMillis
                )
            } else habit
        }
        saveAll()
    }

    fun addHabit(name: String, description: String = "") {
        val habit = HabitItem(name = name, description = description)
        _habits.value = _habits.value + habit
        saveAll()
    }

    // --- Goals Operations ---
    fun addGoal(goal: GoalItem) {
        _goals.value = _goals.value + goal
        saveAll()
    }

    fun updateGoalProgress(id: String, progress: Double) {
        _goals.value = _goals.value.map {
            if (it.id == id) it.copy(currentValue = progress, editedAtMillis = currentTimestamp()) else it
        }
        saveAll()
    }

    // --- Sleep Operations ---
    fun logSleep(entry: SleepEntry) {
        _sleepLogs.value = listOf(entry) + _sleepLogs.value
        saveAll()
    }

    fun quickSleepStart(): Long {
        prefs.edit().putLong("sleep_start_timestamp", currentTimestamp()).apply()
        return currentTimestamp()
    }

    fun quickSleepAwake(): SleepEntry? {
        val start = prefs.getLong("sleep_start_timestamp", 0L)
        if (start == 0L) return null
        val now = currentTimestamp()
        val entry = SleepEntry(
            type = SleepType.NIGHT,
            startTimeMillis = start,
            endTimeMillis = now,
            qualityRating = 4,
            loggedAtMillis = now
        )
        _sleepLogs.value = listOf(entry) + _sleepLogs.value
        prefs.edit().remove("sleep_start_timestamp").apply()
        saveAll()
        return entry
    }

    fun getPendingSleepStart(): Long? {
        val s = prefs.getLong("sleep_start_timestamp", 0L)
        return if (s > 0L) s else null
    }

    // --- Meals Operations ---
    fun logMeal(note: String = "Meal", customTimestamp: Long = currentTimestamp()): MealEntry {
        val now = currentTimestamp()
        val entry = MealEntry(
            timestampMillis = customTimestamp,
            note = note,
            loggedAtMillis = now
        )
        _mealLogs.value = listOf(entry) + _mealLogs.value
        saveAll()
        return entry
    }

    fun updateMealTime(id: String, newTimestamp: Long) {
        val now = currentTimestamp()
        _mealLogs.value = _mealLogs.value.map {
            if (it.id == id) it.copy(timestampMillis = newTimestamp, editedAtMillis = now) else it
        }
        saveAll()
    }

    fun deleteMeal(id: String) {
        _mealLogs.value = _mealLogs.value.filterNot { it.id == id }
        saveAll()
    }

    // --- Notes Operations ---
    fun saveNote(note: NoteItem) {
        val existing = _notes.value.find { it.id == note.id }
        if (existing != null) {
            _notes.value = _notes.value.map { if (it.id == note.id) note.copy(updatedAtMillis = currentTimestamp()) else it }
        } else {
            _notes.value = listOf(note) + _notes.value
        }
        saveAll()
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filterNot { it.id == id }
        saveAll()
    }

    // --- Courses & Documents ---
    fun addCourse(name: String, sectionType: SectionType, description: String = "") {
        val course = CourseSection(name = name, sectionType = sectionType, description = description)
        _courses.value = _courses.value + course
        saveAll()
    }

    fun addDocument(document: StudyDocument) {
        _documents.value = _documents.value + document
        saveAll()
    }

    fun updateReadingProgress(docId: String, percent: Int, position: Int) {
        _documents.value = _documents.value.map {
            if (it.id == docId) it.copy(readingProgressPercent = percent, lastReadPosition = position, lastAccessedMillis = currentTimestamp()) else it
        }
        saveAll()
    }

    // --- Flashcards ---
    fun addFlashcard(card: Flashcard) {
        _flashcards.value = _flashcards.value + card
        saveAll()
    }

    fun recordFlashcardReview(id: String, correct: Boolean) {
        _flashcards.value = _flashcards.value.map {
            if (it.id == id) {
                it.copy(
                    timesReviewed = it.timesReviewed + 1,
                    correctCount = if (correct) it.correctCount + 1 else it.correctCount,
                    lastReviewedMillis = currentTimestamp()
                )
            } else it
        }
        saveAll()
    }

    // --- Tutor ---
    fun addTutorSession(session: TutorSession) {
        _tutorSessions.value = listOf(session) + _tutorSessions.value
        saveAll()
    }

    // --- Focus Sessions ---
    fun logFocusSession(session: FocusSessionLog) {
        _sessionLogs.value = listOf(session) + _sessionLogs.value
        saveAll()
    }

    // --- Settings ---
    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        saveSettingsToPrefs(settings)
    }

    private fun saveSettingsToPrefs(s: AppSettings) {
        prefs.edit()
            .putString("ai_provider", s.aiProvider)
            .putString("ai_base_url", s.aiBaseUrl)
            .putString("ai_api_key", s.aiApiKey)
            .putString("ai_model_name", s.aiModelName)
            .putString("app_theme", s.theme)
            .putFloat("font_scale", s.fontScale)
            .putBoolean("reduce_motion", s.reduceMotion)
            .putInt("grace_period", s.gracePeriodSeconds)
            .putInt("break_freq", s.breakFrequencyMinutes)
            .putInt("break_dur", s.breakDurationMinutes)
            .putFloat("sleep_target", s.sleepTargetHours.toFloat())
            .putInt("meal_target", s.mealTargetCount)
            .apply()
    }

    // --- Persistence & Wi-Fi Export/Import ---
    fun exportAllToJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", currentTimestamp())

        // Export events
        val eventsArray = JSONArray()
        for (e in _events.value) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("title", e.title)
            obj.put("description", e.description)
            obj.put("startTimeMillis", e.startTimeMillis)
            obj.put("endTimeMillis", e.endTimeMillis)
            obj.put("type", e.type.name)
            obj.put("isCompleted", e.isCompleted)
            obj.put("loggedAtMillis", e.loggedAtMillis)
            obj.put("editedAtMillis", e.editedAtMillis ?: -1L)
            eventsArray.put(obj)
        }
        root.put("events", eventsArray)

        // Export notes
        val notesArray = JSONArray()
        for (n in _notes.value) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("content", n.content)
            obj.put("createdAtMillis", n.createdAtMillis)
            obj.put("updatedAtMillis", n.updatedAtMillis)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        // Export meals & sleep
        val mealsArray = JSONArray()
        for (m in _mealLogs.value) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("timestampMillis", m.timestampMillis)
            obj.put("note", m.note)
            obj.put("loggedAtMillis", m.loggedAtMillis)
            mealsArray.put(obj)
        }
        root.put("meals", mealsArray)

        return root.toString(2)
    }

    fun importFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            if (root.has("events")) {
                val array = root.getJSONArray("events")
                val importedEvents = mutableListOf<ScheduleEvent>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    importedEvents.add(
                        ScheduleEvent(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            startTimeMillis = obj.getLong("startTimeMillis"),
                            endTimeMillis = obj.getLong("endTimeMillis"),
                            type = try { EventType.valueOf(obj.getString("type")) } catch (e: Exception) { EventType.STUDY },
                            isCompleted = obj.optBoolean("isCompleted", false),
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp())
                        )
                    )
                }
                _events.value = importedEvents
            }
            saveAll()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveAll() {
        // Lightweight local persistence via SharedPreferences / JSON files
        val json = exportAllToJson()
        prefs.edit().putString("backup_json", json).apply()
    }

    private fun loadFromDisk() {
        val json = prefs.getString("backup_json", null)
        if (!json.isNullOrBlank()) {
            importFromJson(json)
        } else {
            seedInitialData()
        }
        // Load settings
        _settings.value = AppSettings(
            aiProvider = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter",
            aiBaseUrl = prefs.getString("ai_base_url", "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1",
            aiApiKey = prefs.getString("ai_api_key", "") ?: "",
            aiModelName = prefs.getString("ai_model_name", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001",
            theme = prefs.getString("app_theme", "OBSIDIAN_PURPLE") ?: "OBSIDIAN_PURPLE",
            fontScale = prefs.getFloat("font_scale", 1.0f),
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            gracePeriodSeconds = prefs.getInt("grace_period", 15),
            breakFrequencyMinutes = prefs.getInt("break_freq", 50),
            breakDurationMinutes = prefs.getInt("break_dur", 10),
            sleepTargetHours = prefs.getFloat("sleep_target", 8.0f).toDouble(),
            mealTargetCount = prefs.getInt("meal_target", 3)
        )
    }
}
