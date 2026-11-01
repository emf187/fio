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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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

    var isImporting by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<GalleryImage?>(null) }
    var actionTarget by remember { mutableStateOf<GalleryImage?>(null) }
    var renameTarget by remember { mutableStateOf<GalleryImage?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyImageToGallery(context, uri) }
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
                Text("Gallery", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = when (images.size) {
                        0 -> "Store pictures on this device"
                        1 -> "1 photo stored on this device"
                        else -> "${images.size} photos stored on this device"
                    },
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !isImporting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adding...", color = Color.White, fontSize = 11.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add photos", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
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
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryThumb(
                        file = repository.galleryFile(image),
                        onClick = { viewerIndex = images.indexOf(image).coerceAtLeast(0) },
                        onLongClick = { actionTarget = image }
                    )
                }
            }
        }
    }

    // Full-screen viewer
    viewerIndex?.let { startIndex ->
        GalleryViewer(
            images = images,
            repository = repository,
            startIndex = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
            onClose = { viewerIndex = null },
            onDeleteRequest = { image -> pendingDelete = image },
            onRenameRequest = { image ->
                renameTarget = image
                renameInput = image.displayName
            }
        )
    }

    // Long-press options for a photo: name it or remove it
    actionTarget?.let { image ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = colors.surface,
            title = { Text("Photo options", color = colors.textPrimary) },
            text = {
                Text(
                    text = "Give this photo a name, or remove it from the gallery.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    renameTarget = image
                    renameInput = image.displayName
                }) {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Name this photo", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionTarget = null
                    pendingDelete = image
                }) {
                    Text("Remove", color = colors.error, fontWeight = FontWeight.SemiBold)
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
                    if (images.size <= 1) viewerIndex = null
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
}

@Composable
private fun GalleryThumb(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = LocalFioColors.current
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(file, 400) }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
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
