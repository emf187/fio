package com.example.fio.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.fio.data.model.AppSettings
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.transfer.FIO_TRANSFER_PORT
import com.example.fio.data.transfer.FioTransferClient
import com.example.fio.data.transfer.FioWifi
import com.example.fio.data.transfer.TransferServer
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.FioPrimaryButton
import com.example.fio.ui.components.FioRow
import com.example.fio.ui.components.FioScreenHeader
import com.example.fio.ui.components.FioSecondaryButton
import com.example.fio.ui.components.FioSectionHeader
import com.example.fio.ui.theme.FioType
import com.example.fio.ui.theme.LocalFioColors
import com.example.fio.ui.theme.accentSwatch
import com.example.fio.ui.theme.themePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by repository.settings.collectAsState()
    val profileName by repository.profileName.collectAsState()
    val chatHistory by repository.chatHistory.collectAsState()

    // Local mutable state
    var profileNameInput by remember(profileName) { mutableStateOf(profileName) }
    var providerChoice by remember { mutableStateOf(settings.aiProvider) }
    var apiKeyInput by remember { mutableStateOf(settings.aiApiKey) }
    var baseUrlInput by remember { mutableStateOf(settings.aiBaseUrl) }
    var modelNameInput by remember { mutableStateOf(settings.aiModelName) }

    var gracePeriodInput by remember { mutableStateOf(settings.gracePeriodSeconds.toString()) }
    var breakFreqInput by remember { mutableStateOf(settings.breakFrequencyMinutes.toString()) }
    var breakDurInput by remember { mutableStateOf(settings.breakDurationMinutes.toString()) }

    var sleepTargetInput by remember { mutableStateOf(settings.sleepTargetHours.toString()) }
    var mealTargetInput by remember { mutableStateOf(settings.mealTargetCount.toString()) }

    var importExportDialogState by remember { mutableStateOf<String?>(null) } // "EXPORT" or "IMPORT"
    var backupJsonInput by remember { mutableStateOf("") }

    // Device-to-Device transfer (complete clone over Wi-Fi). null = closed,
    // true = open in Sync (merge) mode, false = open in Clone (replace) mode.
    var transferSendOpen by remember { mutableStateOf(false) }
    var transferReceiveMerge by remember { mutableStateOf<Boolean?>(null) }

    // Export / Import Dialog
    importExportDialogState?.let { mode ->
        AlertDialog(
            onDismissRequest = { importExportDialogState = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = if (mode == "EXPORT") "Export Data (as File / Message)" else "Import Data Backup",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (mode == "EXPORT")
                            "Console backup of all data as text — convenient for a quick share. For a COMPLETE one-tap clone (with the actual PDF/book/image files) use Device to Device Transfer below."
                        else "Paste the JSON backup string to restore all records, books, documents, and progress:",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = if (mode == "EXPORT") repository.exportAllToJson() else backupJsonInput,
                        onValueChange = { if (mode == "IMPORT") backupJsonInput = it },
                        readOnly = mode == "EXPORT",
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    if (mode == "EXPORT") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val json = repository.exportAllToJson()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Fio_Profile_Backup.json")
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Fio Profile via Wi-Fi / Quick Share"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wi-Fi Quick Share", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val json = repository.exportAllToJson()
                                    clipboardManager.setText(AnnotatedString(json))
                                    Toast.makeText(context, "Backup copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JSON", color = colors.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (mode == "IMPORT") {
                    Button(
                        onClick = {
                            if (backupJsonInput.isNotBlank()) {
                                val success = repository.importFromJson(backupJsonInput)
                                Toast.makeText(context, if (success) "Imported successfully!" else "Invalid backup format", Toast.LENGTH_SHORT).show()
                                importExportDialogState = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("Restore Data", color = Color.White)
                    }
                } else {
                    Button(onClick = { importExportDialogState = null }) {
                        Text("Done", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { importExportDialogState = null }) {
                    Text("Close", color = colors.textSecondary)
                }
            }
        )
    }

    // Complete profile clone over Wi-Fi
    if (transferSendOpen) {
        SendTransferDialog(repository = repository, onDismiss = { transferSendOpen = false })
    }
    transferReceiveMerge?.let { merge ->
        ReceiveTransferDialog(
            repository = repository,
            defaultMerge = merge,
            onDismiss = { transferReceiveMerge = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header ---
        item {
            FioScreenHeader(
                title = "Settings",
                subtitle = "Make it yours. Everything stays on this device."
            )
        }

        // --- 1b. Your name ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(18.dp)
            ) {
                Text("Your name", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Shown on your home screen and in your chats.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = profileNameInput,
                    onValueChange = { profileNameInput = it },
                    label = { Text("Name", color = colors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_settings_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                FioPrimaryButton(
                    text = "Save name",
                    onClick = {
                        val trimmed = profileNameInput.trim().ifEmpty { "User" }
                        repository.setProfileName(trimmed)
                        Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_name_button")
                )
            }
        }

        // --- 2. AI Integration (Google Gemini default & OpenAI-compatible providers) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("AI assistant", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Add a key from your AI provider. It stays on this phone.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Provider picker: tap sets provider and fills the matching defaults.
                val providers = listOf(
                    "Google Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
                    "OpenRouter" to "https://openrouter.ai/api/v1",
                    "Custom" to ""
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("provider_picker")
                ) {
                    providers.forEach { (name, _) ->
                        val isSelected = providerChoice == name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.primary else colors.surfaceElevated)
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                .clickable {
                                    providerChoice = name
                                    if (name == "Google Gemini") {
                                        baseUrlInput = "https://generativelanguage.googleapis.com/v1beta/openai"
                                        if (modelNameInput.isBlank() || modelNameInput.contains("/")) {
                                            modelNameInput = "gemini-3.8-flash"
                                        }
                                    } else if (name == "OpenRouter") {
                                        baseUrlInput = "https://openrouter.ai/api/v1"
                                        if (modelNameInput.isBlank() || !modelNameInput.contains("/")) {
                                            modelNameInput = "google/gemini-2.0-flash-001"
                                        }
                                    }
                                }
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (name == "Google Gemini") "Gemini" else if (name == "OpenRouter") "OpenRouter" else "Custom",
                                color = if (isSelected) Color.White else colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                if (providerChoice == "Google Gemini") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Get a free Gemini API key →",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                    )
                                }
                            }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .testTag("get_gemini_key_link")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // One-tap model picker — no hand-typing identifiers.
                    var showModelMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showModelMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("gemini_model_picker")
                        ) {
                            Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Choose Gemini model", fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            listOf(
                                "gemini-3.8-flash" to "Default · current flagship",
                                "gemini-3.6-flash" to "Fast · free tier",
                                "gemini-3.5-flash-lite" to "Light · free tier",
                                "gemini-3.1-flash-lite" to "Lightest · free tier"
                            ).forEach { (model, note) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model, color = colors.textPrimary, fontSize = 12.sp)
                                            Text(note, color = colors.textMuted, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        modelNameInput = model
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("Base URL", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("base_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelNameInput,
                    onValueChange = { modelNameInput = it },
                    label = { Text("Model Identifier", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("model_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))
                FioPrimaryButton(
                    text = "Save AI settings",
                    onClick = {
                        val updated = settings.copy(
                            aiProvider = providerChoice,
                            aiApiKey = apiKeyInput.trim(),
                            aiBaseUrl = baseUrlInput.trim(),
                            aiModelName = modelNameInput.trim()
                        )
                        repository.updateSettings(updated)
                        Toast.makeText(context, "AI settings saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_ai_settings_button")
                )

                Spacer(modifier = Modifier.height(4.dp))
                FioDivider()
                FioRow(
                    title = "Show pictures in answers",
                    subtitle = if (settings.showAiImages)
                        "On. Pictures appear when they help, using a little data."
                    else "Off. Answers are text only, which saves data.",
                    trailing = {
                        Switch(
                            checked = settings.showAiImages,
                            onCheckedChange = {
                                repository.updateSettings(settings.copy(showAiImages = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.primary
                            ),
                            modifier = Modifier.testTag("show_ai_images_toggle")
                        )
                    }
                )
            }
        }

        // --- 3. Look and feel ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(18.dp)
            ) {
                Text("Look and feel", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Pick a canvas, then make it yours with an accent colour.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(14.dp))

                val themeOptions = listOf(
                    "OBSIDIAN_PURPLE" to "Midnight",
                    "SLATE" to "Slate",
                    "PLUM" to "Plum",
                    "FOREST" to "Forest",
                    "OBSIDIAN_LIGHT" to "Paper",
                    "SAND" to "Sand",
                    "MIST" to "Mist",
                    "OLED_BLACK" to "True black",
                    "HIGH_CONTRAST" to "Contrast"
                )

                themeOptions.chunked(3).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { (id, label) ->
                            val isSelected = settings.theme == id
                            val (previewBg, previewAccent, previewText) = themePreview(id)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(previewBg)
                                    .then(
                                        if (isSelected) Modifier.border(
                                            1.5.dp,
                                            previewAccent,
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        repository.updateSettings(settings.copy(theme = id))
                                    }
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(previewAccent)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = label,
                                    color = previewText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(3 - rowThemes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Accent", style = FioType.label, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val accents = listOf(
                        "MINT" to "Theme",
                        "BLUE" to "Blue",
                        "GREEN" to "Green",
                        "AMBER" to "Amber",
                        "ROSE" to "Rose",
                        "ORANGE" to "Orange"
                    )
                    accents.forEach { (id, label) ->
                        val selected = settings.accentColor == id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) accentSwatch(id).copy(alpha = 0.18f)
                                    else colors.surfaceElevated
                                )
                                .clickable { repository.updateSettings(settings.copy(accentColor = id)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accentSwatch(id))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = if (selected) accentSwatch(id) else colors.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(4.dp))

                FioRow(
                    title = "Reduce motion",
                    subtitle = "Turns off extra animation. Easier on older devices.",
                    trailing = {
                        Switch(
                            checked = settings.reduceMotion,
                            onCheckedChange = {
                                repository.updateSettings(settings.copy(reduceMotion = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.primary
                            )
                        )
                    }
                )

                FioDivider()

                FioRow(
                    title = "Text size",
                    subtitle = "How large answers and notes read.",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "S" to 0.92f,
                                "M" to 1.0f,
                                "L" to 1.14f
                            ).forEach { (label, scale) ->
                                com.example.fio.ui.components.FioPill(
                                    text = label,
                                    selected = kotlin.math.abs(settings.fontScale - scale) < 0.03f,
                                    onClick = {
                                        repository.updateSettings(settings.copy(fontScale = scale))
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }

        // --- 4. Lockdown Mode Rules Customization (Section 10 requirement) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("Focus rules", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Choose how strict focus sessions are and how often you get breaks.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gracePeriodInput,
                        onValueChange = { gracePeriodInput = it },
                        label = { Text("Grace (seconds)", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = breakDurInput,
                        onValueChange = { breakDurInput = it },
                        label = { Text("Break (minutes)", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                FioSecondaryButton(
                    text = "Save rules",
                    onClick = {
                        val grace = gracePeriodInput.toIntOrNull() ?: 15
                        val breakDur = breakDurInput.toIntOrNull() ?: 10
                        val updated = settings.copy(gracePeriodSeconds = grace, breakDurationMinutes = breakDur)
                        repository.updateSettings(updated)
                        Toast.makeText(context, "Focus rules saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 5. Backup ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(18.dp)
            ) {
                Text("Backup", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Your data stays on this phone. Save a text copy, or restore one you made earlier.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FioSecondaryButton(
                        text = "Save a copy",
                        icon = Icons.Default.Share,
                        onClick = { importExportDialogState = "EXPORT" },
                        modifier = Modifier.weight(1f).testTag("export_data_button")
                    )
                    FioSecondaryButton(
                        text = "Restore",
                        icon = Icons.Default.Restore,
                        onClick = {
                            backupJsonInput = ""
                            importExportDialogState = "IMPORT"
                        },
                        modifier = Modifier.weight(1f).testTag("import_data_button")
                    )
                }
            }
        }

        // --- 5b. Move to another device ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(18.dp)
            ) {
                Text("Move to another device", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Copies everything to another phone or tablet on the same Wi-Fi: books, notes, chats, photos, calendar, settings and your AI key. Devices find each other automatically.",
                    style = FioType.small,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                FioPrimaryButton(
                    text = "Clone this profile",
                    icon = Icons.Default.IosShare,
                    onClick = { transferSendOpen = true },
                    modifier = Modifier.fillMaxWidth().testTag("transfer_send_button")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FioSecondaryButton(
                        text = "Sync progress",
                        icon = Icons.Default.Sync,
                        onClick = { transferReceiveMerge = true },
                        modifier = Modifier.weight(1f).testTag("transfer_sync_button")
                    )
                    FioSecondaryButton(
                        text = "Receive",
                        icon = Icons.Default.DeviceHub,
                        onClick = { transferReceiveMerge = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Sync keeps the newest version of each item, so nothing you did on either device is lost.",
                    style = FioType.caption,
                    color = colors.textMuted
                )
            }
        }

        // --- 6. About ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(18.dp)
            ) {
                Text("About Fio", style = FioType.section, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A private study app, made for one person and shared openly.",
                    style = FioType.small,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))
                AboutRow(label = "Developer", value = "Mike Edeimu")
                AboutRow(
                    label = "GitHub",
                    value = "@emf187",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/emf187"))
                            )
                        }
                    }
                )
                AboutRow(
                    label = "Source",
                    value = "fio on GitHub",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/emf187/fio"))
                            )
                        }
                    }
                )
                AboutRow(label = "Version", value = BuildConfig.VERSION_NAME)

                Spacer(modifier = Modifier.height(12.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(4.dp))

                FioRow(
                    title = "Chat history",
                    subtitle = if (chatHistory.isEmpty()) {
                        "No messages saved yet."
                    } else {
                        "${chatHistory.size} messages, stored on this device."
                    },
                    trailing = if (chatHistory.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = {
                                    repository.clearChatHistory()
                                    Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear", color = colors.error, fontSize = 12.sp)
                            }
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalFioColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            color = if (onClick != null) colors.primary else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
