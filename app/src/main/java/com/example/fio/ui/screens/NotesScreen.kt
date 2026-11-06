package com.example.fio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.fio.data.model.NoteItem
import com.example.fio.data.model.formatTimestamp
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.buildMarkdownAnnotated
import com.example.fio.ui.components.markdownVisualTransformation
import com.example.fio.ui.components.stripMarkdownMarkers
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notes by repository.notes.collectAsState()
    val noteFolders by repository.noteFolders.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    // Note categories: null = All folders, "" = Unfiled, otherwise a folder name.
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    // New-folder dialog (list view)
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    // Long-press a folder chip → delete confirm
    var folderPendingDelete by remember { mutableStateOf<String?>(null) }

    // Active Note Editor state
    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var isPreviewMode by remember { mutableStateOf(false) }

    // Debounced editor persistence: typtng updates the screen tnstantly and only
    // sertaltzes to disk once the user pauses, so long notes never lag keystrokes.
    var pendingNoteSave by remember { mutableStateOf<NoteItem?>(null) }

    fun queueNoteSave(updated: NoteItem) {
        val previous = pendingNoteSave
        if (previous != null && previous.id != updated.id) {
            repository.saveNote(previous) // switching notes: flush the old one now
        }
        pendingNoteSave = updated
    }

    LaunchedEffect(pendingNoteSave) {
        val pending = pendingNoteSave ?: return@LaunchedEffect
        delay(600)
        repository.saveNote(pending)
        if (pendingNoteSave === pending) pendingNoteSave = null
    }

    DisposableEffect(Unit) {
        onDispose { pendingNoteSave?.let { repository.saveNote(it) } }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            editingNote?.let { note ->
                if (uri != null) {
                    coroutineScope.launch {
                        val stored = ImageStorage.processAndStoreImage(context, uri)
                        val storedPath = stored?.first ?: uri.toString()
                        val updatedUris = note.imageUris + storedPath
                        val updatedContent = note.content + "\n\n![Image Attachment]($storedPath)"
                        val updated = note.copy(imageUris = updatedUris, content = updatedContent)
                        editingNote = updated
                        pendingNoteSave = null
                        repository.saveNote(updated)
                    }
                }
            }
        }
    )

    // All distinct tags across notes
    val allTags = remember(notes) {
        notes.flatMap { it.tags }.distinct()
    }

    // Union of persisted folders and folders found on notes (both stay selectable).
    val allFolders = remember(notes, noteFolders) {
        (noteFolders + notes.mapNotNull { it.folder.ifBlank { null } }).distinct().sorted()
    }

    val filteredNotes = remember(notes, searchQuery, selectedTag, selectedFolder) {
        notes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            val matchesTag = selectedTag == null || note.tags.contains(selectedTag)
            val matchesFolder = when (selectedFolder) {
                null -> true
                "" -> note.folder.isBlank()
                else -> note.folder.equals(selectedFolder, ignoreCase = true)
            }
            matchesSearch && matchesTag && matchesFolder
        }
    }

    if (editingNote != null) {
        val note = editingNote!!
        // TextFieldValue drives the body editor so the toolbar can format the current selection.
        var contentValue by remember(note.id) { mutableStateOf(TextFieldValue(note.content)) }
        // Folder picker for this note
        var showNoteFolderPicker by remember { mutableStateOf(false) }

        fun commitContentChange(update: TextFieldValue = contentValue) {
            contentValue = update
            // Automatically parse #tags
            val extractedTags = Regex("#([a-zA-Z0-9_-]+)").findAll(update.text).map { it.groupValues[1] }.toList()
            val updated = note.copy(content = update.text, tags = extractedTags)
            editingNote = updated
            queueNoteSave(updated)
        }

        // --- Full Screen Obsidian Markdown Editor ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(16.dp)
        ) {
            // Editor Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    pendingNoteSave = null
                    repository.saveNote(note)
                    editingNote = null
                }, modifier = Modifier.testTag("back_from_note_button")) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Preview vs Edit toggle
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }, modifier = Modifier.testTag("toggle_preview_button")) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = "Toggle Preview",
                            tint = colors.primary
                        )
                    }

                    // Attach Image
                    IconButton(onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }, modifier = Modifier.testTag("attach_image_button")) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach Image", tint = colors.textSecondary)
                    }

                    // Delete Note
                    IconButton(onClick = {
                        pendingNoteSave = null
                        repository.deleteNote(note.id)
                        editingNote = null
                    }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = colors.error)
                    }
                }
            }

            FioDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Title Input
            OutlinedTextField(
                value = note.title,
                onValueChange = { newTitle ->
                    val updated = note.copy(title = newTitle)
                    editingNote = updated
                    queueNoteSave(updated)
                },
                placeholder = { Text("Note Title", color = colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Text(
                text = "Last updated: ${formatTimestamp(note.updatedAtMillis)}",
                color = colors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Folder selector for this note (categories)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (note.folder.isBlank()) colors.surfaceElevated else colors.primaryContainer)
                        .border(1.dp, if (note.folder.isBlank()) colors.border else colors.primary, RoundedCornerShape(10.dp))
                        .clickable { showNoteFolderPicker = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("note_folder_selector")
                ) {
                    Text(
                        text = if (note.folder.isBlank()) "Add to folder" else note.folder,
                        color = if (note.folder.isBlank()) colors.textMuted else colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            FioDivider(modifier = Modifier.padding(vertical = 6.dp))

            if (!isPreviewMode) {
                // --- Formatting Toolbar ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormatChip(label = "B", tag = "md_format_bold", fontWeight = FontWeight.Bold) {
                        commitContentChange(applyMarkdownMarkers(contentValue, "**", "**"))
                    }
                    FormatChip(label = "I", tag = "md_format_italic", fontStyle = FontStyle.Italic) {
                        commitContentChange(applyMarkdownMarkers(contentValue, "*", "*"))
                    }
                    FormatChip(label = "U", tag = "md_format_underline", textDecoration = TextDecoration.Underline) {
                        commitContentChange(applyMarkdownMarkers(contentValue, "__", "__"))
                    }
                    FormatChip(label = "S", tag = "md_format_strike", textDecoration = TextDecoration.LineThrough) {
                        commitContentChange(applyMarkdownMarkers(contentValue, "~~", "~~"))
                    }
                    FormatChip(label = "•", tag = "md_format_bullet") {
                        commitContentChange(applyMarkdownListPrefix(contentValue, "- "))
                    }
                    FormatChip(label = "✓", tag = "md_format_check") {
                        commitContentChange(applyMarkdownListPrefix(contentValue, "- [ ] "))
                    }
                    FormatChip(label = "H", tag = "md_format_heading") {
                        commitContentChange(applyMarkdownListPrefix(contentValue, "# ", firstLineOnly = true))
                    }
                    FormatChip(label = "[[]]", tag = "md_format_wikilink") {
                        commitContentChange(applyMarkdownMarkers(contentValue, "[[", "]]"))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Markdown editor: bounded height (scrolls with the caret) and live styled markers.
                OutlinedTextField(
                    value = contentValue,
                    onValueChange = { update -> commitContentChange(update) },
                    visualTransformation = markdownVisualTransformation(colors.textPrimary),
                    placeholder = { Text("Write in Markdown...\nUse [[links]] to connect notes\nUse #tags to organize\nUse **bold** and # headers", color = colors.textMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("note_content_editor"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            } else {
                // Markdown Rendered Preview with Clickable [[links]]
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val lines = note.content.lines()
                    items(lines) { line ->
                        when {
                            line.startsWith("# ") -> {
                                Text(line.removePrefix("# "), color = colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            line.startsWith("## ") -> {
                                Text(line.removePrefix("## "), color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            line.startsWith("### ") -> {
                                Text(line.removePrefix("### "), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            line.startsWith("> ") -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Text(line.removePrefix("> "), color = colors.textSecondary, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                            else -> {
                                // Check for [[wikilinks]]
                                val wikiLinkRegex = Regex("\\[\\[(.*?)\\]\\]")
                                val match = wikiLinkRegex.find(line)
                                if (match != null) {
                                    val linkedTitle = match.groupValues[1]
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(buildMarkdownAnnotated(line.substring(0, match.range.first), colors.textPrimary), color = colors.textPrimary, fontSize = 13.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.primaryContainer)
                                                .border(0.5.dp, colors.primary, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    // Persist whatever is still queued for the open note first.
                            pendingNoteSave?.let { repository.saveNote(it) }
                            pendingNoteSave = null
                            // Navigate to existing note or create one!
                            val existing = notes.find { it.title.equals(linkedTitle, ignoreCase = true) }
                            if (existing != null) {
                                editingNote = existing
                            } else {
                                val newLinked = NoteItem(title = linkedTitle, content = "# $linkedTitle\n\nLinked from [[${note.title}]]")
                                repository.saveNote(newLinked)
                                editingNote = newLinked
                            }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("[[$linkedTitle]]", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(buildMarkdownAnnotated(line.substring(match.range.last + 1), colors.textPrimary), color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                } else {
                                    Text(buildMarkdownAnnotated(line, colors.textPrimary), color = colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        // Move note to a folder / create a new category
        if (showNoteFolderPicker) {
            AlertDialog(
                onDismissRequest = { showNoteFolderPicker = false },
                containerColor = colors.surface,
                title = { Text("File this note", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Folder:", color = colors.textMuted, fontSize = 11.sp)
                        // Unfile option (caters to notes living directly outside folders)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (note.folder.isBlank()) colors.primaryContainer else colors.surfaceElevated)
                                .clickable {
                                    val updated = note.copy(folder = "")
                                    editingNote = updated
                                    pendingNoteSave = null
                                    repository.saveNote(updated)
                                    showNoteFolderPicker = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Unfiled", color = if (note.folder.isBlank()) colors.primary else colors.textPrimary, fontSize = 12.sp)
                        }
                        LazyColumn(modifier = Modifier.height(160.dp)) {
                            items(allFolders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (note.folder.equals(folder, true)) colors.primaryContainer else Color.Transparent)
                                        .clickable {
                                            val updated = note.copy(folder = folder)
                                            editingNote = updated
                                            pendingNoteSave = null
                                            repository.saveNote(updated)
                                            showNoteFolderPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(folder, color = if (note.folder.equals(folder, true)) colors.primary else colors.textPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            label = { Text("New folder name", color = colors.textMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("new_note_folder_input")
                        )
                        Button(
                            onClick = {
                                val name = newFolderNameInput.trim()
                                if (name.isNotBlank()) {
                                    repository.addNoteFolder(name)
                                    val updated = note.copy(folder = name)
                                    editingNote = updated
                                    pendingNoteSave = null
                                    repository.saveNote(updated)
                                    newFolderNameInput = ""
                                    showNoteFolderPicker = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("create_note_folder_button")
                        ) {
                            Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Create & file here", color = Color.White, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNoteFolderPicker = false }) {
                        Text("Done", color = colors.textSecondary)
                    }
                }
            )
        }
        return
    }

    // --- Notes List & Graph Screen ---
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Search
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Notes & Links", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Markdown-first knowledge base with [[wikilinks]]", color = colors.textMuted, fontSize = 11.sp)
                }

                IconButton(
                    onClick = {
                        val newNote = NoteItem(
                            title = "Untitled Note",
                            content = "",
                            folder = selectedFolder?.takeIf { it.isNotBlank() } ?: ""
                        )
                        selectedFolder?.takeIf { it.isNotBlank() }?.let { repository.addNoteFolder(it) }
                        repository.saveNote(newNote)
                        editingNote = newNote
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary)
                        .testTag("create_new_note_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Note", tint = Color.White)
                }
            }
        }

        // Folder chips (categories)
        item(key = "note_folder_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderChip(
                    label = "All",
                    icon = Icons.Default.Apps,
                    selected = selectedFolder == null,
                    tag = "folder_chip_all"
                ) { selectedFolder = null }
                FolderChip(
                    label = "Unfiled",
                    icon = Icons.Default.FolderOff,
                    selected = selectedFolder == "",
                    tag = "folder_chip_unfiled"
                ) { selectedFolder = "" }
                allFolders.forEach { folder ->
                    FolderChip(
                        label = folder,
                        icon = Icons.Default.Folder,
                        selected = selectedFolder == folder,
                        tag = "folder_chip_$folder",
                        onLongClick = { folderPendingDelete = folder }
                    ) { selectedFolder = folder }
                }
                FolderChip(
                    label = "New",
                    icon = Icons.Default.CreateNewFolder,
                    selected = false,
                    tag = "folder_chip_new"
                ) {
                    newFolderNameInput = ""
                    showNewFolderDialog = true
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notes or #tags...", color = colors.textMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = colors.textMuted) },
                modifier = Modifier.fillMaxWidth().testTag("notes_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )
        }

        // Tags Bar
        if (allTags.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTag == null) colors.primaryContainer else colors.surface)
                            .border(1.dp, if (selectedTag == null) colors.primary else colors.border, RoundedCornerShape(10.dp))
                            .clickable { selectedTag = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("All", color = if (selectedTag == null) colors.primary else colors.textSecondary, fontSize = 11.sp)
                    }

                    allTags.forEach { tag ->
                        val isSelected = selectedTag == tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surface)
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                .clickable { selectedTag = if (isSelected) null else tag }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("#$tag", color = if (isSelected) colors.primary else colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Notes List
        if (filteredNotes.isEmpty()) {
            item {
                Text("No matching notes found. Create your first note above.", color = colors.textMuted, fontSize = 12.sp)
            }
        } else {
            items(filteredNotes, key = { it.id }) { noteItem ->
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingNote = noteItem }
                        .testTag("note_item_${noteItem.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = noteItem.title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (noteItem.folder.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = noteItem.folder, color = colors.primary, fontSize = 9.sp)
                                }
                            }
                        }
                        Text(text = formatTimestamp(noteItem.updatedAtMillis), color = colors.textMuted, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stripMarkdownMarkers(noteItem.content).take(120).replace("\n", " "),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 2
                    )

                    if (noteItem.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            noteItem.tags.take(3).forEach { i ->
                                FioBadge(text = "#$i", color = colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // New folder dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = colors.surface,
            title = { Text("New note folder", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    label = { Text("e.g. Mathematics, Fitness, Ideas", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotBlank()) {
                            repository.addNoteFolder(name)
                            selectedFolder = name
                        }
                        newFolderNameInput = ""
                        showNewFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text("Create", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Delete folder confirmation (long-press a folder chip)
    folderPendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Delete folder \"$folder\"?", color = colors.textPrimary) },
            text = {
                Text(
                    "The notes inside are NOT deleted — they simply become Unfiled. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteNoteFolder(folder)
                    if (selectedFolder == folder) selectedFolder = null
                    folderPendingDelete = null
                }) { Text("Delete folder", color = colors.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

/** Category chip for the note-folder bar (tap = filter, long-press = delete). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    tag: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = LocalFioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.textMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) colors.primary else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --- Markdown editing helpers -------------------------------------------------

/** Wraps the selection (or inserts an empty pair at the caret) with Markdown markers; unwraps when already wrapped. */
private fun applyMarkdownMarkers(value: TextFieldValue, open: String, close: String): TextFieldValue {
    val text = value.text
    val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
    val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
    if (selStart == selEnd) {
        val newText = text.substring(0, selStart) + open + close + text.substring(selEnd)
        return TextFieldValue(newText, TextRange(selStart + open.length))
    }
    val isWrapped = selStart >= open.length && selEnd + close.length <= text.length &&
            text.regionMatches(selStart - open.length, open, 0, open.length) &&
            text.regionMatches(selEnd, close, 0, close.length)
    return if (isWrapped) {
        val newText = text.removeRange(selStart - open.length, selEnd + close.length)
        TextFieldValue(newText, TextRange(selStart - open.length, selEnd - open.length))
    } else {
        val newText = text.substring(0, selStart) + open + text.substring(selStart, selEnd) + close + text.substring(selEnd)
        TextFieldValue(newText, TextRange(selStart + open.length, selEnd + open.length))
    }
}

/** Prefixes the lines covered by the selection with [prefix] (first line only when [firstLineOnly]). */
private fun applyMarkdownListPrefix(value: TextFieldValue, prefix: String, firstLineOnly: Boolean = false): TextFieldValue {
    val text = value.text
    val selStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
    val selEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
    val blockStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)) + 1
    val blockEnd = text.indexOf('\n', (selEnd - 1).coerceAtLeast(0)).let { if (it == -1) text.length else it }
    val block = text.substring(blockStart, blockEnd)
    val lines = block.split('\n')
    val styled = when {
        firstLineOnly -> lines.mapIndexed { index, line -> if (index == 0 && line.isNotBlank()) prefix + line else line }
        else -> lines.map { line -> if (line.isBlank()) line else prefix + line }
    }.joinToString("\n")
    val newText = text.substring(0, blockStart) + styled + text.substring(blockEnd)
    // Park the caret just after the first prefixed line so typing continues inside the list/heading.
    val caret = minOf(blockStart + prefix.length, newText.length)
    return TextFieldValue(newText, TextRange(caret))
}

@Composable
private fun FormatChip(
    label: String,
    tag: String,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    fontSize: Int = 13,
    onClick: () -> Unit
) {
    val colors = LocalFioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration
        )
    }
}
