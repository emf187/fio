package com.example.fio.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.max

/**
 * Related-image strip shown below Wingman / Tutor replies: 1–2 thumbnails,
 * tap to open a full-screen viewer. Bitmaps are decoded once and remembered,
 * with a coarse inSampleSize so weak devices stay smooth.
 */
@Composable
fun AiImageStrip(
    paths: List<String>,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit
) {
    if (paths.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        paths.take(2).forEach { path ->
            val bitmap = remember(path) { decodePreview(path) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Related image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(path) }
                )
            }
        }
    }
}

/** Full-screen tap-to-close viewer for a downloaded related image. */
@Composable
fun AiImageViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() }
                .padding(16.dp)
        ) {
            val bitmap = remember(path) { decodePreview(path, fullQuality = true) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Related image full view",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Decodes at most 400px wide (strip) / larger for the viewer, sampling down cheaply. */
private fun decodePreview(path: String, fullQuality: Boolean = false): Bitmap? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val targetWidth = if (fullQuality) 1400 else 400
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > targetWidth) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}
