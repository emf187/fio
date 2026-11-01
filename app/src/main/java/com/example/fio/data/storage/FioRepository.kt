package com.example.fio.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.fio.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class FioRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fio_storage_v2", Context.MODE_PRIVATE)

    /**
     * Document bodies (books, PDFs, docs) live in their own files instead of the
     * SharedPreferences blob. This keeps every save/load a few KB instead of many MB,
     * which is what made the app feel slow and janky before.
     */
    private val docsDir = File(context.filesDir, "fio_doc_content")
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fio-doc-io")
    }

    private fun contentFile(docId: String): File = File(docsDir, "$docId.txt")

    private fun writeContentFile(docId: String, content: String) {
        if (content.isBlank()) return
        try {
            if (!docsDir.exists()) docsDir.mkdirs()
            contentFile(docId).writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteContentFile(docId: String) {
        try {
            contentFile(docId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _galleryImages = MutableStateFlow<List<GalleryImage>>(emptyList())
    val galleryImages: StateFlow<List<GalleryImage>> = _galleryImages.asStateFlow()

    private val galleryDir = File(context.filesDir, "fio_gallery")

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _profileName.value = prefs.getString("profile_name", "") ?: ""
        val json = prefs.getString("backup_json", null)
        if (!json.isNullOrBlank()) {
            parseState(json)
            hydrateDocumentContents()
        }
        // Load settings
        val geminiBase = "https://generativelanguage.googleapis.com/v1beta/openai"
        val geminiModel = "gemini-3.8-flash"
        _settings.value = AppSettings(
            aiProvider = prefs.getString("ai_provider", "Google Gemini") ?: "Google Gemini",
            aiBaseUrl = prefs.getString("ai_base_url", geminiBase) ?: geminiBase,
            aiApiKey = prefs.getString("ai_api_key", "") ?: "",
            aiModelName = prefs.getString("ai_model_name", geminiModel) ?: geminiModel,
            theme = prefs.getString("app_theme", "OBSIDIAN_LIGHT") ?: "OBSIDIAN_LIGHT",
            accentColor = prefs.getString("accent_color", "BLUE") ?: "BLUE",
            fontScale = prefs.getFloat("font_scale", 1.0f),
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            gracePeriodSeconds = prefs.getInt("grace_period", 15),
            breakFrequencyMinutes = prefs.getInt("break_freq", 50),
            breakDurationMinutes = prefs.getInt("break_dur", 10),
            sleepTargetHours = prefs.getFloat("sleep_target", 8.0f).toDouble(),
            mealTargetCount = prefs.getInt("meal_target", 3)
        )

        // One-time migration to the Notion-style look: users who never picked a
        // theme themselves get the clean light palette (white page, gray blocks,
        // blue accent). Anyone who already chose a theme/accent keeps theirs.
        if (!prefs.getBoolean("theme_migrated_notion2", false)) {
            val storedTheme = prefs.getString("app_theme", null)
            val storedAccent = prefs.getString("accent_color", null)
            if (storedTheme == null && storedAccent == null) {
                prefs.edit()
                    .putString("app_theme", "OBSIDIAN_LIGHT")
                    .putString("accent_color", "BLUE")
                    .apply()
                _settings.value = _settings.value.copy(theme = "OBSIDIAN_LIGHT", accentColor = "BLUE")
            }
            prefs.edit().putBoolean("theme_migrated_notion2", true).apply()
        }

        // One-time migration to Google Gemini as the default AI provider:
        // users who were still sitting on stock OpenRouter defaults with no key
        // configured get pointed at Gemini (generous free tier, daily reset).
        // Anyone who set up a real OpenRouter key keeps it untouched.
        if (!prefs.getBoolean("provider_migrated_gemini", false)) {
            val storedProvider = prefs.getString("ai_provider", null)
            val hasKey = !prefs.getString("ai_api_key", "").isNullOrBlank()
            if (!hasKey && (storedProvider == null || storedProvider == "OpenRouter")) {
                prefs.edit()
                    .putString("ai_provider", "Google Gemini")
                    .putString("ai_base_url", geminiBase)
                    .putString("ai_model_name", geminiModel)
                    .apply()
                _settings.value = _settings.value.copy(
                    aiProvider = "Google Gemini", aiBaseUrl = geminiBase, aiModelName = geminiModel
                )
            }
            prefs.edit().putBoolean("provider_migrated_gemini", true).apply()
        }

        _galleryImages.value = loadGalleryImages()
    }

    /**
     * Fills document bodies from their side files at startup (and migrates any legacy
     * inline content out of prefs once). Raw file IO only - no JSON parsing - so cold
     * start stays fast even with a large library.
     */
    private fun hydrateDocumentContents() {
        val current = _documents.value
        if (current.isEmpty()) return
        var changed = false
        val updated = current.map { doc ->
            var content = doc.content
            var dirty = false
            val file = contentFile(doc.id)
            if (content.isBlank()) {
                if (file.exists()) {
                    content = runCatching { file.readText() }.getOrNull() ?: ""
                    dirty = true
                }
            } else if (!file.exists()) {
                // Legacy data stored the full text inside prefs: move it to a side file
                // so the prefs blob stays small from now on.
                writeContentFile(doc.id, content)
            }
            val chunks = if (doc.chunks.isEmpty() && content.isNotBlank()) {
                deriveDocumentChunks(doc.title, content)
            } else {
                doc.chunks
            }
            if (dirty || chunks !== doc.chunks) {
                changed = true
                doc.copy(content = content, chunks = chunks)
            } else {
                doc
            }
        }
        if (changed) _documents.value = updated
    }

    // --- Life Score Engine (Section 12 of spec) - Zero mock, purely user-driven ---
    fun calculateLifeScore(): LifeScoreBreakdown {
        val s = _settings.value
        val weights = s.categoryWeights

        val hasAnyData = _sessionLogs.value.isNotEmpty() ||
                _sleepLogs.value.isNotEmpty() ||
                _mealLogs.value.isNotEmpty() ||
                _habits.value.isNotEmpty() ||
                _events.value.isNotEmpty() ||
                _goals.value.isNotEmpty()

        if (!hasAnyData) {
            return LifeScoreBreakdown(
                overallScore = 0,
                previousWeekScore = 0,
                trend = 0,
                studyScore = 0,
                sleepScore = 0,
                mealsScore = 0,
                habitsScore = 0,
                activityScore = 0,
                goalsScore = 0,
                note = "No activity logged yet. Schedule events, complete focus sessions, log meals or sleep to calculate your score."
            )
        }

        // 1. Study Score (0-100)
        val completedSessions = _sessionLogs.value.filter { it.status == SessionStatus.COMPLETED }
        val studyScore = if (completedSessions.isNotEmpty()) {
            val totalMins = completedSessions.sumOf { it.actualMinutes }
            val focusComp = ((totalMins / 120.0) * 80).coerceIn(10.0, 80.0)
            val tutorBonus = if (_tutorSessions.value.isNotEmpty()) _tutorSessions.value.map { it.scorePercentage }.average() * 0.2 else 0.0
            (focusComp + tutorBonus).coerceIn(0.0, 100.0).roundToInt()
        } else 0

        // 2. Sleep Score (0-100)
        val recentSleep = _sleepLogs.value.firstOrNull { it.type == SleepType.NIGHT }
        val sleepScore = if (recentSleep != null) {
            val ratio = (recentSleep.durationHours / s.sleepTargetHours).coerceAtMost(1.0)
            ((ratio * 80) + (recentSleep.qualityRating * 4)).coerceIn(0.0, 100.0).roundToInt()
        } else 0

        // 3. Meals Score (0-100)
        val todayMealCount = _mealLogs.value.size
        val mealScore = if (todayMealCount > 0) {
            val ratio = (todayMealCount.toDouble() / s.mealTargetCount).coerceAtMost(1.0)
            (ratio * 100).roundToInt()
        } else 0

        // 4. Habits Score (0-100)
        val totalHabits = _habits.value.size
        val habitScore = if (totalHabits > 0) {
            val completedHabits = _habits.value.count { it.completedDates.isNotEmpty() }
            ((completedHabits.toDouble() / totalHabits) * 100).roundToInt()
        } else 0

        // 5. Activity & Rest Score (0-100)
        val scheduledActivity = _events.value.count { it.type == EventType.GYM || it.type == EventType.JOG || it.type == EventType.REST }
        val activityScore = if (scheduledActivity > 0) {
            val completedActivity = _events.value.count { (it.type == EventType.GYM || it.type == EventType.JOG || it.type == EventType.REST) && it.isCompleted }
            ((completedActivity.toDouble() / scheduledActivity) * 100).roundToInt()
        } else 0

        // 6. Goals Score (0-100)
        val goals = _goals.value
        val goalsScore = if (goals.isNotEmpty()) {
            val avg = goals.map { (it.currentValue / it.targetValue.coerceAtLeast(1.0)) * 100 }.average()
            avg.coerceIn(0.0, 100.0).roundToInt()
        } else 0

        // Weighted overall score skipping empty categories (Section 12 requirement)
        var totalWeight = 0.0
        var weightedSum = 0.0

        if (studyScore > 0) { weightedSum += studyScore * weights.study; totalWeight += weights.study }
        if (sleepScore > 0) { weightedSum += sleepScore * weights.sleep; totalWeight += weights.sleep }
        if (mealScore > 0) { weightedSum += mealScore * weights.meals; totalWeight += weights.meals }
        if (habitScore > 0) { weightedSum += habitScore * weights.habits; totalWeight += weights.habits }
        if (activityScore > 0) { weightedSum += activityScore * weights.activity; totalWeight += weights.activity }
        if (goalsScore > 0) { weightedSum += goalsScore * weights.goals; totalWeight += weights.goals }

        val overall = if (totalWeight > 0) (weightedSum / totalWeight).roundToInt() else 0
        val trend = 0

        val note = if (overall > 0) {
            "Current score is $overall% based on your logged actions today."
        } else {
            "Start logging your day to track your Life Score."
        }

        return LifeScoreBreakdown(
            overallScore = overall,
            previousWeekScore = 0,
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

    // --- Chat History (Wingman Study Sessions) ---
    fun addChatMessage(msg: ChatMessage) {
        _chatHistory.value = _chatHistory.value + msg
        saveAll()
    }

    fun clearChatHistory(courseId: String? = null) {
        _chatHistory.value = if (courseId != null) {
            _chatHistory.value.filterNot { it.courseId == courseId }
        } else emptyList()
        saveAll()
    }

    // --- Gallery (stored pictures) ---
    fun galleryFile(image: GalleryImage): File = File(galleryDir, image.fileName)

    fun galleryDirectory(): File = galleryDir

    fun addGalleryImages(images: List<GalleryImage>) {
        if (images.isEmpty()) return
        _galleryImages.value = images + _galleryImages.value
        saveGalleryImages()
    }

    fun removeGalleryImage(id: String) {
        val target = _galleryImages.value.firstOrNull { it.id == id } ?: return
        _galleryImages.value = _galleryImages.value.filterNot { it.id == id }
        saveGalleryImages()
        ioExecutor.execute { runCatching { File(galleryDir, target.fileName).delete() } }
    }

    /** Gives a stored photo a custom name (shown in the grid & viewer). */
    fun renameGalleryImage(id: String, name: String) {
        val renamed = name.trim().take(60)
        val updated = _galleryImages.value
            .firstOrNull { it.id == id }
            ?.copy(displayName = renamed) ?: return
        _galleryImages.value = _galleryImages.value.map { if (it.id == id) updated else it }
        saveGalleryImages()
    }

    private fun loadGalleryImages(): List<GalleryImage> {
        val raw = prefs.getString("gallery_images", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<GalleryImage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    GalleryImage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        fileName = obj.getString("fileName"),
                        displayName = obj.optString("displayName", ""),
                        mimeType = obj.optString("mimeType", "image/*"),
                        addedAtMillis = obj.optLong("addedAtMillis", currentTimestamp())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveGalleryImages() {
        val array = JSONArray()
        for (img in _galleryImages.value) {
            array.put(
                JSONObject().apply {
                    put("id", img.id)
                    put("fileName", img.fileName)
                    put("displayName", img.displayName)
                    put("mimeType", img.mimeType)
                    put("addedAtMillis", img.addedAtMillis)
                }
            )
        }
        prefs.edit().putString("gallery_images", array.toString()).apply()
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
    fun addCourse(name: String, sectionType: SectionType, description: String = ""): CourseSection {
        val course = CourseSection(name = name, sectionType = sectionType, description = description)
        _courses.value = _courses.value + course
        saveAll()
        return course
    }

    fun addDocument(document: StudyDocument) {
        _documents.value = _documents.value + document
        if (document.content.isNotBlank()) {
            val docId = document.id
            val body = document.content
            // Body file write happens off the main thread; the metadata snapshot below
            // is tiny and synchronous so the document shows up (and survives) immediately.
            ioExecutor.execute { writeContentFile(docId, body) }
        }
        saveAll()
    }

    fun removeDocument(docId: String) {
        _documents.value = _documents.value.filterNot { it.id == docId }
        ioExecutor.execute { deleteContentFile(docId) }
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
            .putString("accent_color", s.accentColor)
            .putFloat("font_scale", s.fontScale)
            .putBoolean("reduce_motion", s.reduceMotion)
            .putInt("grace_period", s.gracePeriodSeconds)
            .putInt("break_freq", s.breakFrequencyMinutes)
            .putInt("break_dur", s.breakDurationMinutes)
            .putFloat("sleep_target", s.sleepTargetHours.toFloat())
            .putInt("meal_target", s.mealTargetCount)
            .apply()
    }

    // --- Profile Name ---
    fun setProfileName(name: String) {
        _profileName.value = name.trim()
        prefs.edit().putString("profile_name", _profileName.value).apply()
        saveAll()
    }

    // --- Comprehensive Export / Import (All books, documents, progress, and records) ---

    /** Full, human-readable backup (includes document bodies). Used by Settings export. */
    fun exportAllToJson(): String = buildExportJson(pretty = true, includeDocumentContents = true)

    private fun buildExportJson(pretty: Boolean, includeDocumentContents: Boolean): String {
        val root = JSONObject()
        root.put("version", 4)
        root.put("exportedAt", currentTimestamp())
        root.put("profileName", _profileName.value)

        // 1. Export events
        val eventsArray = JSONArray()
        for (e in _events.value) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("title", e.title)
            obj.put("description", e.description)
            obj.put("startTimeMillis", e.startTimeMillis)
            obj.put("endTimeMillis", e.endTimeMillis)
            obj.put("type", e.type.name)
            obj.put("courseId", e.courseId ?: "")
            obj.put("isCompleted", e.isCompleted)
            obj.put("loggedAtMillis", e.loggedAtMillis)
            obj.put("editedAtMillis", e.editedAtMillis ?: -1L)
            eventsArray.put(obj)
        }
        root.put("events", eventsArray)

        // 2. Export habits & streak progress
        val habitsArray = JSONArray()
        for (h in _habits.value) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("name", h.name)
            obj.put("description", h.description)
            obj.put("frequencyDays", h.frequencyDays)
            obj.put("streak", h.streak)
            obj.put("bestStreak", h.bestStreak)
            val completedDatesArray = JSONArray()
            h.completedDates.forEach { completedDatesArray.put(it) }
            obj.put("completedDates", completedDatesArray)
            obj.put("loggedAtMillis", h.loggedAtMillis)
            obj.put("lastCompletedAtMillis", h.lastCompletedAtMillis ?: -1L)
            habitsArray.put(obj)
        }
        root.put("habits", habitsArray)

        // 3. Export goals & progress
        val goalsArray = JSONArray()
        for (g in _goals.value) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("title", g.title)
            obj.put("category", g.category)
            obj.put("targetValue", g.targetValue)
            obj.put("currentValue", g.currentValue)
            obj.put("unit", g.unit)
            obj.put("deadlineMillis", g.deadlineMillis ?: -1L)
            obj.put("loggedAtMillis", g.loggedAtMillis)
            obj.put("editedAtMillis", g.editedAtMillis ?: -1L)
            goalsArray.put(obj)
        }
        root.put("goals", goalsArray)

        // 4. Export courses
        val coursesArray = JSONArray()
        for (c in _courses.value) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("sectionType", c.sectionType.name)
            obj.put("description", c.description)
            obj.put("createdAtMillis", c.createdAtMillis)
            coursesArray.put(obj)
        }
        root.put("courses", coursesArray)

        // 5. Export books & documents data (content, reading progress, highlights)
        //    Document bodies are only embedded for user-facing backups; the regular
        //    in-app snapshot keeps them in side files so saves stay tiny.
        val docsArray = JSONArray()
        for (d in _documents.value) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("courseId", d.courseId)
            obj.put("title", d.title)
            obj.put("author", d.author)
            obj.put("format", d.format)
            if (includeDocumentContents) {
                obj.put("content", d.content)
            }
            obj.put("readingProgressPercent", d.readingProgressPercent)
            obj.put("lastReadPosition", d.lastReadPosition)
            obj.put("loggedAtMillis", d.loggedAtMillis)
            obj.put("lastAccessedMillis", d.lastAccessedMillis)

            val highlightsArray = JSONArray()
            d.highlights.forEach { highlightsArray.put(it) }
            obj.put("highlights", highlightsArray)

            docsArray.put(obj)
        }
        root.put("documents", docsArray)

        // 6. Export notes
        val notesArray = JSONArray()
        for (n in _notes.value) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("content", n.content)
            obj.put("courseId", n.courseId ?: "")
            val tagsArray = JSONArray()
            n.tags.forEach { tagsArray.put(it) }
            obj.put("tags", tagsArray)
            val imagesArray = JSONArray()
            n.imageUris.forEach { imagesArray.put(it) }
            obj.put("imageUris", imagesArray)
            obj.put("createdAtMillis", n.createdAtMillis)
            obj.put("updatedAtMillis", n.updatedAtMillis)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        // 7. Export sleep logs
        val sleepArray = JSONArray()
        for (s in _sleepLogs.value) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("type", s.type.name)
            obj.put("startTimeMillis", s.startTimeMillis)
            obj.put("endTimeMillis", s.endTimeMillis)
            obj.put("qualityRating", s.qualityRating)
            obj.put("loggedAtMillis", s.loggedAtMillis)
            obj.put("editedAtMillis", s.editedAtMillis ?: -1L)
            sleepArray.put(obj)
        }
        root.put("sleep", sleepArray)

        // 8. Export meals
        val mealsArray = JSONArray()
        for (m in _mealLogs.value) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("timestampMillis", m.timestampMillis)
            obj.put("note", m.note)
            obj.put("loggedAtMillis", m.loggedAtMillis)
            obj.put("editedAtMillis", m.editedAtMillis ?: -1L)
            mealsArray.put(obj)
        }
        root.put("meals", mealsArray)

        // 9. Export flashcards
        val flashcardsArray = JSONArray()
        for (f in _flashcards.value) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("courseId", f.courseId)
            obj.put("front", f.front)
            obj.put("back", f.back)
            obj.put("timesReviewed", f.timesReviewed)
            obj.put("correctCount", f.correctCount)
            obj.put("lastReviewedMillis", f.lastReviewedMillis ?: -1L)
            flashcardsArray.put(obj)
        }
        root.put("flashcards", flashcardsArray)

        // 10. Export tutor sessions
        val tutorArray = JSONArray()
        for (t in _tutorSessions.value) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("courseId", t.courseId)
            obj.put("questionPrompt", t.questionPrompt)
            obj.put("studentAnswer", t.studentAnswer)
            obj.put("aiGrade", t.aiGrade)
            obj.put("scorePercentage", t.scorePercentage)
            obj.put("feedback", t.feedback)
            val weakArray = JSONArray()
            t.weakTopics.forEach { weakArray.put(it) }
            obj.put("weakTopics", weakArray)
            obj.put("recommendedRevision", t.recommendedRevision)
            obj.put("loggedAtMillis", t.loggedAtMillis)
            tutorArray.put(obj)
        }
        root.put("tutorSessions", tutorArray)

        // 11. Export focus sessions & strikes
        val sessionLogsArray = JSONArray()
        for (sl in _sessionLogs.value) {
            val obj = JSONObject()
            obj.put("id", sl.id)
            obj.put("title", sl.title)
            obj.put("plannedMinutes", sl.plannedMinutes)
            obj.put("actualMinutes", sl.actualMinutes)
            obj.put("isLockdownEnabled", sl.isLockdownEnabled)
            obj.put("startingStrikes", sl.startingStrikes)
            obj.put("strikesUsed", sl.strikesUsed)
            obj.put("breaksTaken", sl.breaksTaken)
            obj.put("status", sl.status.name)
            obj.put("loggedAtMillis", sl.loggedAtMillis)
            sessionLogsArray.put(obj)
        }
        root.put("sessionLogs", sessionLogsArray)

        // 12. Export chat (Wingman study sessions)
        val chatArray = JSONArray()
        for (c in _chatHistory.value) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("sender", c.sender)
            obj.put("text", c.text)
            obj.put("courseId", c.courseId ?: "")
            obj.put("imagePath", c.imagePath ?: "")
            obj.put("timestampMillis", c.timestampMillis)
            chatArray.put(obj)
        }
        root.put("chat", chatArray)

        // 13. Export settings
        val s = _settings.value
        val settingsObj = JSONObject()
        settingsObj.put("aiProvider", s.aiProvider)
        settingsObj.put("aiBaseUrl", s.aiBaseUrl)
        settingsObj.put("aiApiKey", s.aiApiKey)
        settingsObj.put("aiModelName", s.aiModelName)
        settingsObj.put("theme", s.theme)
        settingsObj.put("fontScale", s.fontScale.toDouble())
        settingsObj.put("reduceMotion", s.reduceMotion)
        settingsObj.put("gracePeriodSeconds", s.gracePeriodSeconds)
        settingsObj.put("breakFrequencyMinutes", s.breakFrequencyMinutes)
        settingsObj.put("breakDurationMinutes", s.breakDurationMinutes)
        settingsObj.put("sleepTargetHours", s.sleepTargetHours)
        settingsObj.put("mealTargetCount", s.mealTargetCount)
        root.put("settings", settingsObj)

        return if (pretty) root.toString(2) else root.toString()
    }

    fun importFromJson(jsonString: String): Boolean {
        val success = parseState(jsonString)
        if (success) {
            // Persist every restored document body to its side file, then snapshot.
            _documents.value.forEach { doc ->
                if (doc.content.isNotBlank()) writeContentFile(doc.id, doc.content)
            }
            saveAll()
        }
        return success
    }

    private fun parseState(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)

            // Profile name
            if (root.has("profileName")) {
                val name = root.optString("profileName", "").trim()
                if (name.isNotEmpty()) {
                    _profileName.value = name
                    prefs.edit().putString("profile_name", name).apply()
                }
            }

            // Events
            if (root.has("events")) {
                val array = root.getJSONArray("events")
                val list = mutableListOf<ScheduleEvent>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ScheduleEvent(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            startTimeMillis = obj.getLong("startTimeMillis"),
                            endTimeMillis = obj.getLong("endTimeMillis"),
                            type = try { EventType.valueOf(obj.getString("type")) } catch (e: Exception) { EventType.STUDY },
                            courseId = obj.optString("courseId", "").ifEmpty { null },
                            isCompleted = obj.optBoolean("isCompleted", false),
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            editedAtMillis = if (obj.has("editedAtMillis") && obj.getLong("editedAtMillis") > 0) obj.getLong("editedAtMillis") else null
                        )
                    )
                }
                _events.value = list
            }

            // Habits & streak progress
            if (root.has("habits")) {
                val array = root.getJSONArray("habits")
                val list = mutableListOf<HabitItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val datesList = mutableListOf<String>()
                    if (obj.has("completedDates")) {
                        val dArr = obj.getJSONArray("completedDates")
                        for (dIdx in 0 until dArr.length()) {
                            datesList.add(dArr.getString(dIdx))
                        }
                    }
                    list.add(
                        HabitItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            description = obj.optString("description", ""),
                            frequencyDays = obj.optInt("frequencyDays", 1),
                            streak = obj.optInt("streak", 0),
                            bestStreak = obj.optInt("bestStreak", 0),
                            completedDates = datesList,
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            lastCompletedAtMillis = if (obj.has("lastCompletedAtMillis") && obj.getLong("lastCompletedAtMillis") > 0) obj.getLong("lastCompletedAtMillis") else null
                        )
                    )
                }
                _habits.value = list
            }

            // Goals
            if (root.has("goals")) {
                val array = root.getJSONArray("goals")
                val list = mutableListOf<GoalItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        GoalItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            category = obj.optString("category", "Study"),
                            targetValue = obj.optDouble("targetValue", 100.0),
                            currentValue = obj.optDouble("currentValue", 0.0),
                            unit = obj.optString("unit", "%"),
                            deadlineMillis = if (obj.has("deadlineMillis") && obj.getLong("deadlineMillis") > 0) obj.getLong("deadlineMillis") else null,
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            editedAtMillis = if (obj.has("editedAtMillis") && obj.getLong("editedAtMillis") > 0) obj.getLong("editedAtMillis") else null
                        )
                    )
                }
                _goals.value = list
            }

            // Courses
            if (root.has("courses")) {
                val array = root.getJSONArray("courses")
                val list = mutableListOf<CourseSection>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CourseSection(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            sectionType = try { SectionType.valueOf(obj.getString("sectionType")) } catch (e: Exception) { SectionType.STUDY },
                            description = obj.optString("description", ""),
                            createdAtMillis = obj.optLong("createdAtMillis", currentTimestamp())
                        )
                    )
                }
                _courses.value = list
            }

            // Books & Documents Data (with chunks, highlights, reading progress)
            if (root.has("documents")) {
                val array = root.getJSONArray("documents")
                val list = mutableListOf<StudyDocument>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val title = obj.getString("title")
                    // Content is optional in the snapshot: the regular in-app backup keeps
                    // bodies in side files (filled in afterwards by hydrateDocumentContents).
                    val content = obj.optString("content", "")

                    val chunksList = mutableListOf<DocumentChunk>()
                    if (obj.has("chunks")) {
                        val chkArr = obj.getJSONArray("chunks")
                        for (cIdx in 0 until chkArr.length()) {
                            val cObj = chkArr.getJSONObject(cIdx)
                            chunksList.add(
                                DocumentChunk(
                                    id = cObj.optString("id", UUID.randomUUID().toString()),
                                    title = cObj.optString("title", "Part ${cIdx + 1}"),
                                    text = cObj.getString("text")
                                )
                            )
                        }
                    }
                    if (chunksList.isEmpty() && content.isNotBlank()) {
                        chunksList.addAll(deriveDocumentChunks(title, content))
                    }

                    val highlightsList = mutableListOf<String>()
                    if (obj.has("highlights")) {
                        val hlArr = obj.getJSONArray("highlights")
                        for (hIdx in 0 until hlArr.length()) {
                            highlightsList.add(hlArr.getString(hIdx))
                        }
                    }

                    list.add(
                        StudyDocument(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            courseId = obj.getString("courseId"),
                            title = title,
                            author = obj.optString("author", ""),
                            format = obj.optString("format", "TXT"),
                            content = content,
                            chunks = chunksList,
                            readingProgressPercent = obj.optInt("readingProgressPercent", 0),
                            lastReadPosition = obj.optInt("lastReadPosition", 0),
                            highlights = highlightsList,
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            lastAccessedMillis = obj.optLong("lastAccessedMillis", currentTimestamp())
                        )
                    )
                }
                _documents.value = list
            }

            // Notes
            if (root.has("notes")) {
                val array = root.getJSONArray("notes")
                val list = mutableListOf<NoteItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tagsList = mutableListOf<String>()
                    if (obj.has("tags")) {
                        val tArr = obj.getJSONArray("tags")
                        for (tIdx in 0 until tArr.length()) tagsList.add(tArr.getString(tIdx))
                    }
                    val imgsList = mutableListOf<String>()
                    if (obj.has("imageUris")) {
                        val iArr = obj.getJSONArray("imageUris")
                        for (imIdx in 0 until iArr.length()) imgsList.add(iArr.getString(imIdx))
                    }
                    list.add(
                        NoteItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            content = obj.getString("content"),
                            courseId = obj.optString("courseId", "").ifEmpty { null },
                            tags = tagsList,
                            imageUris = imgsList,
                            createdAtMillis = obj.optLong("createdAtMillis", currentTimestamp()),
                            updatedAtMillis = obj.optLong("updatedAtMillis", currentTimestamp())
                        )
                    )
                }
                _notes.value = list
            }

            // Sleep logs
            if (root.has("sleep")) {
                val array = root.getJSONArray("sleep")
                val list = mutableListOf<SleepEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SleepEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            type = try { SleepType.valueOf(obj.getString("type")) } catch (e: Exception) { SleepType.NIGHT },
                            startTimeMillis = obj.getLong("startTimeMillis"),
                            endTimeMillis = obj.getLong("endTimeMillis"),
                            qualityRating = obj.optInt("qualityRating", 4),
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            editedAtMillis = if (obj.has("editedAtMillis") && obj.getLong("editedAtMillis") > 0) obj.getLong("editedAtMillis") else null
                        )
                    )
                }
                _sleepLogs.value = list
            }

            // Meals
            if (root.has("meals")) {
                val array = root.getJSONArray("meals")
                val list = mutableListOf<MealEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        MealEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestampMillis = obj.optLong("timestampMillis", currentTimestamp()),
                            note = obj.optString("note", "Meal"),
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp()),
                            editedAtMillis = if (obj.has("editedAtMillis") && obj.getLong("editedAtMillis") > 0) obj.getLong("editedAtMillis") else null
                        )
                    )
                }
                _mealLogs.value = list
            }

            // Flashcards
            if (root.has("flashcards")) {
                val array = root.getJSONArray("flashcards")
                val list = mutableListOf<Flashcard>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Flashcard(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            courseId = obj.getString("courseId"),
                            front = obj.getString("front"),
                            back = obj.getString("back"),
                            timesReviewed = obj.optInt("timesReviewed", 0),
                            correctCount = obj.optInt("correctCount", 0),
                            lastReviewedMillis = if (obj.has("lastReviewedMillis") && obj.getLong("lastReviewedMillis") > 0) obj.getLong("lastReviewedMillis") else null
                        )
                    )
                }
                _flashcards.value = list
            }

            // Tutor Sessions
            if (root.has("tutorSessions")) {
                val array = root.getJSONArray("tutorSessions")
                val list = mutableListOf<TutorSession>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val weakList = mutableListOf<String>()
                    if (obj.has("weakTopics")) {
                        val wArr = obj.getJSONArray("weakTopics")
                        for (wIdx in 0 until wArr.length()) weakList.add(wArr.getString(wIdx))
                    }
                    list.add(
                        TutorSession(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            courseId = obj.getString("courseId"),
                            questionPrompt = obj.getString("questionPrompt"),
                            studentAnswer = obj.getString("studentAnswer"),
                            aiGrade = obj.optString("aiGrade", "B+"),
                            scorePercentage = obj.optInt("scorePercentage", 85),
                            feedback = obj.getString("feedback"),
                            weakTopics = weakList,
                            recommendedRevision = obj.optString("recommendedRevision", ""),
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp())
                        )
                    )
                }
                _tutorSessions.value = list
            }

            // Session Logs (Focus & Strikes)
            if (root.has("sessionLogs")) {
                val array = root.getJSONArray("sessionLogs")
                val list = mutableListOf<FocusSessionLog>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        FocusSessionLog(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "Study Session"),
                            plannedMinutes = obj.optInt("plannedMinutes", 25),
                            actualMinutes = obj.optInt("actualMinutes", 25),
                            isLockdownEnabled = obj.optBoolean("isLockdownEnabled", false),
                            startingStrikes = obj.optInt("startingStrikes", 0),
                            strikesUsed = obj.optInt("strikesUsed", 0),
                            breaksTaken = obj.optInt("breaksTaken", 0),
                            status = try { SessionStatus.valueOf(obj.getString("status")) } catch (e: Exception) { SessionStatus.COMPLETED },
                            loggedAtMillis = obj.optLong("loggedAtMillis", currentTimestamp())
                        )
                    )
                }
                _sessionLogs.value = list
            }

            // Chat history
            if (root.has("chat")) {
                val array = root.getJSONArray("chat")
                val list = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            sender = obj.getString("sender"),
                            text = obj.getString("text"),
                            courseId = obj.optString("courseId", "").ifEmpty { null },
                            imagePath = obj.optString("imagePath", "").ifEmpty { null },
                            timestampMillis = obj.optLong("timestampMillis", currentTimestamp())
                        )
                    )
                }
                _chatHistory.value = list
            }

            // Settings
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                val updatedSettings = _settings.value.copy(
                    aiProvider = sObj.optString("aiProvider", _settings.value.aiProvider),
                    aiBaseUrl = sObj.optString("aiBaseUrl", _settings.value.aiBaseUrl),
                    aiApiKey = sObj.optString("aiApiKey", _settings.value.aiApiKey),
                    aiModelName = sObj.optString("aiModelName", _settings.value.aiModelName),
                    theme = sObj.optString("theme", _settings.value.theme),
                    fontScale = sObj.optDouble("fontScale", _settings.value.fontScale.toDouble()).toFloat(),
                    reduceMotion = sObj.optBoolean("reduceMotion", _settings.value.reduceMotion),
                    gracePeriodSeconds = sObj.optInt("gracePeriodSeconds", _settings.value.gracePeriodSeconds),
                    breakFrequencyMinutes = sObj.optInt("breakFrequencyMinutes", _settings.value.breakFrequencyMinutes),
                    breakDurationMinutes = sObj.optInt("breakDurationMinutes", _settings.value.breakDurationMinutes),
                    sleepTargetHours = sObj.optDouble("sleepTargetHours", _settings.value.sleepTargetHours),
                    mealTargetCount = sObj.optInt("mealTargetCount", _settings.value.mealTargetCount)
                )
                _settings.value = updatedSettings
                saveSettingsToPrefs(updatedSettings)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Snapshots app state. Document bodies live in side files, so this payload is a few
     * KB of metadata rather than the whole library - cheap enough to run synchronously
     * on every mutation without janking the UI.
     */
    private fun saveAll() {
        val json = buildExportJson(pretty = false, includeDocumentContents = false)
        prefs.edit().putString("backup_json", json).apply()
    }
}
