package com.example.fio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.transfer.FIO_TRANSFER_PORT
import com.example.fio.data.transfer.FioTransferClient
import com.example.fio.data.transfer.FioWifi
import com.example.fio.data.transfer.TransferServer
import com.example.fio.data.transfer.TransferSummary
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Device-to-Device transfer dialogs: the full-profile clone (Send / Receive)
 * plus the one-way "Sync" mode that merges progress newest-wins instead of
 * replacing data.
 */

@Composable
internal fun SendTransferDialog(repository: FioRepository, onDismiss: () -> Unit) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var logs by remember { mutableStateOf(listOf("Tap Start when both devices are on the same Wi-Fi.")) }
    var serverActive by remember { mutableStateOf(false) }
    var packing by remember { mutableStateOf(false) }

    val server = remember {
        TransferServer(context.applicationContext, repository) { message ->
            if (message.startsWith("Packing")) packing = true
            if (message.startsWith("Bundle ready")) packing = false
            if (message.startsWith("Listening on")) {
                packing = false
                serverActive = true
            }
            if (message.startsWith("Could not open")) {
                packing = false
                serverActive = false
            }
            logs = (logs + message).takeLast(7)
        }
    }
    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                "Clone this profile to another device",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Everything is included — courses, books & PDFs, notes with images, chats, gallery photos, flashcards, tutor sessions, stats, calendar, timetable + alarms, settings and your AI key.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                if (packing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Packing profile (books & photos)…", color = colors.primary, fontSize = 12.sp)
                    }
                }
                if (serverActive) {
                    val address = server.address()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(address))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = address,
                            color = colors.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("TAP TO COPY", color = colors.textMuted, fontSize = 10.sp)
                    }
                    Text(
                        "On the other device: Fio → Settings → Device to Device Transfer → Receive (or Sync) → type that exact address. Keep this screen open until it finishes.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    logs.forEach { line ->
                        Text(line, color = colors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (serverActive) {
                OutlinedButton(
                    onClick = { server.stop(); serverActive = false; packing = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Stop sharing", color = colors.error, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = {
                        serverActive = true // optimistic; logs then confirm readiness
                        packing = true
                        server.start()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Start transfer", color = Color.White, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.textSecondary)
            }
        }
    )
}

@Composable
internal fun ReceiveTransferDialog(
    repository: FioRepository,
    defaultMerge: Boolean,
    onDismiss: () -> Unit
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var mergeMode by remember { mutableStateOf(defaultMerge) }
    // idle | connecting | found | downloading | restoring | done | error
    var phase by remember { mutableStateOf("idle") }
    var receivedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var summaryLine by remember { mutableStateOf("") }
    var offerBase by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val busy = phase == "connecting" || phase == "downloading" || phase == "restoring"

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.surface,
        title = {
            Text(
                if (mergeMode) "Sync progress" else "Receive a profile clone",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Make sure BOTH devices are on the same Wi-Fi network. On the sending device open Fio → Settings → Device to Device Transfer → Clone (Send), then type its address here.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    enabled = !busy,
                    singleLine = true,
                    placeholder = {
                        Text("e.g. http://192.168.1.34:$FIO_TRANSFER_PORT", color = colors.textMuted, fontSize = 12.sp)
                    },
                    label = { Text("Address shown on the other device", color = colors.textMuted, fontSize = 11.sp) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Mode toggle
                Row(modifier = Modifier.fillMaxWidth()) {
                    SelectableTile(
                        modifier = Modifier.weight(1f),
                        label = "Sync (merge)",
                        hint = "Progress merges — the used-most device wins",
                        selected = mergeMode,
                        enabled = !busy
                    ) { mergeMode = true }
                    Spacer(modifier = Modifier.width(8.dp))
                    SelectableTile(
                        modifier = Modifier.weight(1f),
                        label = "Clone (replace)",
                        hint = "Wipes this device's Fio data with the sent one",
                        selected = !mergeMode,
                        enabled = !busy
                    ) { mergeMode = false }
                }

                when (phase) {
                    "connecting" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connecting…", color = colors.primary, fontSize = 12.sp)
                        }
                    }
                    "found" -> {
                        Text("Sender's profile: $summaryLine", color = colors.textSecondary, fontSize = 12.sp)
                        Text(
                            if (mergeMode)
                                "Ready to SYNC: records are merged — newer copies of each item win, the device you've used most keeps its progress, nothing from this device is deleted."
                            else
                                "Ready to CLONE: this device's Fio data will be REPLACED by the sender's full profile.",
                            color = if (mergeMode) colors.success else colors.warning,
                            fontSize = 11.sp
                        )
                    }
                    "downloading" -> {
                        LinearProgressIndicator(
                            progress = {
                                if (totalBytes > 0) receivedBytes.toFloat() / totalBytes.toFloat() else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.primary,
                            trackColor = colors.surfaceElevated
                        )
                        Text(
                            "Downloading… ${receivedBytes / 1024} KB" + (if (totalBytes > 0) " / ${totalBytes / 1024} KB" else ""),
                            color = colors.textMuted, fontSize = 11.sp
                        )
                    }
                    "restoring" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Restoring profile…", color = colors.primary, fontSize = 12.sp)
                        }
                    }
                    "done" -> {
                        Text("✓ Done! Profile received.", color = colors.success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(summaryLine, color = colors.textSecondary, fontSize = 12.sp)
                    }
                    "error" -> {
                        Text(message, color = colors.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            val showGo = phase != "downloading" && phase != "restoring" && phase != "connecting" &&
                (phase == "idle" || phase == "done" || (phase == "found" && !mergeMode))
            when {
                phase == "found" && mergeMode -> {
                    Button(
                        onClick = {
                            phase = "downloading"
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        FioTransferClient.downloadAndRestore(
                                            context.applicationContext,
                                            repository,
                                            offerBase,
                                            merge = true
                                        ) { ph, rec, tot ->
                                            receivedBytes = rec
                                            totalBytes = tot
                                            if (ph == "restoring") phase = "restoring"
                                        }
                                    }
                                }
                                result.fold(
                                    onSuccess = {
                                        phase = "done"
                                        summaryLine = "Sync complete — ${it.line()}"
                                    },
                                    onFailure = {
                                        phase = "error"
                                        message = it.message ?: "Sync failed"
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.success),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sync now", color = Color.White, fontSize = 12.sp)
                    }
                }
                phase == "found" -> {
                    Button(
                        onClick = {
                            phase = "downloading"
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        FioTransferClient.downloadAndRestore(
                                            context.applicationContext,
                                            repository,
                                            offerBase,
                                            merge = false
                                        ) { ph, rec, tot ->
                                            receivedBytes = rec
                                            totalBytes = tot
                                            if (ph == "restoring") phase = "restoring"
                                        }
                                    }
                                }
                                result.fold(
                                    onSuccess = {
                                        phase = "done"
                                        summaryLine = "Clone complete — ${it.line()}"
                                    },
                                    onFailure = {
                                        phase = "error"
                                        message = it.message ?: "Transfer failed"
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clone now", color = Color.White, fontSize = 12.sp)
                    }
                }
                showGo -> {
                    Button(
                        onClick = {
                            phase = "connecting"
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val probed = FioTransferClient.probe(address)
                                        offerBase = probed.first
                                        probed.second.line()
                                    }
                                }
                                result.fold(
                                    onSuccess = {
                                        summaryLine = it
                                        phase = "found"
                                    },
                                    onFailure = {
                                        phase = "error"
                                        message = it.message ?: "Could not connect"
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Connect", color = Color.White, fontSize = 12.sp)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) {
                Text(if (busy) "…" else "Close", color = if (busy) colors.textMuted else colors.textSecondary)
            }
        }
    )
}

@Composable
private fun SelectableTile(
    modifier: Modifier = Modifier,
    label: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalFioColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceElevated)
            .padding(10.dp)
    ) {
        Text(
            text = (if (selected) "● " else "○ ") + label,
            color = if (selected) colors.primary else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(hint, color = colors.textMuted, fontSize = 10.sp)
    }
}
