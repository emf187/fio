@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fio.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fio.data.model.GalleryImage
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.AttachImageButton
import com.example.fio.ui.components.FioSelectionBar
import com.example.fio.ui.components.rememberCameraCapture
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Stored pictures: import from the system photo picker, browse, view, delete. */
@Composable
fun GalleryScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images by repository.galleryImages.collectAsState()
    val folders by repository.galleryFolders.collectAsState()

    var isImporting by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<GalleryImage?>(null) }
    var renameTarget by remember { mutableStateOf<GalleryImage?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }

    // Multi-select: long-press starts selection, tap toggles while active.
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    // Folder browsing: null = All photos, "" = Unfiled, otherwise a folder name.
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    var pendingFolderDelete by remember { mutableStateOf<String?>(null) }

    val folderNames = remember(folders, images) { repository.galleryFolderNames(images) }
    val visibleImages = remember(images, selectedFolder) {
        when (selectedFolder) {
            null -> images
            else -> images.filter { it.folder == selectedFolder }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        val targetFolder = selectedFolder ?: ""
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyImageToGallery(context, uri) }
                    .map { if (targetFolder.isEmpty()) it else it.copy(folder = targetFolder) }
            }
            repository.addGalleryImages(imported)
            isImporting = false
            Toast.makeText(
                context,
                if (imported.isEmpty()) "Could not add those photos" else "Added ${imported.size} photo(s)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val captureGalleryPhoto = rememberCameraCapture { uri ->
        isImporting = true
        val targetFolder = selectedFolder ?: ""
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                listOfNotNull(copyImageToGallery(context, uri))
                    .map { if (targetFolder.isEmpty()) it else it.copy(folder = targetFolder) }
            }
            repository.addGalleryImages(imported)
            isImporting = false
            Toast.makeText(
                context,
                if (imported.isEmpty()) "Could not add that photo" else "Added 1 photo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    BackHandler(enabled = viewerIndex != null) { viewerIndex = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Gallery", style = com.example.fio.ui.theme.FioType.display, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when (images.size) {
                        0 -> "Pictures you save for studying live here."
                        1 -> "1 photo, stored on this device."
                        else -> "${images.size} photos, stored on this device."
                    },
                    style = com.example.fio.ui.theme.FioType.small,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box {
            com.example.fio.ui.components.FioPrimaryButton(
                text = if (isImporting) "Adding…" else "Add",
                icon = if (isImporting) null else Icons.Default.AddPhotoAlternate,
                enabled = !isImporting,
                onClick = { showAddMenu = true }
            )
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
                containerColor = colors.surfaceElevated
            ) {
                DropdownMenuItem(
                    text = { Text("Choose from gallery", color = colors.textPrimary, fontSize = 13.sp) },
                    onClick = {
                        showAddMenu = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Take a photo", color = colors.textPrimary, fontSize = 13.sp) },
                    onClick = {
                        showAddMenu = false
                        captureGalleryPhoto()
                    }
                )
            }
            }
        }

        // Selection toolbar (multi-select): move or delete many photos at once.
        if (selectedIds.isNotEmpty()) {
            FioSelectionBar(
                count = selectedIds.size,
                onClear = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = visibleImages.map { it.id }.toSet() },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                TextButton(onClick = { showMoveDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Move", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { confirmBulkDelete = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = colors.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Folder filter chips: All / Unfiled / each user folder, plus create-folder.
        // Always shown so a folder can be created even before the first photo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (images.isNotEmpty()) {
                    GalleryFolderChip("All", images.size, selectedFolder == null, onClick = { selectedFolder = null })
                    GalleryFolderChip("Unfiled", images.count { it.folder.isBlank() }, selectedFolder == "", onClick = { selectedFolder = "" })
                }
                folderNames.forEach { name ->
                    GalleryFolderChip(
                        label = name,
                        count = images.count { it.folder == name },
                        selected = selectedFolder == name,
                        onClick = { selectedFolder = name },
                        onLongClick = { pendingFolderDelete = name }
                    )
                }
                if (images.isEmpty() && folderNames.isEmpty()) {
                    Text(
                        text = "Make a folder for each subject or topic.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    newFolderInput = ""
                    showFolderDialog = true
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New folder",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (images.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No pictures yet.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap \"Add photos\" to store pictures for your study notes.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else if (visibleImages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if ((selectedFolder ?: "").isBlank()) "Nothing unfiled." else "This folder is empty.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Long-press a photo to move it here, or add new ones.",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(visibleImages, key = { it.id }) { image ->
                    val selected = image.id in selectedIds
                    GalleryThumb(
                        file = repository.galleryFile(image),
                        selected = selected,
                        onClick = {
                            val active = selectedIds
                            if (active.isNotEmpty()) {
                                selectedIds = if (image.id in active) active - image.id else active + image.id
                            } else {
                                viewerIndex = visibleImages.indexOf(image).coerceAtLeast(0)
                            }
                        },
                        onLongClick = {
                            val active = selectedIds
                            selectedIds = if (image.id in active) active - image.id else active + image.id
                        }
                    )
                }
            }
        }
    }

    // Full-screen viewer
    if (viewerIndex != null && visibleImages.isNotEmpty()) {
        val startIndex = viewerIndex ?: 0
        GalleryViewer(
            images = visibleImages,
            repository = repository,
            startIndex = startIndex.coerceIn(0, visibleImages.size - 1),
            onClose = { viewerIndex = null },
            onDeleteRequest = { image -> pendingDelete = image },
            onRenameRequest = { image ->
                renameTarget = image
                renameInput = image.displayName
            }
        )
    }

    // Bulk move (multi-select) — also used for a single photo.
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            containerColor = colors.surface,
            title = { Text("Move ${selectedIds.size} photo(s)", color = colors.textPrimary) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("Choose a folder:", color = colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            repository.moveGalleryImages(selectedIds, "")
                            selectedIds = emptySet()
                            showMoveDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unfiled", color = colors.textPrimary, fontSize = 12.sp)
                    }
                    folderNames.forEach { name ->
                        TextButton(
                            onClick = {
                                repository.moveGalleryImages(selectedIds, name)
                                selectedIds = emptySet()
                                showMoveDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, color = colors.textPrimary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Bulk delete confirmation (multi-select)
    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete ${selectedIds.size} photo(s)?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "The selected photos will be removed from your gallery. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeGalleryImages(selectedIds)
                    selectedIds = emptySet()
                    confirmBulkDelete = false
                    viewerIndex = null
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Rename dialog
    renameTarget?.let { image ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = colors.surface,
            title = { Text("Name this photo", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it.take(60) },
                    placeholder = { Text("Photo name", color = colors.textMuted) },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.renameGalleryImage(image.id, renameInput)
                    renameTarget = null
                }) {
                    Text("Save", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    pendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Remove this photo?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "\u201c${image.displayName.ifBlank { "Photo" }}\u201d will be removed from your gallery. This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeGalleryImage(image.id)
                    pendingDelete = null
                    if (visibleImages.size <= 1) viewerIndex = null
                }) {
                    Text("Remove", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Create folder (and manage: delete existing ones)
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            containerColor = colors.surface,
            title = { Text("New folder", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFolderInput,
                        onValueChange = { newFolderInput = it.take(40) },
                        placeholder = { Text("Folder name", color = colors.textMuted) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    if (folderNames.isNotEmpty()) {
                        Text(
                            text = "Existing folders — long-press a folder chip, or tap the bin to delete it (photos move to Unfiled):",
                            color = colors.textMuted,
                            fontSize = 10.sp
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                        ) {
                            folderNames.forEach { name ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$name (${images.count { it.folder == name }})",
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { pendingFolderDelete = name },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete folder",
                                            tint = colors.error,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (repository.addGalleryFolder(newFolderInput)) {
                        newFolderInput = ""
                        showFolderDialog = false
                    }
                }) {
                    Text("Create", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Delete folder confirmation
    pendingFolderDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingFolderDelete = null },
            containerColor = colors.surface,
            title = { Text("Delete \u201c$name\u201d?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "Photos in this folder move back to Unfiled. The photos themselves are not deleted.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteGalleryFolder(name)
                    if (selectedFolder == name) selectedFolder = null
                    pendingFolderDelete = null
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFolderDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

/** Folder chip for the gallery filter row: label + photo count, tap to filter, long-press to delete. */
@Composable
private fun GalleryFolderChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = LocalFioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceElevated)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (selected) colors.primary else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                color = if (selected) colors.primary else colors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun GalleryThumb(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false
) {
    val colors = LocalFioColors.current
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(file, 400) }
    }
    // pointerInput only restarts when its key changes, so the gesture block
    // must call the *latest* lambdas — otherwise taps keep using the state
    // captured on first composition (e.g. can't unselect a selected photo).
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) colors.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick() }
                )
            }
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryViewer(
    images: List<GalleryImage>,
    repository: FioRepository,
    startIndex: Int,
    onClose: () -> Unit,
    onDeleteRequest: (GalleryImage) -> Unit,
    onRenameRequest: (GalleryImage) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    // Pages report their zoom state; page swiping pauses while the open page is zoomed.
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    var chromeVisible by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = zoomedPages[pagerState.currentPage] != true
            ) { page ->
                val image = images.getOrNull(page) ?: return@HorizontalPager
                ZoomableGalleryImage(
                    file = repository.galleryFile(image),
                    displayName = image.displayName.ifBlank { "Photo" },
                    onZoomChanged = { zoomed -> zoomedPages[page] = zoomed },
                    onToggleChrome = { chromeVisible = !chromeVisible }
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) { detectTapGestures { } } // keep taps out of the pager
                    ) {
                        val current = images.getOrNull(pagerState.currentPage)
                        Text(
                            text = current?.displayName?.ifBlank { "Photo" } ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = current?.let {
                                "${formatGalleryDate(it.addedAtMillis)}  \u00b7  ${pagerState.currentPage + 1} / ${images.size}"
                            } ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                    val current = images.getOrNull(pagerState.currentPage)
                    if (current != null) {
                        IconButton(onClick = { onRenameRequest(current) }) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "Name this photo",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { onDeleteRequest(current) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete photo",
                                tint = Color(0xFFF87171)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A full-screen gallery image with pinch-to-zoom, double-tap zoom and pan.
 * Reports when the image is zoomed so page swiping can pause.
 */
@Composable
private fun ZoomableGalleryImage(
    file: File,
    displayName: String,
    onZoomChanged: (Boolean) -> Unit,
    onToggleChrome: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(file, 2400)
        }
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.01f
    LaunchedEffect(isZoomed) { onZoomChanged(isZoomed) }

    // Keeps the (scaled) image inside the viewport: never slides fully off-screen.
    fun clampOffset(newOffset: Offset): Offset {
        val bmp = bitmap ?: return Offset.Zero
        val cw = containerSize.width.toFloat()
        val ch = containerSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || bmp.width <= 0 || bmp.height <= 0) return Offset.Zero
        val fit = minOf(cw / bmp.width, ch / bmp.height)
        val drawnW = bmp.width * fit * scale
        val drawnH = bmp.height * fit * scale
        val maxX = maxOf(0f, (drawnW - cw) / 2f)
        val maxY = maxOf(0f, (drawnH - ch) / 2f)
        return Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
    }

    val loaded = bitmap
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Image(
        bitmap = loaded.asImageBitmap(),
        contentDescription = displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(file.absolutePath) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pinchStarted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break
                        if (pressedCount >= 2) {
                            pinchStarted = true
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                scale = if (scale * zoomChange < 1.04f) 1f else (scale * zoomChange).coerceIn(1f, 8f)
                                if (scale == 1f) offset = Offset.Zero
                            }
                            if (scale > 1f) {
                                val pan = event.calculatePan() * scale
                                offset = clampOffset(offset + pan)
                            }
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (pinchStarted || scale > 1f) {
                            if (scale > 1f) {
                                val pan = event.calculatePan() * scale
                                offset = clampOffset(offset + pan)
                            }
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { position ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val newScale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val dX = position.x - centerX
                            val dY = position.y - centerY
                            scale = newScale
                            offset = clampOffset(Offset(-dX * (newScale - 1f), -dY * (newScale - 1f)))
                        }
                    }
                )
            }
    )
}

private fun formatGalleryDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

/** Copies a picked image into filesDir/fio_gallery and returns its metadata. */
private fun copyImageToGallery(context: Context, uri: Uri): GalleryImage? {
    return try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("bmp") -> "bmp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            else -> "jpg"
        }
        val displayName = queryDisplayName(context, uri)
            ?.substringBeforeLast('.')
            ?.ifBlank { null }
            ?: "Photo"
        val dir = File(context.filesDir, "fio_gallery").apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(dir, fileName)

        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > 40L * 1024 * 1024) {
                        output.close()
                        target.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: return null

        if (target.length() == 0L) {
            target.delete()
            return null
        }

        GalleryImage(
            fileName = fileName,
            displayName = displayName,
            mimeType = mime,
            addedAtMillis = System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
}.getOrNull()

/** Decodes a file to a bitmap capped at [maxDimension] px (memory-safe for grids/viewer). */
private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    return try {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension &&
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (e: Exception) {
        null
    }
}
