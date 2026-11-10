@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fio.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.ai.AiImageHelper
import com.example.fio.data.ai.AiImageHelper.MATH_RULES
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.ai.FriendlyAiError
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.AiImageStrip
import com.example.fio.ui.components.AiImageViewerDialog
import com.example.fio.ui.components.AttachImageButton
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioHaptics
import com.example.fio.ui.components.FioMarkdown
import com.example.fio.ui.components.FioPrimaryButton
import com.example.fio.ui.components.FioSecondaryButton
import com.example.fio.ui.components.rememberCameraCapture
import com.example.fio.ui.components.rememberFioHaptics
import com.example.fio.ui.components.saveImageToDevice
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TutorScreen(
    repository: FioRepository,
    aiClient: FioAiClient
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberFioHaptics()
    val clipboard = LocalClipboardManager.current

    val courses by repository.courses.collectAsState()
    val documents by repository.documents.collectAsState()
    val tutorSessions by repository.tutorSessions.collectAsState()
    val chatHistory by repository.chatHistory.collectAsState()
    val settings by repository.settings.collectAsState()

    val studyCourses = courses.filter { it.sectionType == SectionType.STUDY }
    var selectedCourseId by remember { mutableStateOf(studyCourses.firstOrNull()?.id ?: "") }

    LaunchedEffect(studyCourses) {
        if (selectedCourseId.isEmpty() && studyCourses.isNotEmpty()) {
            selectedCourseId = studyCourses.first().id
        }
    }

    val currentCourse = courses.find { it.id == selectedCourseId }
    val courseDocs = documents.filter { it.courseId == selectedCourseId }
    val courseSessions = tutorSessions.filter { it.courseId == selectedCourseId }

    // --- Tab state: Practice (graded questions) vs Tutor Chat (free conversation) ---
    var screenTab by remember { mutableStateOf(0) }

    // --- Practice state ---
    var activeQuestionPrompt by remember { mutableStateOf("") }
    var studentAnswerInput by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isGrading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var gradingResult by remember { mutableStateOf<TutorSession?>(null) }
    var tutorErrorMessage by remember { mutableStateOf<String?>(null) }

    // Related images from the AI (same capability as Wingman) + Socratic hint state
    var viewingImagePath by remember { mutableStateOf<String?>(null) }
    var noteSaveImagePath by remember { mutableStateOf<String?>(null) }
    var questionImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var hintCard by remember { mutableStateOf<String?>(null) }
    var hintImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isHintLoading by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var historyPendingClear by remember { mutableStateOf(false) }
    // Long-press target: the single practice session to delete.
    var sessionPendingDelete by remember { mutableStateOf<TutorSession?>(null) }
    // Long-press on a subject chip: edit its title and details.
    var courseEditTarget by remember { mutableStateOf<CourseSection?>(null) }

    // --- Tutor Chat state (mirrors the Study Companion chat) ---
    val tutorMessages = chatHistory.filter { it.courseId == selectedCourseId && it.channel == "TUTOR" }
    var chatTab by remember { mutableStateOf(0) } // 0 = live chat, 1 = history
    var tutorQuery by remember { mutableStateOf("") }
    var tutorChatPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isTutorChatLoading by remember { mutableStateOf(false) }
    var tutorChatJob by remember { mutableStateOf<Job?>(null) }
    var chatPendingClear by remember { mutableStateOf(false) }
    var chatSessionPendingDelete by remember { mutableStateOf<List<ChatMessage>?>(null) }

    // Photo picker launcher (Zero-permission Android Photo Picker compliant with Google Play)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedPhotoUri = uri }
    )
    val chatPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> tutorChatPhotoUri = uri }
    )

    // Camera capture for photos (shared helper).
    val captureTutorPhoto = rememberCameraCapture { uri -> selectedPhotoUri = uri }
    val captureChatPhoto = rememberCameraCapture { uri -> tutorChatPhotoUri = uri }

    // Collect all weak topics across sessions
    val allWeakTopics = courseSessions.flatMap { it.weakTopics }.distinct()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    /** Sends one tutor-chat turn (message + optional photo) and streams the reply into the thread. */
    fun launchTutorChat(prompt: String, photoUri: Uri?) {
        if (isTutorChatLoading || selectedCourseId.isBlank()) return
        isTutorChatLoading = true
        repository.addChatMessage(
            ChatMessage(sender = "You", text = prompt, courseId = selectedCourseId, channel = "TUTOR")
        )
        tutorQuery = ""
        tutorChatJob = coroutineScope.launch {
            try {
                val storedImg = if (photoUri != null) ImageStorage.processAndStoreImage(context, photoUri) else null
                val chunks = courseDocs.flatMap { it.chunks }
                // Per-chat memory: the same conversation the student sees on screen.
                val priorThread = tutorMessages
                    .filter { it.courseId == selectedCourseId && it.channel == "TUTOR" }
                    .takeLast(10)
                when (val res = aiClient.sendPrompt(
                    prompt = prompt,
                    systemInstruction = buildString {
                        append(
                            "You are the Tutor inside Fio's Tutor page for the subject \u201c${currentCourse?.name ?: "this course"}\u201d. " +
                                "Teach like a patient university tutor: build the answer step by step, check the student's reasoning, " +
                                "and end with one short question or practice idea that cements the concept. "
                        )
                        if (priorThread.isNotEmpty()) {
                            append("Recent conversation for context: ")
                            priorThread.forEach { m ->
                                append("${m.sender}: ${m.text.take(240)} ")
                            }
                        }
                        append(MATH_RULES)
                        if (settings.showAiImages) append(AiImageHelper.IMAGE_RULE)
                    },
                    settings = settings,
                    chunks = chunks,
                    imageBase64 = storedImg?.second,
                    tone = "Respond in clean markdown: short reasoning steps, bold key terms, plain English."
                )) {
                    is AiResult.Success -> {
                        val (clean, imageQuery) = AiImageHelper.extractImageQuery(res.data)
                        val images = if (settings.showAiImages) {
                            try {
                                val q = AiImageHelper.effectiveImageQuery(imageQuery, clean)
                                AiImageHelper.fetchRelatedImages(context, q, 2, clean)
                            } catch (e: Exception) { emptyList() }
                        } else emptyList()
                        repository.addChatMessage(
                            ChatMessage(
                                sender = "Tutor",
                                text = clean,
                                courseId = selectedCourseId,
                                channel = "TUTOR",
                                aiImagePaths = images.map { it.localPath }
                            )
                        )
                    }
                    is AiResult.Error -> {
                        repository.addChatMessage(
                            ChatMessage(
                                sender = "Tutor",
                                text = FriendlyAiError.message(res.message),
                                courseId = selectedCourseId,
                                channel = "TUTOR"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                repository.addChatMessage(
                    ChatMessage(
                        sender = "Tutor",
                        text = FriendlyAiError.message(e.message ?: "Unknown error"),
                        courseId = selectedCourseId,
                        channel = "TUTOR"
                    )
                )
            } finally {
                isTutorChatLoading = false
            }
        }
    }

    fun cancelTutorChat() {
        tutorChatJob?.cancel()
        tutorChatJob = null
        isTutorChatLoading = false
        if (selectedCourseId.isNotBlank()) {
            repository.addChatMessage(
                ChatMessage(sender = "Tutor", text = "Response stopped.", courseId = selectedCourseId, channel = "TUTOR")
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Course Selector (now fully scrollable) ---
        item {
            com.example.fio.ui.components.FioScreenHeader(
                title = "Tutor",
                subtitle = "Practice, get graded, chat through hard topics."
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Subject", style = com.example.fio.ui.theme.FioType.label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                studyCourses.forEach { c ->
                    val isSelected = c.id == selectedCourseId
                    Box(
                        modifier = Modifier
                            .widthIn(max = 190.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) colors.surfaceElevated else colors.surface)
                            .combinedClickable(
                                onClick = { selectedCourseId = c.id },
                                onLongClick = {
                                    haptics.longPress()
                                    courseEditTarget = c
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = c.name,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // --- 2. Practice / Tutor Chat switch ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surfaceElevated)
                    .testTag("tutor_tabs")
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    "Practice" to 0,
                    "Tutor Chat${if (tutorMessages.isNotEmpty()) " (${tutorMessages.size})" else ""}" to 1
                ).forEach { (tabLabel, tabIndex) ->
                    val isSelected = screenTab == tabIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) colors.textPrimary else Color.Transparent)
                            .clickable {
                                haptics.tick()
                                screenTab = tabIndex
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            color = if (isSelected) colors.background else colors.textMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (screenTab == 0) {
            // ============================================================
            // PRACTICE TAB — generate, answer, get graded
            // ============================================================
            item {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Quiz, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Current Question", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        FioSecondaryButton(
                            text = if (isGenerating) "Thinking…" else "New AI Question",
                            icon = Icons.Default.Refresh,
                            compact = true,
                            loading = isGenerating,
                            onClick = {
                                if (!isGenerating && courseDocs.isNotEmpty()) {
                                    isGenerating = true
                                    tutorErrorMessage = null
                                    coroutineScope.launch {
                                        val chunks = courseDocs.flatMap { it.chunks }
                                        when (val res = aiClient.sendPrompt(
                                            prompt = "Generate 1 challenging exam practice question testing deep conceptual understanding of the course material.",
                                            systemInstruction = "You are a university tutor creating ONE rigorous exam practice question at a time. " +
                                                "Output only the question text with no preamble and never include the answer or a worked solution. " +
                                                "Questions should invite the student to reason step by step independently. " +
                                                MATH_RULES +
                                                (if (settings.showAiImages) AiImageHelper.IMAGE_RULE else ""),
                                            settings = settings,
                                            chunks = chunks
                                        )) {
                                            is AiResult.Success -> {
                                                val (clean, imageQuery) = AiImageHelper.extractImageQuery(res.data)
                                                questionImages = if (settings.showAiImages) {
                                                    val q = AiImageHelper.effectiveImageQuery(imageQuery, clean)
                                                    runCatching {
                                                        AiImageHelper.fetchRelatedImages(context, q, 2, clean)
                                                    }.getOrDefault(emptyList<AiImageHelper.AiImage>())
                                                        .map { it.localPath }
                                                } else emptyList()
                                                activeQuestionPrompt = clean.trim()
                                                studentAnswerInput = ""
                                                gradingResult = null
                                                hintCard = null
                                                hintImages = emptyList()
                                            }
                                            is AiResult.Error -> tutorErrorMessage = FriendlyAiError.message(res.message)
                                        }
                                        isGenerating = false
                                    }
                                } else if (courseDocs.isEmpty()) {
                                    Toast.makeText(context, "Import documents for this subject first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("generate_question_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (activeQuestionPrompt.isNotBlank()) {
                        FioMarkdown(text = activeQuestionPrompt, baseFontSize = 12.sp)
                    } else {
                        Text(
                            "Tap \u201cNew AI Question\u201d for a fresh exam-style question, or attach a past paper photo below.",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (questionImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AiImageStrip(paths = questionImages, onOpen = { viewingImagePath = it })
                    }

                    // Optional attached photo from past questions / handwritten notes
                    selectedPhotoUri?.let { uri ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = colors.info, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Attached Question Photo", color = colors.textMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedPhotoUri = null }, modifier = Modifier.size(20.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = colors.textMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = studentAnswerInput,
                        onValueChange = { studentAnswerInput = it },
                        placeholder = { Text("Write your answer here in detail...", color = colors.textMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("tutor_answer_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Upload past paper photo: gallery or camera.
                        Box(modifier = Modifier.weight(1f)) {
                            FioSecondaryButton(
                                text = "Photo",
                                icon = Icons.Default.AddPhotoAlternate,
                                compact = true,
                                onClick = { showPhotoMenu = true },
                                modifier = Modifier.fillMaxWidth().testTag("upload_past_question_photo")
                            )
                            DropdownMenu(
                                expanded = showPhotoMenu,
                                onDismissRequest = { showPhotoMenu = false },
                                containerColor = colors.surfaceElevated
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Choose from gallery", color = colors.textPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showPhotoMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Take a photo", color = colors.textPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showPhotoMenu = false
                                        captureTutorPhoto()
                                    }
                                )
                            }
                        }

                        // Socratic hint: nudge without revealing the answer (same image/math capability as Wingman)
                        FioSecondaryButton(
                            text = "Hint",
                            icon = Icons.Default.Lightbulb,
                            compact = true,
                            loading = isHintLoading,
                            onClick = {
                                if (!isHintLoading) {
                                    isHintLoading = true
                                    tutorErrorMessage = null
                                    coroutineScope.launch {
                                        val chunks = courseDocs.flatMap { it.chunks }
                                        val hintPrompt = buildString {
                                            append("Exam question: ${activeQuestionPrompt.ifBlank { "(no typed question — use the attached photo or student answer)" }}\n")
                                            if (studentAnswerInput.isNotBlank()) {
                                                append("Student's attempt so far:\n${studentAnswerInput.trim()}\n")
                                            } else {
                                                append("The student has not written anything yet.\n")
                                            }
                                            append("Give ONE hint or guiding question that nudges the student toward the next step.")
                                        }
                                        when (val res = aiClient.sendPrompt(
                                            prompt = hintPrompt,
                                            systemInstruction = "You are a Socratic tutor for university students. NEVER reveal the answer, the worked solution or the final result. " +
                                                "Give one short hint (55 words max): a guiding question, or which exact concept/formula to start from, or the smallest missing step. " +
                                                MATH_RULES +
                                                (if (settings.showAiImages) AiImageHelper.IMAGE_RULE else ""),
                                            settings = settings,
                                            chunks = chunks
                                        )) {
                                            is AiResult.Success -> {
                                                val (clean, imageQuery) = AiImageHelper.extractImageQuery(res.data)
                                                hintCard = clean.trim()
                                                hintImages = if (settings.showAiImages) {
                                                    val q = AiImageHelper.effectiveImageQuery(imageQuery, clean)
                                                    runCatching {
                                                        AiImageHelper.fetchRelatedImages(context, q, 1, clean)
                                                    }.getOrDefault(emptyList<AiImageHelper.AiImage>())
                                                        .map { it.localPath }
                                                } else emptyList()
                                            }
                                            is AiResult.Error -> tutorErrorMessage = FriendlyAiError.message(res.message)
                                        }
                                        isHintLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("get_hint_button")
                        )

                        FioPrimaryButton(
                            text = "Check answer",
                            compact = true,
                            loading = isGrading,
                            onClick = {
                                if ((studentAnswerInput.isNotBlank() || selectedPhotoUri != null) && !isGrading) {
                                    isGrading = true
                                    tutorErrorMessage = null
                                    coroutineScope.launch {
                                        val storedImg = if (selectedPhotoUri != null) {
                                            ImageStorage.processAndStoreImage(context, selectedPhotoUri!!)
                                        } else null

                                        val chunks = courseDocs.flatMap { it.chunks }
                                        val evaluationPrompt = """
Question: ${activeQuestionPrompt.ifBlank { "No typed question — evaluate from the attached photo / written answer." }}
Student Answer: ${studentAnswerInput.ifBlank { "See attached photo for student's handwritten response/diagram." }}

Evaluate this answer. Return ONLY a valid JSON object without markdown fences, with these exact keys:
{
  "grade": "A" (or "A+", "B", "C", "Needs Work"),
  "score": 85,
  "feedback": "Detailed explanation of what was accurate, what was missing, and how to improve.",
  "weakTopics": ["Topic 1", "Topic 2"],
  "recommendedRevision": "Concrete revision plan and textbook chapters to review."
}
""".trimIndent()

                                        when (val res = aiClient.sendPrompt(
                                            prompt = evaluationPrompt,
                                            systemInstruction = "You are an objective, encouraging university grading tutor. You can evaluate text and attached student photos/diagrams. " +
                                                "Your feedback must teach: first confirm what was right, then give the next logical steps as HINTS and guiding questions that let the student finish the reasoning themselves - " +
                                                "do NOT dump the full corrected answer or final result. Never exceed 130 words. " +
                                                MATH_RULES +
                                                "Output ONLY the raw JSON object requested by the prompt, no markdown fences and no [IMG] markers.",
                                            settings = settings,
                                            chunks = chunks,
                                            imageBase64 = storedImg?.second
                                        )) {
                                            is AiResult.Success -> {
                                                try {
                                                    val clean = res.data.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                                    val json = JSONObject(clean)
                                                    val weakArray = json.optJSONArray("weakTopics")
                                                    val weakList = mutableListOf<String>()
                                                    if (weakArray != null) {
                                                        for (i in 0 until weakArray.length()) {
                                                            weakList.add(weakArray.getString(i))
                                                        }
                                                    }
                                                    val session = TutorSession(
                                                        courseId = selectedCourseId,
                                                        questionPrompt = activeQuestionPrompt.ifBlank { "[Photo answer submitted]" },
                                                        studentAnswer = studentAnswerInput.ifBlank { "[Photo answer submitted]" },
                                                        aiGrade = json.optString("grade", "B"),
                                                        scorePercentage = json.optInt("score", 80),
                                                        feedback = json.optString("feedback", "Good effort."),
                                                        weakTopics = weakList,
                                                        recommendedRevision = json.optString("recommendedRevision", "Review related chapters."),
                                                        photoUri = storedImg?.first ?: selectedPhotoUri?.toString()
                                                    )
                                                    repository.addTutorSession(session)
                                                    gradingResult = session
                                                } catch (e: Exception) {
                                                    tutorErrorMessage = "The grader gave an unreadable answer. Try again or rephrase your response."
                                                }
                                            }
                                            is AiResult.Error -> tutorErrorMessage = FriendlyAiError.message(res.message)
                                        }
                                        isGrading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.4f).testTag("submit_answer_button")
                        )
                    }

                    // Socratic hint card (teaches; never reveals the answer)
                    hintCard?.let { hint ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primaryContainer.copy(alpha = 0.35f))
                                .border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                                .testTag("tutor_hint_card")
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tutor Nudge", color = colors.primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                FioMarkdown(text = hint, baseFontSize = 12.sp)
                                if (hintImages.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    AiImageStrip(paths = hintImages, onOpen = { viewingImagePath = it })
                                }
                            }
                        }
                    }

                    tutorErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, color = colors.error, fontSize = 11.sp)
                    }
                }
            }

            // --- Tutor Evaluation Result Card ---
            gradingResult?.let { res ->
                item {
                    FioCard(
                        backgroundColor = colors.surfaceElevated,
                        borderColor = colors.primary,
                        padding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tutor Evaluation", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            FioBadge(text = "Grade: ${res.aiGrade} (${res.scorePercentage}%)", color = colors.success)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FioMarkdown(text = res.feedback, baseFontSize = 12.sp)

                        if (res.weakTopics.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Identified Weak Topics:", color = colors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                res.weakTopics.forEach { topic ->
                                    FioBadge(text = topic, color = colors.warning)
                                }
                            }
                        }

                        if (res.recommendedRevision.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Recommended Revision:", color = colors.info, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            FioMarkdown(text = res.recommendedRevision, baseFontSize = 11.sp)

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    repository.addEvent(
                                        ScheduleEvent(
                                            title = "Revision: ${res.weakTopics.firstOrNull() ?: "Weak Topics"}",
                                            description = res.recommendedRevision,
                                            startTimeMillis = now + (2 * 3600 * 1000L),
                                            endTimeMillis = now + (3 * 3600 * 1000L),
                                            type = EventType.STUDY,
                                            courseId = selectedCourseId
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("schedule_revision_button")
                            ) {
                                Icon(imageVector = Icons.Default.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Schedule Revision Session in Calendar", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // --- Weak Topics Accumulated Over Time (Section 3 requirement) ---
            item {
                Text("Tracked Weak Topics Over Time", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            if (allWeakTopics.isEmpty()) {
                item {
                    Text("No weak topics logged yet. Complete tutor questions to identify areas for improvement.", color = colors.textMuted, fontSize = 11.sp)
                }
            } else {
                item {
                    FioCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allWeakTopics.forEach { topic ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = topic, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    TextButton(
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            repository.addEvent(
                                                ScheduleEvent(
                                                    title = "Revision: $topic",
                                                    startTimeMillis = now + (3 * 3600 * 1000L),
                                                    endTimeMillis = now + (4 * 3600 * 1000L),
                                                    type = EventType.STUDY
                                                )
                                            )
                                        }
                                    ) {
                                        Text("+ Schedule", color = colors.primary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Previous Practice Sessions History ---
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Past Questions & Results (${courseSessions.size})",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (courseSessions.isNotEmpty()) {
                        TextButton(
                            onClick = { historyPendingClear = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", color = colors.error, fontSize = 11.sp)
                        }
                    }
                }
            }

            items(courseSessions, key = { it.id }) { session ->
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.longPress()
                                sessionPendingDelete = session
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = session.questionPrompt.take(50) + "...", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        FioBadge(text = session.aiGrade, color = colors.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Logged: ${formatTimestamp(session.loggedAtMillis)}", color = colors.textMuted, fontSize = 9.sp)
                }
            }
        } else {
            // ============================================================
            // TUTOR CHAT TAB — same chat experience as the Study Companion
            // ============================================================
            item {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(14.dp)
                ) {
                    // Card title row + clear-current-chat action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Tutor Chat · ${currentCourse?.name ?: "Choose a subject"}",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (tutorMessages.isNotEmpty()) {
                            TextButton(
                                onClick = { chatPendingClear = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", color = colors.error, fontSize = 11.sp)
                            }
                        }
                    }

                    if (currentCourse == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pick a subject chip above to start chatting.", color = colors.textMuted, fontSize = 12.sp)
                    } else {
                        // ChatGPT-style tabs inside the chat: live chat vs full history log.
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surfaceElevated)
                                .testTag("tutor_chat_tabs")
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                "Chat" to 0,
                                "History (${tutorMessages.size})" to 1
                            ).forEach { (tabLabel, tabIndex) ->
                                val isSelected = chatTab == tabIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isSelected) colors.textPrimary else Color.Transparent)
                                        .clickable {
                                            haptics.tick()
                                            chatTab = tabIndex
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabLabel,
                                        color = if (isSelected) colors.background else colors.textMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // One renderer shared by the Chat tab and expanded History sessions.
                        val tutorBubble: @Composable (ChatMessage) -> Unit = { msg ->
                            val isUser = msg.sender == "You"
                            if (isUser) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.88f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(colors.surfaceElevated)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = colors.textPrimary,
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeFormat.format(Date(msg.timestampMillis)),
                                            color = colors.textMuted,
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy message",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    haptics.tick()
                                                    clipboard.setText(AnnotatedString(msg.text))
                                                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        // Edit & resend: rewinds the chat to just before this question.
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit and resend",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    haptics.tick()
                                                    repository.deleteChatMessagesFrom(msg.id)
                                                    tutorQuery = msg.text
                                                    Toast.makeText(context, "Edit your message, then press send", Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FioMarkdown(
                                            text = msg.text,
                                            baseFontSize = 13.5.sp
                                        )
                                        if (msg.aiImagePaths.isNotEmpty()) {
                                            AiImageStrip(
                                                paths = msg.aiImagePaths,
                                                onOpen = { viewingImagePath = it }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeFormat.format(Date(msg.timestampMillis)),
                                            color = colors.textMuted,
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy answer",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    haptics.tick()
                                                    clipboard.setText(AnnotatedString(msg.text))
                                                    Toast.makeText(context, "Answer copied", Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                        if (isAiFailureMessage(msg.text)) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            // Retry a failed request with the same prompt.
                                            Text(
                                                text = "Retry",
                                                color = colors.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    val failedPrompt = tutorMessages.lastOrNull { it.sender == "You" }?.text
                                                    if (failedPrompt != null) {
                                                        repository.deleteChatMessagesFrom(msg.id)
                                                        launchTutorChat(failedPrompt, null)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (chatTab == 0) {
                            // --- Chat tab: the full conversation ---
                            val chatScroll = rememberScrollState()
                            LaunchedEffect(chatTab, tutorMessages.size, tutorMessages.lastOrNull()?.id) {
                                withFrameNanos { }
                                if (tutorMessages.isNotEmpty()) {
                                    runCatching { chatScroll.animateScrollTo(chatScroll.maxValue) }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.56f).dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(chatScroll)
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (tutorMessages.isEmpty()) {
                                        Text(
                                            "Ask your tutor anything about this subject — it remembers the conversation.",
                                            color = colors.textMuted,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                    tutorMessages.forEach { msg -> tutorBubble(msg) }
                                }
                                // Jump-to-latest pill (ChatGPT-style ↓).
                                if (tutorMessages.isNotEmpty() && chatScroll.canScrollForward) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(vertical = 6.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(colors.surfaceElevated)
                                            .border(1.dp, colors.border, CircleShape)
                                            .clickable { coroutineScope.launch { chatScroll.animateScrollTo(chatScroll.maxValue) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Scroll to latest",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // --- History: past chats grouped into dated sessions ---
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.56f).dp)
                            ) {
                                val historyScroll = rememberScrollState()
                                if (tutorMessages.isEmpty()) {
                                    Text("No saved messages yet.", color = colors.textMuted, fontSize = 11.sp)
                                } else {
                                    // A new session starts whenever there is a long pause
                                    // (> 30 min) between messages.
                                    val sessions = remember(tutorMessages) {
                                        val gapMs = 30L * 60L * 1000L
                                        val groups = mutableListOf<MutableList<ChatMessage>>()
                                        tutorMessages.forEach { msg ->
                                            val currentGroup = groups.lastOrNull()
                                            if (currentGroup == null || msg.timestampMillis - currentGroup.last().timestampMillis > gapMs) {
                                                groups.add(mutableListOf(msg))
                                            } else {
                                                currentGroup.add(msg)
                                            }
                                        }
                                        groups.map { it.toList() }.reversed()
                                    }
                                    var expandedSession by remember { mutableStateOf<String?>(null) }
                                    Column(
                                        modifier = Modifier.fillMaxWidth().verticalScroll(historyScroll),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sessions.forEach { session ->
                                            val sessionKey = session.first().id
                                            val isExpanded = expandedSession == sessionKey
                                            val firstTs = session.first().timestampMillis
                                            val startCal = Calendar.getInstance().apply { timeInMillis = firstTs }
                                            val nowCal = Calendar.getInstance()
                                            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                            val dateLabel = when {
                                                startCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                                                    startCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) ->
                                                    "Today · ${timeFormat.format(Date(firstTs))}"
                                                startCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                                                    startCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR) ->
                                                    "Yesterday · ${timeFormat.format(Date(firstTs))}"
                                                startCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) ->
                                                    dayFormat.format(Date(firstTs))
                                                else ->
                                                    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(firstTs))
                                            }
                                            val title = session.firstOrNull { it.sender == "You" }?.text
                                                ?.lineSequence()?.firstOrNull()?.trim()
                                                ?.let { if (it.length > 46) it.take(46).trimEnd() + "…" else it }
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Tutor chat"
                                            val lastText = session.last().text
                                            val description = when {
                                                isAiFailureMessage(lastText) ->
                                                    "AI was unreachable — tap to read the conversation."
                                                else -> lastText
                                                    .lineSequence()
                                                    .firstOrNull { it.isNotBlank() }
                                                    ?.trim()
                                                    ?.let { if (it.length > 90) it.take(90).trimEnd() + "…" else it }
                                                    ?: "No summary available."
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surface)
                                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                                    .combinedClickable(
                                                        onClick = { expandedSession = if (isExpanded) null else sessionKey },
                                                        onLongClick = {
                                                            haptics.longPress()
                                                            chatSessionPendingDelete = session
                                                        }
                                                    )
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(dateLabel, color = colors.textMuted, fontSize = 9.sp)
                                                    Text(
                                                        text = if (session.size == 1) "1 message" else "${session.size} messages",
                                                        color = colors.textMuted,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = title,
                                                    color = colors.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = description,
                                                    color = colors.textSecondary,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (isExpanded) "Tap to collapse" else "Tap to open conversation",
                                                    color = colors.primary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (isExpanded) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    session.forEach { tutorBubble(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Attached photo chip above the composer
                        tutorChatPhotoUri?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = colors.info, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Attached Image for Tutor", color = colors.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { tutorChatPhotoUri = null }, modifier = Modifier.size(20.dp)) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = colors.textMuted)
                                }
                            }
                        }

                        // ChatGPT-style composer, pinned under the conversation.
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(start = 2.dp, end = 4.dp)
                                    .heightIn(min = 46.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AttachImageButton(
                                    hasImage = tutorChatPhotoUri != null,
                                    onGallery = {
                                        chatPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onCamera = captureChatPhoto,
                                    modifier = Modifier.size(38.dp)
                                )
                                OutlinedTextField(
                                    value = tutorQuery,
                                    onValueChange = { tutorQuery = it },
                                    placeholder = {
                                        Text(
                                            "Message Tutor",
                                            color = colors.textMuted,
                                            fontSize = 13.sp
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("tutor_chat_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        cursorColor = colors.accent,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Circular action: spinner while thinking (tap to stop), send otherwise.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isTutorChatLoading -> colors.surfaceElevated
                                            (tutorQuery.isNotBlank() || tutorChatPhotoUri != null) -> colors.textPrimary
                                            else -> colors.surfaceElevated
                                        }
                                    )
                                    .testTag("tutor_chat_submit"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isTutorChatLoading) {
                                    // Spinner while the tutor thinks; tapping it stops the request.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { cancelTutorChat() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.primary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if ((tutorQuery.isNotBlank() || tutorChatPhotoUri != null)) {
                                                val queryText = tutorQuery.ifBlank {
                                                    "Please examine this attached image and explain it in the context of the course material."
                                                }
                                                val img = tutorChatPhotoUri
                                                tutorChatPhotoUri = null
                                                launchTutorChat(queryText, img)
                                            }
                                        }
                                    ) {
                                        val readyToSend = tutorQuery.isNotBlank() || tutorChatPhotoUri != null
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Send",
                                            tint = if (readyToSend) colors.background else colors.textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen viewer for related AI images
    viewingImagePath?.let { path ->
        AiImageViewerDialog(
            path = path,
            onDismiss = { viewingImagePath = null },
            onSaveToDevice = {
                val ok = saveImageToDevice(context, path)
                Toast.makeText(
                    context,
                    if (ok) "Saved to Pictures/Fio" else "Could not save image",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSaveToNote = {
                viewingImagePath = null
                noteSaveImagePath = path
            }
        )
    }

    // Long-press on a subject chip: edit its title and details.
    courseEditTarget?.let { course ->
        com.example.fio.ui.components.CourseDetailsDialog(
            course = course,
            onDismiss = { courseEditTarget = null },
            onSave = { name, details ->
                repository.updateCourseDetails(course.id, name, details)
                courseEditTarget = null
                Toast.makeText(context, "Subject updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (historyPendingClear) {
        AlertDialog(
            onDismissRequest = { historyPendingClear = false },
            containerColor = colors.surface,
            title = { Text("Clear practice history?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "All past questions and results under \u201c${currentCourse?.name ?: "this subject"}\u201d will be permanently deleted. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearTutorSessions(selectedCourseId)
                    activeQuestionPrompt = ""
                    studentAnswerInput = ""
                    gradingResult = null
                    hintCard = null
                    hintImages = emptyList()
                    questionImages = emptyList()
                    historyPendingClear = false
                }) {
                    Text("Clear", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { historyPendingClear = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Confirmation (long-press): delete one graded practice session.
    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Delete this practice session?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "The practice session from ${formatTimestamp(session.loggedAtMillis)} (graded ${session.aiGrade}) will be permanently deleted. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteTutorSession(session.id)
                    if (gradingResult?.id == session.id) gradingResult = null
                    sessionPendingDelete = null
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Confirmation: clear the whole Tutor Chat thread for this subject.
    if (chatPendingClear) {
        AlertDialog(
            onDismissRequest = { chatPendingClear = false },
            containerColor = colors.surface,
            title = { Text("Clear tutor chat?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "The entire tutor conversation under \u201c${currentCourse?.name ?: "this subject"}\u201d will be permanently deleted. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelTutorChat()
                    repository.clearChatThread(selectedCourseId, "TUTOR")
                    chatPendingClear = false
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatPendingClear = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Confirmation (long-press): delete one chat session from the History tab.
    chatSessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { chatSessionPendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Delete this chat session?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "The tutor conversation from ${formatTimestamp(session.first().timestampMillis)} will be permanently deleted. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteChatMessages(session.map { it.id })
                    chatSessionPendingDelete = null
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatSessionPendingDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Save a related image into an existing note.
    noteSaveImagePath?.let { imgPath ->
        val allNotes = repository.notes.value
        AlertDialog(
            onDismissRequest = { noteSaveImagePath = null },
            containerColor = colors.surface,
            title = { Text("Save image to note", color = colors.textPrimary) },
            text = {
                if (allNotes.isEmpty()) {
                    Text(
                        "No notes yet. Create one in Notes & Links first.",
                        color = colors.textSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        allNotes.take(30).forEach { note ->
                            TextButton(
                                onClick = {
                                    repository.saveNote(
                                        note.copy(
                                            content = note.content + "\n\n![Image Attachment]($imgPath)",
                                            imageUris = note.imageUris + imgPath,
                                            updatedAtMillis = System.currentTimeMillis()
                                        )
                                    )
                                    Toast.makeText(
                                        context,
                                        "Saved to \u201c${note.title.ifBlank { "Untitled note" }}\u201d",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    noteSaveImagePath = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = note.title.ifBlank { "Untitled note" },
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Save", color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { noteSaveImagePath = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}
