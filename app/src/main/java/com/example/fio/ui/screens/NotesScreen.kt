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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notes by repository.notes.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    // Active Note Editor state
    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var isPreviewMode by remember { mutableStateOf(false) }

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

    val filteredNotes = remember(notes, searchQuery, selectedTag) {
        notes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            val matchesTag = selectedTag == null || note.tags.contains(selectedTag)
            matchesSearch && matchesTag
        }
    }

    if (editingNote != null) {
        val note = editingNote!!
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
                    repository.saveNote(updated)
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

            FioDivider(modifier = Modifier.padding(vertical = 6.dp))

            if (!isPreviewMode) {
                // Markdown Raw Editor
                OutlinedTextField(
                    value = note.content,
                    onValueChange = { newContent ->
                        // Automatically parse #tags
                        val extractedTags = Regex("#([a-zA-Z0-9_-]+)").findAll(newContent).map { it.groupValues[1] }.toList()
                        val updated = note.copy(content = newContent, tags = extractedTags)
                        editingNote = updated
                        repository.saveNote(updated)
                    },
                    placeholder = { Text("Write in Obsidian Markdown...\nUse [[links]] to connect notes\nUse #tags to organize\nUse **bold** and # headers", color = colors.textMuted) },
                    modifier = Modifier.fillMaxSize().testTag("note_content_editor"),
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
                                        .clip(RoundedCornerShape(4.dp))
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
                                        Text(line.substring(0, match.range.first), color = colors.textPrimary, fontSize = 13.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.primaryContainer)
                                                .border(0.5.dp, colors.primary, RoundedCornerShape(4.dp))
                                                .clickable {
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
                                        Text(line.substring(match.range.last + 1), color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                } else {
                                    Text(line, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
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
                    Text("Obsidian Notes & Links", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Markdown-first knowledge base with [[wikilinks]]", color = colors.textMuted, fontSize = 11.sp)
                }

                IconButton(
                    onClick = {
                        val newNote = NoteItem(title = "Untitled Note", content = "")
                        repository.saveNote(newNote)
                        editingNote = newNote
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary)
                        .testTag("create_new_note_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Note", tint = Color.White)
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selectedTag == null) colors.primaryContainer else colors.surface)
                            .border(1.dp, if (selectedTag == null) colors.primary else colors.border, RoundedCornerShape(4.dp))
                            .clickable { selectedTag = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("All", color = if (selectedTag == null) colors.primary else colors.textSecondary, fontSize = 11.sp)
                    }

                    allTags.forEach { tag ->
                        val isSelected = selectedTag == tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surface)
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(4.dp))
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
            items(filteredNotes) { noteItem ->
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
                        Text(text = noteItem.title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = formatTimestamp(noteItem.updatedAtMillis), color = colors.textMuted, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = noteItem.content.take(120).replace("\n", " "),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 2
                    )

                    if (noteItem.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            noteItem.tags.take(3).forEach { t ->
                                FioBadge(text = "#$t", color = colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
