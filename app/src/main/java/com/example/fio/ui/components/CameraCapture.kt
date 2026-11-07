package com.example.fio.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.fio.ui.theme.LocalFioColors
import java.io.File

/**
 * System-camera capture without any permission: the photo is written into
 * cache/camera/ and shared with the camera app through the app's FileProvider
 * (declared in the manifest). Every screen that can attach an image uses this,
 * so "take a photo" works everywhere the gallery does.
 */
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { ok -> if (ok) pendingUri?.let(onCaptured) }
    )
    return {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingUri = uri
            launcher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Standard "attach image" button: tap opens a small menu offering the system
 * photo picker or the camera. Used by chat, notes, tutor, dashboard and
 * gallery screens so every image entry point behaves identically.
 */
@Composable
fun AttachImageButton(
    hasImage: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFioColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Add photo",
                tint = if (hasImage) colors.primary else colors.textMuted
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surfaceElevated
        ) {
            DropdownMenuItem(
                text = { Text("Choose from gallery", color = colors.textPrimary, fontSize = 13.sp) },
                onClick = { expanded = false; onGallery() }
            )
            DropdownMenuItem(
                text = { Text("Take a photo", color = colors.textPrimary, fontSize = 13.sp) },
                onClick = { expanded = false; onCamera() }
            )
        }
    }
}
