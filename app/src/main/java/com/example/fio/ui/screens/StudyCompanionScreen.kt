package com.example.fio.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.fio.ui.components.FioMarkdown
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.reader.FioReaderHost
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // Claude-style conversation title: the chat is named after its first
    // question, so each subject session reads like a titled conversation.
    val chatTitle = courseChatMessages.firstOrNull { it.sender == "You" }
        ?.text?.trim()?.lineSequence()?.firstOrNull()?.trim()?.take(44)
        ?.ifBlank { null } ?: "New Chat"

    // Active Reader State
    var activeReadingDoc by remember { mutableStateOf<StudyDocument?>(null) }

    // Long-press target for the "remove document" confirmation
    var docPendingRemoval by remember { mutableStateOf<StudyDocument?>(null) }

    // Document Picker State
    var targetCourseIdForImport by remember { mutableStateOf("") }
    var isImportingDocs by remember { mutableStateOf(false) }
    var importDone by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }
    var importCurrentFile by remember { mutableStateOf("") }

    // System File Picker for PDF, EPUB, DOCX, CSV/XLS, MD, TXT
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val targetId = if (targetCourseIdForImport.isNotBlank()) targetCourseIdForImport else selectedCourseId
            if (targetId.isNotBlank()) {
                isImportingDocs = true
                importTotal = uris.size
                importDone = 0
                importCurrentFile = ""
                coroutineScope.launch {
                    var successCount = 0
                    for ((index, uri) in uris.withIndex()) {
                        importCurrentFile = uri.lastPathSegment
                            ?.substringAfterLast('/')
                            ?.substringAfterLast(':')
                            ?.takeIf { it.isNotBlank() }
                            ?: "Document ${index + 1}"
                        val imported = DocumentImporter.importDocument(context, uri, targetId)
                        if (imported != null) {
                            repository.addDocument(imported)
                            successCount++
                        }
                        importDone = index + 1
                    }
                    importCurrentFile = ""
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
    // Claude-style tabs inside the chat window: Chat (live) / History (log).
    var chatTab by remember { mutableStateOf(0) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val clipboard = LocalClipboardManager.current

    // One shared path for the send button, Retry and Edit-and-resend, so the
    // chat behaves like a real LLM conversation from every entry point.
    fun launchStudyQuery(prompt: String, photoUri: Uri?) {
        val activeCourse = currentCourse ?: return
        if (prompt.isBlank() || isAiLoading) return
        isAiLoading = true
        repository.addChatMessage(ChatMessage(sender = "You", text = prompt, courseId = activeCourse.id))
        aiQuery = ""
        aiJob = coroutineScope.launch {
            try {
                val storedImg = if (photoUri != null) ImageStorage.processAndStoreImage(context, photoUri) else null
                val chunks = courseDocs.flatMap { it.chunks }
                when (val res = aiClient.sendPrompt(
                    prompt = prompt,
                    systemInstruction = "You are Wingman, a precise personal study tutor answering questions based on course material and diagrams. Subject: ${activeCourse.name}",
                    settings = settings,
                    chunks = chunks,
                    imageBase64 = storedImg?.second
                )) {
                    is AiResult.Success -> {
                        repository.addChatMessage(ChatMessage(sender = "Wingman", text = res.data, courseId = activeCourse.id))
                    }
                    is AiResult.Error -> {
                        val err = res.message
                        val needsKeyHint = err.contains("401") || err.contains("403") ||
                            err.contains("API key", ignoreCase = true) || err.contains("quota", ignoreCase = true)
                        val hint = if (needsKeyHint) " Check your API Key in Settings." else ""
                        repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Could not reach AI: $err.$hint", courseId = activeCourse.id))
                    }
                }
            } finally {
                isAiLoading = false
            }
        }
    }

    // Stop button on the loading state: cancels the in-flight request.
    fun cancelStudyQuery() {
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
        if (selectedCourseId.isNotBlank()) {
            repository.addChatMessage(ChatMessage(sender = "Wingman", text = "Response stopped.", courseId = selectedCourseId))
        }
    }

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

    // Full-screen Areada-based reader (see ui/reader/FioReaderHost.kt).
    // Hosted in its own full-screen window so it always draws above the library.
    activeReadingDoc?.let { doc ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { activeReadingDoc = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FioReaderHost(
                document = doc,
                onClose = { percent, position ->
                    repository.updateReadingProgress(doc.id, percent, position)
                    activeReadingDoc = null
                },
                onProgress = { percent, position ->
                    repository.updateReadingProgress(doc.id, percent, position)
                }
            )
        }
    }

    // Long-press confirmation: remove a document from the library
    docPendingRemoval?.let { doc ->
        AlertDialog(
            onDismissRequest = { docPendingRemoval = null },
            containerColor = colors.surface,
            title = { Text("Remove document?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "\u201c${doc.title}\u201d will be removed from your library. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeDocument(doc.id)
                    if (activeReadingDoc?.id == doc.id) activeReadingDoc = null
                    docPendingRemoval = null
                }) {
                    Text("Remove", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { docPendingRemoval = null }) {
                    Text("Cancel", color = colors.textMuted)
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
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp)
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionType.values().forEach { sec ->
                    val isSelected = selectedSection == sec
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
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
                    shape = RoundedCornerShape(12.dp)
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surface)
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(12.dp))
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
                    // Claude-style: the conversation's own title, auto-built
                    // from the first question of the session.
                    Text(
                        text = chatTitle,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("chat_title")
                    )
                    // History: now lives in the History tab inside the chat.
                    if (courseChatMessages.isNotEmpty()) {
                        TextButton(
                            onClick = { repository.clearChatHistory(selectedCourseId) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("New Chat", color = colors.textMuted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Wingman · ${currentCourse?.name ?: "No Subject Selected"} — answers, images, summaries",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Optional attached image preview
                coursePhotoUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
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
                            .clip(RoundedCornerShape(12.dp))
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
                            if (isAiLoading) {
                                cancelStudyQuery()
                            } else if ((aiQuery.isNotBlank() || coursePhotoUri != null) && currentCourse != null) {
                                val queryText = aiQuery.ifBlank { "Please examine this attached image and explain it in the context of the course material." }
                                val img = coursePhotoUri
                                coursePhotoUri = null
                                launchStudyQuery(queryText, img)
                            }
                        },
                        enabled = currentCourse != null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer)
                            .testTag("course_ai_query_submit")
                    ) {
                        if (isAiLoading) {
                            // Stop: cancels the in-flight response.
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Stop generating", tint = colors.error)
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
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Flashcards", fontSize = 10.sp, color = colors.textPrimary)
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
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Study Guide", fontSize = 10.sp, color = colors.textPrimary)
                    }
                }

                // Claude-style tabs inside the chat window: the live chat vs
                // the full history log for this subject.
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .testTag("chat_tabs")
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Chat" to 0, "History (${courseChatMessages.size})" to 1).forEach { (tabLabel, tabIndex) ->
                        val isSelected = chatTab == tabIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                                .clickable { chatTab = tabIndex }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) colors.primary else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val dayFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

                // One bubble renderer shared by the Chat tab and expanded History sessions.
                val chatBubble: @Composable (ChatMessage) -> Unit = { msg ->
                            val isUser = msg.sender == "You"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isUser) colors.surface else colors.background)
                                    .border(1.dp, if (isUser) colors.border else colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeFormat.format(Date(msg.timestampMillis)),
                                            color = colors.textMuted,
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        // Copy — every message, one tap.
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy message",
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    clipboard.setText(AnnotatedString(msg.text))
                                                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isUser) {
                                            // Edit & resend: rewinds the chat to
                                            // just before this question.
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit and resend",
                                                tint = colors.textMuted,
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clickable {
                                                        repository.deleteChatMessagesFrom(msg.id)
                                                        aiQuery = msg.text
                                                        Toast.makeText(context, "Edit your message, then press send", Toast.LENGTH_SHORT).show()
                                                    }
                                            )
                                        } else if (msg.text.startsWith("Could not reach AI")) {
                                            // Retry a failed request with the same prompt.
                                            Text(
                                                text = "Retry",
                                                color = colors.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    val failedPrompt = courseChatMessages.lastOrNull { it.sender == "You" }?.text
                                                    if (failedPrompt != null) {
                                                        repository.deleteChatMessagesFrom(msg.id)
                                                        launchStudyQuery(failedPrompt, null)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (isUser) {
                                    Text(
                                        text = msg.text,
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                } else {
                                    FioMarkdown(
                                        text = msg.text,
                                        baseFontSize = 11.sp
                                    )
                                }
                            }
                }

                if (chatTab == 0) {
                    // --- Chat tab: live rolling context, last 4 messages ---
                    val recent = courseChatMessages.takeLast(4)
                    if (recent.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recent.forEach { chatBubble(it) }
                        }
                    } else {
                        Text("Ask your first question to start the chat.", color = colors.textMuted, fontSize = 11.sp)
                    }
                } else {
                    // --- History tab: past chats grouped into dated sessions ---
                    if (courseChatMessages.isEmpty()) {
                        Text("No saved messages yet.", color = colors.textMuted, fontSize = 11.sp)
                    } else {
                        // A new session starts whenever there is a long pause
                        // (> 30 min) between messages, so the log reads like
                        // separate conversations instead of one endless list.
                        val sessions = remember(courseChatMessages) {
                            val gapMs = 30L * 60L * 1000L
                            val groups = mutableListOf<MutableList<ChatMessage>>()
                            courseChatMessages.forEach { msg ->
                                val current = groups.lastOrNull()
                                if (current == null || msg.timestampMillis - current.last().timestampMillis > gapMs) {
                                    groups.add(mutableListOf(msg))
                                } else {
                                    current.add(msg)
                                }
                            }
                            groups.map { it.toList() }.reversed()
                        }
                        var expandedSession by remember { mutableStateOf<String?>(null) }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                                    ?: "Wingman chat"
                                val lastText = session.last().text
                                val description = when {
                                    lastText.startsWith("Could not reach AI") ->
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
                                        .clickable { expandedSession = if (isExpanded) null else sessionKey }
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
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = description,
                                        color = colors.textSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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
                                        session.forEach { chatBubble(it) }
                                    }
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
                        shape = RoundedCornerShape(12.dp),
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
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textSecondary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Text", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (isImportingDocs) {
            item(key = "import_progress") {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.primary.copy(alpha = 0.4f),
                    padding = PaddingValues(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Importing documents",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$importDone / $importTotal",
                                color = colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (importTotal > 0) importDone.toFloat() / importTotal.toFloat() else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.primary,
                            trackColor = colors.surfaceElevated
                        )
                        if (importCurrentFile.isNotBlank()) {
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = "Processing $importCurrentFile\u2026",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                        .pointerInput(doc.id) {
                            detectTapGestures(
                                onTap = { activeReadingDoc = doc },
                                onLongPress = { docPendingRemoval = doc }
                            )
                        }
                        .testTag("doc_item_${doc.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (doc.format) {
                                "PDF" -> Icons.Default.PictureAsPdf
                                "EPUB" -> Icons.Default.MenuBook
                                "FB2" -> Icons.Default.MenuBook
                                "SPREADSHEET" -> Icons.Default.TableChart
                                "SLIDES" -> Icons.Default.Slideshow
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
                                shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
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
