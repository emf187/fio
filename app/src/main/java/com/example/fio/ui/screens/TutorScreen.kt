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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.fio.data.ai.AiImageHelper
import com.example.fio.data.ai.AiImageHelper.MATH_RULES
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.AiImageStrip
import com.example.fio.ui.components.AiImageViewerDialog
import com.example.fio.ui.components.AttachImageButton
import com.example.fio.ui.components.rememberCameraCapture
import com.example.fio.ui.components.saveImageToDevice
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.FioMarkdown
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun TutorScreen(
    repository: FioRepository,
    aiClient: FioAiClient
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val courses by repository.courses.collectAsState()
    val documents by repository.documents.collectAsState()
    val tutorSessions by repository.tutorSessions.collectAsState()
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

    // Active Practice State
    var activeQuestionPrompt by remember {
        mutableStateOf("Explain how the Operating System handles a Page Fault when all physical frames are dirty.")
    }
    var studentAnswerInput by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isGrading by remember { mutableStateOf(false) }
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

    // Photo picker launcher (Zero-permission Android Photo Picker compliant with Google Play)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedPhotoUri = uri }
    )

    // Camera capture for the exam-question photo (shared helper).
    val captureTutorPhoto = rememberCameraCapture { uri -> selectedPhotoUri = uri }

    // Collect all weak topics across sessions
    val allWeakTopics = courseSessions.flatMap { it.weakTopics }.distinct()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Course Selector ---
        item {
            com.example.fio.ui.components.FioScreenHeader(
                title = "Tutor",
                subtitle = "Practice, get graded, see what to revise."
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Subject", style = com.example.fio.ui.theme.FioType.label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                onLongClick = { courseEditTarget = c }
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

        // --- 2. Practice Question Generator ---
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

                    TextButton(
                        onClick = {
                            if (!isGrading && courseDocs.isNotEmpty()) {
                                isGrading = true
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
                                        is AiResult.Error -> tutorErrorMessage = res.message
                                    }
                                    isGrading = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("generate_question_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New AI Question", color = colors.primary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                FioMarkdown(text = activeQuestionPrompt, baseFontSize = 12.sp)
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
                        Text("Attached Question Photo: $uri", color = colors.textMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
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
                        com.example.fio.ui.components.FioSecondaryButton(
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
                    com.example.fio.ui.components.FioSecondaryButton(
                        text = if (isHintLoading) "Thinking…" else "Hint",
                        icon = Icons.Default.Lightbulb,
                        compact = true,
                        enabled = !isHintLoading,
                        onClick = {
                            if (!isHintLoading) {
                                isHintLoading = true
                                tutorErrorMessage = null
                                coroutineScope.launch {
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    val hintPrompt = buildString {
                                        append("Exam question: $activeQuestionPrompt\n")
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
                                        is AiResult.Error -> tutorErrorMessage = res.message
                                    }
                                    isHintLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("get_hint_button")
                    )

                    com.example.fio.ui.components.FioPrimaryButton(
                        text = if (isGrading) "Grading…" else "Check answer",
                        compact = true,
                        enabled = !isGrading,
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
Question: $activeQuestionPrompt
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
                                                    questionPrompt = activeQuestionPrompt,
                                                    studentAnswer = studentAnswerInput.ifBlank { "[Photo answer submitted]" },
                                                    aiGrade = json.optString("grade", "B"),
                                                    scorePercentage = json.optInt("score", 80),
                                                    feedback = json.optString("feedback", "Good effort."),
                                                    weakTopics = weakList,
                                                    recommendedRevision = json.optString("recommendedRevision", "Review memory hierarchy chapters."),
                                                    photoUri = storedImg?.first ?: selectedPhotoUri?.toString()
                                                )
                                                repository.addTutorSession(session)
                                                gradingResult = session
                                            } catch (e: Exception) {
                                                tutorErrorMessage = "Failed to parse grade: ${e.message}\n${res.data}"
                                            }
                                        }
                                        is AiResult.Error -> tutorErrorMessage = res.message
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

        // --- 3. Tutor Evaluation Result Card ---
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

        // --- 4. Weak Topics Accumulated Over Time (Section 3 requirement) ---
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

        // --- 5. Previous Practice Sessions History ---
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
                        onLongClick = { sessionPendingDelete = session }
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

    // Confirmation: delete the practice sessions listed under the current subject.
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
            title = { Text("Delete this chat session?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "The practice session from ${formatTimestamp(session.loggedAtMillis)}(graded ${session.aiGrade}) will be permanently deleted. This cannot be undone.",
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
