package com.example.fio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.fio.data.transfer.DiscoveredDevice
import com.example.fio.data.transfer.FIO_TRANSFER_PORT
import com.example.fio.data.transfer.FioTransferClient
import com.example.fio.data.transfer.TransferServer
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Device-to-Device transfer dialogs: auto-discovery on the local Wi-Fi (no URLs),
 * full-profile clone (Send / Receive) and the "Sync" mode that merges progress
 * newest-wins instead of replacing data.
 */

@Composable
internal fun SendTransferDialog(repository: FioRepository, onDismiss: () -> Unit) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var logs by remember { mutableStateOf(listOf("Tap Start when both devices are on the same Wi-Fi.")) }
    var serverActive by remember { mutableStateOf(false) }
    var packing by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }

    val server = remember {
        TransferServer(context.applicationContext, repository) { message ->
            if (message.startsWith("Packing")) packing = true
            if (message.startsWith("Bundle ready")) packing = false
            if (message.startsWith("Ready to be discovered") || message.startsWith("Listening on")) {
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
                "Share this profile",
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
                    "Everything is included — courses, books & PDFs, notes with images, chats, gallery, flashcards, stats, calendar, settings and your AI key.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                if (!serverActive) {
                    Text(
                        "Tap Start, then on the other device open Fio → Settings → Device to Device Transfer → Sync Progress or Receive. It will find this device automatically.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
                if (packing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Packing profile (books & photos)…", color = colors.primary, fontSize = 12.sp)
                    }
                }
                if (serverActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Waiting for the other device — it appears in their list automatically.",
                            color = colors.success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Keep this screen open until the transfer finishes.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    TextButton(
                        onClick = { showAddress = !showAddress },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            if (showAddress) "Hide manual address" else "Device not appearing? Show address",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (showAddress) {
                        Text(
                            server.address(),
                            color = colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(server.address()))
                                }
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            "Tap to copy, then paste it in the other device's manual field.",
                            color = colors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    logs.forEach { line ->
                        Text(line, color = colors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (serverActive) {
                TextButton(
                    onClick = {
                        server.stop()
                        serverActive = false
                        packing = false
                    }
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
                    Text("Start", color = Color.White, fontSize = 12.sp)
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
    var mergeMode by remember { mutableStateOf(defaultMerge) }
    var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    var scanning by remember { mutableStateOf(false) }
    // scanning | found | connecting | downloading | restoring | done | error
    var phase by remember { mutableStateOf("scanning") }
    var receivedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var summaryLine by remember { mutableStateOf("") }
    var offerBase by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val busy = phase == "connecting" || phase == "downloading" || phase == "restoring"

    fun scan() {
        if (scanning || busy) return
        scanning = true
        devices = emptyList()
        phase = "scanning"
        scope.launch {
            val found = mutableListOf<DiscoveredDevice>()
            withContext(Dispatchers.IO) {
                FioTransferClient.discoverDevices(4500) { device ->
                    if (found.none { it.ip == device.ip && it.port == device.port }) {
                        found += device
                        devices = found.toList()
                    }
                }
            }
            scanning = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    fun pick(device: DiscoveredDevice) {
        selectedName = device.displayName
        offerBase = device.address
        summaryLine = device.summary.line()
        phase = "found"
    }

    fun startTransfer(merge: Boolean) {
        phase = "downloading"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    FioTransferClient.downloadAndRestore(
                        context.applicationContext,
                        repository,
                        offerBase,
                        merge = merge
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
                    summaryLine = "${if (merge) "Sync" else "Clone"} complete — ${it.line()}"
                },
                onFailure = {
                    phase = "error"
                    message = it.message ?: "Transfer failed"
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.surface,
        title = {
            Text(
                if (mergeMode) "Sync with another device" else "Receive a profile",
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
                    "Both devices must be on the same Wi-Fi. On the sending device: Settings → Device to Device Transfer → Clone (Send) → Start. It appears below automatically.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

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
                        hint = "Replaces this device's Fio data",
                        selected = !mergeMode,
                        enabled = !busy
                    ) { mergeMode = false }
                }

                when (phase) {
                    "scanning" -> {
                        if (devices.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (scanning) CircularProgressIndicator(
                                    color = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                ) else Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (scanning) "Looking for nearby devices…" else "No devices found yet.",
                                    color = colors.primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceElevated)
                                    .clickable(enabled = !busy) { pick(device) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        device.displayName,
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        device.summary.line(),
                                        color = colors.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text("TAP", color = colors.primary, fontSize = 10.sp)
                            }
                        }
                        if (devices.isNotEmpty() || !scanning) {
                            TextButton(
                                onClick = { scan() },
                                enabled = !scanning && !busy,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan again", color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    "found" -> {
                        Text(
                            "Found: $selectedName",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(summaryLine, color = colors.textSecondary, fontSize = 12.sp)
                        Text(
                            if (mergeMode)
                                "Sync merges the two profiles: the newest copy of each item wins, so the device you've used most keeps its progress and nothing here is deleted."
                            else
                                "Cloning REPLACES this device's Fio data with the sender's full profile.",
                            color = if (mergeMode) colors.success else colors.warning,
                            fontSize = 11.sp
                        )
                    }
                    "connecting" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connecting…", color = colors.primary, fontSize = 12.sp)
                        }
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
                            "Downloading… ${receivedBytes / 1024} KB" +
                                (if (totalBytes > 0) " / ${totalBytes / 1024} KB" else ""),
                            color = colors.textMuted,
                            fontSize = 11.sp
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
                        Text(
                            "✓ Done!",
                            color = colors.success,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(summaryLine, color = colors.textSecondary, fontSize = 12.sp)
                        TextButton(
                            onClick = { scan() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("Scan for another device", color = colors.primary, fontSize = 11.sp)
                        }
                    }
                    "error" -> {
                        Text(message, color = colors.error, fontSize = 12.sp)
                        TextButton(
                            onClick = { phase = "scanning"; scan() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("Try again", color = colors.primary, fontSize = 11.sp)
                        }
                    }
                }

                // Manual fallback — never required, but there if discovery is blocked.
                if (phase == "scanning" || phase == "error") {
                    TextButton(
                        onClick = { showManual = !showManual },
                        enabled = !busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            if (showManual) "Hide manual address" else "Device not showing? Enter an address",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (showManual) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it.trim() },
                            enabled = !busy,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "e.g. http://192.168.1.34:$FIO_TRANSFER_PORT",
                                    color = colors.textMuted,
                                    fontSize = 12.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
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
                                            selectedName = "Manual address"
                                            phase = "found"
                                        },
                                        onFailure = {
                                            phase = "error"
                                            message = it.message ?: "Could not connect"
                                        }
                                    )
                                }
                            },
                            enabled = !busy && address.isNotBlank(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("Connect", color = colors.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                "found" -> {
                    Button(
                        onClick = { startTransfer(mergeMode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mergeMode) colors.success else colors.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (mergeMode) "Sync now" else "Clone now",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                "done" -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Finish", color = Color.White, fontSize = 12.sp)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) {
                Text(
                    if (busy) "…" else "Close",
                    color = if (busy) colors.textMuted else colors.textSecondary
                )
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
