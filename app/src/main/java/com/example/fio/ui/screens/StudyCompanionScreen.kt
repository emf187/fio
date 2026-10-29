package com.example.fio.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.model.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch

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

    val courses by repository.courses.collectAsState()
    val documents by repository.documents.collectAsState()
    val flashcards by repository.flashcards.collectAsState()
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

    // Active Reader State
    var activeReadingDoc by remember { mutableStateOf<StudyDocument?>(null) }
    var readingMode by remember { mutableStateOf(ReadingMode.ACADEMIC) }
    var readerFontSize by remember { mutableStateOf(15.sp) }

    val context = LocalContext.current

    // AI Q&A State (Wingman)
    var aiQuery by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var isAiLoading by remember { mutableStateOf(false) }
    var coursePhotoUri by remember { mutableStateOf<Uri?>(null) }

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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

    if (showAddCourseDialog) {
        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            containerColor = colors.surface,
            title = { Text("New Course / Subject", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newCourseName,
                    onValueChange = { newCourseName = it },
                    label = { Text("Course Title", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_course_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCourseName.isNotBlank()) {
                            repository.addCourse(newCourseName, selectedSection)
                            newCourseName = ""
                            showAddCourseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCourseDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showAddDocDialog) {
        AlertDialog(
            onDismissRequest = { showAddDocDialog = false },
            containerColor = colors.surface,
            title = { Text("Import Document / Book", color = colors.textPrimary) },
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
                                    title = newDocTitle,
                                    content = newDocContent,
                                    chunks = chunks
                                )
                            )
                            newDocTitle = ""
                            newDocContent = ""
                            showAddDocDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Import to Course", color = Color.White)
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
        item {
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
        item {
            val sectionCourses = courses.filter { it.sectionType == selectedSection }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Courses in ${selectedSection.name}",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showAddCourseDialog = true }, modifier = Modifier.testTag("add_course_button")) {
                    Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = "Add Course", tint = colors.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
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

        // --- 3. Wingman AI Q&A for Selected Course ---
        item {
            FioCard(
                backgroundColor = colors.surfaceElevated,
                borderColor = colors.border,
                padding = PaddingValues(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wingman: ${currentCourse?.name ?: "Course Context"}",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Answers questions, analyzes images/diagrams, and summarizes using your course documents.",
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
                        placeholder = { Text("Ask Wingman about this material...", color = colors.textMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).testTag("course_ai_query_input"),
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
                            if ((aiQuery.isNotBlank() || coursePhotoUri != null) && !isAiLoading) {
                                isAiLoading = true
                                aiAnswer = null
                                coroutineScope.launch {
                                    val storedImg = if (coursePhotoUri != null) ImageStorage.processAndStoreImage(context, coursePhotoUri!!) else null
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt(
                                        prompt = aiQuery.ifBlank { "Please examine this attached image and explain it in the context of the course material." },
                                        systemInstruction = "You are Wingman, a precise study tutor answering questions based on course material and attached diagrams. Course: ${currentCourse?.name}",
                                        settings = settings,
                                        chunks = chunks,
                                        imageBase64 = storedImg?.second
                                    )) {
                                        is AiResult.Success -> {
                                            aiAnswer = res.data
                                            coursePhotoUri = null
                                        }
                                        is AiResult.Error -> aiAnswer = "Error: ${res.message}"
                                    }
                                    isAiLoading = false
                                }
                            }
                        },
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

                aiAnswer?.let { answer ->
                    Spacer(modifier = Modifier.height(10.dp))
                    FioCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        padding = PaddingValues(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = answer, color = colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }

                // AI Study Generator Shortcuts
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isAiLoading && courseDocs.isNotEmpty()) {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt("Generate 3 key flashcards (Q and A format)", "Create concise flashcards for spaced repetition.", settings, chunks)) {
                                        is AiResult.Success -> {
                                            // Add generated flashcards
                                            repository.addFlashcard(Flashcard(courseId = selectedCourseId, front = "AI Generated: Core Thesis", back = res.data.take(180)))
                                            aiAnswer = "Generated and added flashcard: \n${res.data}"
                                        }
                                        is AiResult.Error -> aiAnswer = "Failed: ${res.message}"
                                    }
                                    isAiLoading = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Flashcards", fontSize = 10.sp, color = colors.primary)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isAiLoading && courseDocs.isNotEmpty()) {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val chunks = courseDocs.flatMap { it.chunks }
                                    when (val res = aiClient.sendPrompt("Generate an executive study guide and summary.", "Synthesize clear study notes.", settings, chunks)) {
                                        is AiResult.Success -> aiAnswer = "Study Guide:\n${res.data}"
                                        is AiResult.Error -> aiAnswer = "Failed: ${res.message}"
                                    }
                                    isAiLoading = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Study Guide", fontSize = 10.sp, color = colors.primary)
                    }
                }
            }
        }

        // --- 4. Course Documents & Books ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Library & Documents (${courseDocs.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddDocDialog = true }, modifier = Modifier.testTag("import_doc_button")) {
                    Text("+ Import Document", color = colors.primary, fontSize = 12.sp)
                }
            }
        }

        if (courseDocs.isEmpty()) {
            item {
                Text("No documents yet in this course. Tap '+ Import Document' to add text, EPUB, or notes.", color = colors.textMuted, fontSize = 12.sp)
            }
        } else {
            items(courseDocs) { doc ->
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
                        Icon(imageVector = Icons.Default.Book, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
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
        item {
            Text("Spaced Repetition Flashcards (${courseFlashcards.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        items(courseFlashcards) { card ->
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
