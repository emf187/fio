package com.example.fio.ui.screens

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.document.DocumentImporter
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReadingMode {
    ACADEMIC,
    LEISURE,
    RESEARCH
}

@Composable
fun StudyCompanionScreen(
    repository: FioRepository,
    aiClient: FioAiClient
) {
    val colors = LocalFioColors.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val courses by repository.courses.collectAsState()
    val documents by repository.documents.collectAsState()
    val flashcards by repository.flashcards.collectAsState()
    val chatHistory by repository.chatHistory.collectAsState()
    val settings by repository.settings.collectAsState()

    var selectedSection by remember { mutableStateOf(SectionType.STUDY) }
    var selectedCourseId by remember {
        mutableStateOf(courses.firstOrNull { it.sectionType == SectionType.STUDY }?.id ?: "")
    }

    // Keep selectedCourse valid when switching sections
    LaunchedEffect(selectedSection, courses) {
        val matching = courses.filter { it.sectionType == selectedSection }
        if (matching.none { it.id == selectedCourseId }) {
            selectedCourseId = matching.firstOrNull()?.id ?: ""
        }
    }

    val currentCourse = courses.find { it.id == selectedCourseId }
    val courseDocs = documents.filter { it.courseId == selectedCourseId }
    val courseFlashcards = flashcards.filter { it.courseId == selectedCourseId }
    val courseChatMessages = chatHistory.filter { it.courseId == selectedCourseId }

    // Active Reader State
    var activeReadingDoc by remember { mutableStateOf<StudyDocument?>(null) }
    var readingMode by remember { mutableStateOf(ReadingMode.ACADEMIC) }
    var readerFontSize by remember { mutableStateOf(15.sp) }

    // Document Picker State
    var targetCourseIdForImport by remember { mutableStateOf("") }
    var isImportingDocs by remember { mutableStateOf(false) }

    // System File Picker for PDF, EPUB, DOCX, CSV/XLS, MD, TXT
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val targetId = if (targetCourseIdForImport.isNotBlank()) targetCourseIdForImport else selectedCourseId
            if (targetId.isNotBlank()) {
                isImportingDocs = true
                coroutineScope.launch {
                    var successCount = 0
                    for (uri in uris) {
                        val imported = DocumentImporter.importDocument(context, uri, targetId)
                        if (imported != null) {
                            repository.addDocument(imported)
                            successCount++
                        }
                    }
                    isImportingDocs = false
                    Toast.makeText(
                        context,
                        "Imported $successCount document(s) into ${courses.find { it.id == targetId }?.name ?: "course"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(context, "Please create or select a course first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // AI Q&A State (Wingman)
    var aiQuery by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var coursePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showChatHistory by remember { mutableStateOf(true) }

    val coursePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> coursePhotoUri = uri }
    )

    // Dialogs
    var showAddCourseDialog by remember { mutableStateOf(false) }
    var showAddDocDialog by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }
    var newDocTitle by remember { mutableStateOf("") }
    var newDocContent by remember { mutableStateOf("") }

    // Document Reader Dialog
    activeReadingDoc?.let { doc ->
        val isLeisure = readingMode == ReadingMode.LEISURE
        val readerBg = if (isLeisure) Color(0xFF141416) else colors.background

        AlertDialog(
            onDismissRequest = { activeReadingDoc = null },
            modifier = Modifier.fillMaxSize().padding(12.dp),
            containerColor = readerBg,
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = doc.title,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeReadingDoc = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                        }
                    }

                    // Reading Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReadingMode.values().forEach { mode ->
                            val isSelected = readingMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                    .clickable { readingMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (isSelected) colors.primary else colors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        // Font Sizing
                        IconButton(
                            onClick = { readerFontSize = (readerFontSize.value - 1).coerceAtLeast(12f).sp },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("A-", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { readerFontSize = (readerFontSize.value + 1).coerceAtMost(22f).sp },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("A+", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    FioDivider(modifier = Modifier.padding(top = 6.dp))
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (readingMode == ReadingMode.ACADEMIC) {
                        item {
                            FioCard(
                                backgroundColor = colors.surfaceElevated,
                                borderColor = colors.border,
                                padding = PaddingValues(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Academic Mode: Annotations & schedule reading session", color = colors.textSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            repository.addEvent(
                                                ScheduleEvent(
                                                    title = "Read: ${doc.title}",
                                                    startTimeMillis = now + (60 * 60 * 1000L),
                                                    endTimeMillis = now + (110 * 60 * 1000L),
                                                    type = EventType.STUDY
                                                )
                                            )
                                            Toast.makeText(context, "Added to Calendar Schedule", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("+ Schedule", color = colors.primary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = doc.content,
                            color = if (isLeisure) Color(0xFFE4E4E7) else colors.textPrimary,
                            fontSize = readerFontSize,
                            lineHeight = (readerFontSize.value * 1.5).sp,
                            fontFamily = if (isLeisure) FontFamily.Serif else FontFamily.Default
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeReadingDoc = null }) {
                    Text("Done Reading", color = colors.primary)
                }
            }
        )
    }

    // New Course Dialog with automatic file picker option
    if (showAddCourseDialog) {
        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            containerColor = colors.surface,
            title = { Text("New ${selectedSection.name.lowercase().replaceFirstChar { it.uppercase() }} Subject", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter course/subject name. You can immediately pick PDF, EPUB, Docs, Sheets, or MD files from your device.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newCourseName,
                        onValueChange = { newCourseName = it },
                        label = { Text("Course Title (e.g., Biology, Philosophy)", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_course_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (newCourseName.isNotBlank()) {
                                val created = repository.addCourse(newCourseName.trim(), selectedSection)
                                selectedCourseId = created.id
                                newCourseName = ""
                                showAddCourseDialog = false
                            }
                        }
                    ) {
                        Text("Create", color = colors.textPrimary, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (newCourseName.isNotBlank()) {
                                val created = repository.addCourse(newCourseName.trim(), selectedSection)
                                selectedCourseId = created.id
                                targetCourseIdForImport = created.id
                                newCourseName = ""
                                showAddCourseDialog = false
                                // Automatically open file picker to select docs
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/epub+zip",
                                        "text/*",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "*/*"
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create & Select Files", color = Color.White, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCourseDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Manual Text Document Dialog
    if (showAddDocDialog) {
        AlertDialog(
            onDismissRequest = { showAddDocDialog = false },
            containerColor = colors.surface,
            title = { Text("Add Text / Notes to Course", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newDocTitle,
                        onValueChange = { newDocTitle = it },
                        label = { Text("Title", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("doc_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newDocContent,
                        onValueChange = { newDocContent = it },
                        label = { Text("Content / Text Extract (Markdown/TXT)", color = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDocTitle.isNotBlank() && selectedCourseId.isNotBlank()) {
                            val chunks = newDocContent.chunked(600).mapIndexed { i, ch ->
                                DocumentChunk("chk_$i", "Section ${i + 1}", ch)
                            }
                            repository.addDocument(
                                StudyDocument(
                                    courseId = selectedCourseId,
                                    title = newDocTitle.trim(),
                                    content = newDocContent,
                                    chunks = chunks,
                                    format = "TXT"
                                )
                            )
                            newDocTitle = ""
                            newDocContent = ""
                            showAddDocDialog = false
                            Toast.makeText(context, "Note added to course", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save to Course", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDocDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Section Tabs (STUDY, LEISURE, RESEARCH) ---
        item(key = "section_tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionType.values().forEach { sec ->
                    val isSelected = selectedSection == sec
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                            .clickable { selectedSection = sec }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sec.name,
                            color = if (isSelected) colors.primary else colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- 2. Course / Context Picker ---
        item(key = "course_picker_header") {
            val sectionCourses = courses.filter { it.sectionType == selectedSection }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedSection.name} Subjects (${sectionCourses.size})",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddCourseDialog = true },
                    modifier = Modifier.testTag("add_course_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Course", tint = colors.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Subject", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (sectionCourses.isEmpty()) {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("No subjects created yet in ${selectedSection.name}.", color = colors.textMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = { showAddCourseDialog = true }) {
                            Text("+ Create your first subject & select files", color = colors.primary, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sectionCourses.forEach { c ->
                        val isSelected = c.id == selectedCourseId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surface)
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                .clickable { selectedCourseId = c.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = c.name,
                                color = if (isSelected) colors.primary else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Wingman AI Study Companion for Selected Course ---
        item(key = "wingman_card") {
            FioCard(
                backgroundColor = colors.surfaceElevated,
                borderColor = colors.border,
                padding = PaddingValues(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wingman: ${currentCourse?.name ?: "No Subject Selected"}",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (courseChatMessages.isNotEmpty()) {
                        TextButton(
                            onClick = { repository.clearChatHistory(selectedCourseId) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear Session", color = colors.textMuted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Answers questions, analyzes images/diagrams, and summarizes using your documents.",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )

                // Optional attached image preview
                coursePhotoUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attached Image for Wingman", color = colors.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { coursePhotoUri = null }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = colors.textMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            coursePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Attach Image", tint = if (coursePhotoUri != null) colors.primary else colors.textMuted)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = aiQuery,
                        onValueChange = { aiQuery = it },
                        placeholder = { Text(if (currentCourse != null) "Ask Wingman about ${currentCourse.name}..." else "Select a subject first...", color = colors.textMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).testTag("course_ai_query_input"),
                        enabled = currentCourse != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if ((aiQuery.isNotBlank() || coursePhotoUri != null) && !isAiLoading && currentCourse != null) {
                                val queryText = aiQuery.ifBlank { "Please examine this attached image and explain it in the context of the course material." }
                                val activeCourse = currentCourse
                                isAiLoading = true
                                val userMsg = ChatMessage(
                                    sender = "You",
                                    text = queryText,
                                    courseId = activeCourse.id
                                )
                                repository.addChatMessage(userMsg)
                                aiQuery = ""

                                coroutineScope.launch {
                                    val storedImg = if (coursePhotoUri != null) ImageStorage.processAndStoreImage(context, coursePhotoUri!!) else null
                                    coursePhotoUri = null
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt(
                                        prompt = queryText,
                                        systemInstruction = "You are Wingman, a precise personal study tutor answering questions based on course material and diagrams. Subject: ${activeCourse.name}",
                                        settings = settings,
                                        chunks = chunks,
                                        imageBase64 = storedImg?.second
                                    )) {
                                        is AiResult.Success -> {
                                            repository.addChatMessage(
                                                ChatMessage(
                                                    sender = "Wingman",
                                                    text = res.data,
                                                    courseId = activeCourse.id
                                                )
                                            )
                                        }
                                        is AiResult.Error -> {
                                            repository.addChatMessage(
                                                ChatMessage(
                                                    sender = "Wingman",
                                                    text = "Could not reach AI: ${res.message}. Check your API Key in Settings.",
                                                    courseId = activeCourse.id
                                                )
                                            )
                                        }
                                    }
                                    isAiLoading = false
                                }
                            }
                        },
                        enabled = currentCourse != null && !isAiLoading,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .testTag("course_ai_query_submit")
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Query", tint = colors.primary)
                        }
                    }
                }

                // AI Study Generator Shortcuts
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isAiLoading && courseDocs.isNotEmpty() && currentCourse != null) {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt("Generate 3 key flashcards (Q and A format)", "Create concise flashcards for spaced repetition.", settings, chunks)) {
                                        is AiResult.Success -> {
                                            repository.addFlashcard(Flashcard(courseId = selectedCourseId, front = "AI Generated: Core Thesis", back = res.data.take(180)))
                                            repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Generated Spaced Repetition Flashcards:\n${res.data}", courseId = selectedCourseId))
                                        }
                                        is AiResult.Error -> {
                                            repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Flashcard generation error: ${res.message}", courseId = selectedCourseId))
                                        }
                                    }
                                    isAiLoading = false
                                }
                            } else if (courseDocs.isEmpty()) {
                                Toast.makeText(context, "Import documents first to generate flashcards", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Flashcards", fontSize = 10.sp, color = colors.primary)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isAiLoading && courseDocs.isNotEmpty() && currentCourse != null) {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt("Generate an executive study guide and summary.", "Synthesize clear study notes.", settings, chunks)) {
                                        is AiResult.Success -> {
                                            repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Executive Study Guide:\n${res.data}", courseId = selectedCourseId))
                                        }
                                        is AiResult.Error -> {
                                            repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Study Guide generation error: ${res.message}", courseId = selectedCourseId))
                                        }
                                    }
                                    isAiLoading = false
                                }
                            } else if (courseDocs.isEmpty()) {
                                Toast.makeText(context, "Import documents first to generate study guide", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Study Guide", fontSize = 10.sp, color = colors.primary)
                    }
                }

                // Chat History Stream for this Course
                if (courseChatMessages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FioDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showChatHistory = !showChatHistory },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Study Session Chat History (${courseChatMessages.size})",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showChatHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (showChatHistory) {
                        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            courseChatMessages.takeLast(10).forEach { msg ->
                                val isUser = msg.sender == "You"
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isUser) colors.surface else colors.background)
                                        .border(1.dp, if (isUser) colors.border else colors.primary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUser) "You" else "Wingman",
                                            color = if (isUser) colors.primary else colors.info,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = timeFormat.format(Date(msg.timestampMillis)),
                                            color = colors.textMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Course Documents & Books ---
        item(key = "library_header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Library & Documents (${courseDocs.size})",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isImportingDocs) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.primary, strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Primary Import Buttons: System File Picker & Manual Note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedCourseId.isNotBlank()) {
                                targetCourseIdForImport = selectedCourseId
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/epub+zip",
                                        "text/*",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "*/*"
                                    )
                                )
                            } else {
                                Toast.makeText(context, "Create or select a subject above first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).testTag("import_doc_picker_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Files (PDF/EPUB/MD/Sheets)", color = Color.White, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (selectedCourseId.isNotBlank()) {
                                showAddDocDialog = true
                            } else {
                                Toast.makeText(context, "Create or select a subject above first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Text", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (courseDocs.isEmpty()) {
            item(key = "empty_docs") {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (currentCourse != null) "No documents in ${currentCourse.name} yet" else "No subject selected",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Select Files' to import PDFs, EPUBs, Markdown notes, Word docs, or CSV spreadsheets directly from your device storage.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        } else {
            items(courseDocs, key = { it.id }) { doc ->
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeReadingDoc = doc }
                        .testTag("doc_item_${doc.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (doc.format) {
                                "PDF" -> Icons.Default.PictureAsPdf
                                "EPUB" -> Icons.Default.MenuBook
                                "SPREADSHEET" -> Icons.Default.TableChart
                                "DOCX" -> Icons.Default.Description
                                else -> Icons.Default.Article
                            },
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FioBadge(text = doc.format, color = colors.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = doc.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            if (doc.author.isNotBlank()) {
                                Text(text = doc.author, color = colors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { doc.readingProgressPercent / 100f },
                                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = colors.primary,
                                    trackColor = colors.surfaceElevated
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "${doc.readingProgressPercent}%", color = colors.textSecondary, fontSize = 10.sp)
                            }
                        }
                        IconButton(onClick = { activeReadingDoc = doc }) {
                            Icon(imageVector = Icons.Default.MenuBook, contentDescription = "Read", tint = colors.primary)
                        }
                    }
                }
            }
        }

        // --- 5. Course Flashcards Review ---
        item(key = "flashcards_header") {
            Text("Spaced Repetition Flashcards (${courseFlashcards.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        if (courseFlashcards.isEmpty()) {
            item(key = "empty_flashcards") {
                Text(
                    text = "No flashcards yet. Tap '+ Flashcards' in Wingman above to automatically generate cards from your imported documents.",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
        } else {
            items(courseFlashcards, key = { it.id }) { card ->
                var revealed by remember { mutableStateOf(false) }
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { revealed = !revealed }
                ) {
                    Text(text = "Q: ${card.front}", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (revealed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "A: ${card.back}", color = colors.primary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    repository.recordFlashcardReview(card.id, true)
                                    revealed = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.success),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("I Knew It", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    repository.recordFlashcardReview(card.id, false)
                                    revealed = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Forgot", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Text(text = "Tap to reveal answer", color = colors.textMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
