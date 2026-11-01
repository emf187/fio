package com.example.fio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
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

    // Photo picker launcher (Zero-permission Android Photo Picker compliant with Google Play)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedPhotoUri = uri }
    )

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
            Text("Tutor Mode: Select Course", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                studyCourses.forEach { c ->
                    val isSelected = c.id == selectedCourseId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.primaryContainer else colors.surface)
                            .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(12.dp))
                            .clickable { selectedCourseId = c.id }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = c.name,
                            color = if (isSelected) colors.primary else colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
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
                                        systemInstruction = "You are an expert university professor creating rigorous exam questions.",
                                        settings = settings,
                                        chunks = chunks
                                    )) {
                                        is AiResult.Success -> {
                                            activeQuestionPrompt = res.data.trim()
                                            studentAnswerInput = ""
                                            gradingResult = null
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
                Text(
                    text = activeQuestionPrompt,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Upload past paper photo
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textSecondary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("upload_past_question_photo")
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Photo", color = colors.textSecondary, fontSize = 11.sp)
                    }

                    Button(
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
                                        systemInstruction = "You are an objective, encouraging university grading tutor. You can evaluate text and attached student photos/diagrams.",
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
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("submit_answer_button")
                    ) {
                        if (isGrading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Submit & Grade", color = Color.White, fontSize = 12.sp)
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
                    Text(text = res.feedback, color = colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)

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
                        Text(text = res.recommendedRevision, color = colors.textSecondary, fontSize = 11.sp)

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
            Text("Past Questions & Results (${courseSessions.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        items(courseSessions) { session ->
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
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
}
