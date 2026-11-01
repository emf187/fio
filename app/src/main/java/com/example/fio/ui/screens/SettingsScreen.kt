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
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import com.example.fio.ui.theme.accentSwatch

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

    // Export / Import Dialog
    importExportDialogState?.let { mode ->
        AlertDialog(
            onDismissRequest = { importExportDialogState = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = if (mode == "EXPORT") "Export Data Over Wi-Fi / File" else "Import Data Backup",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (mode == "EXPORT")
                            "All books, documents data, reading progress, chat sessions, schedules, habits, goals, notes, sleep, and meals are serialized below. Copy and transfer to another device:"
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
            Column {
                Text("Settings & Customization", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Provider-agnostic AI, themes, lockdown rules, and offline data sync", color = colors.textMuted, fontSize = 11.sp)
            }
        }

        // --- 1b. User Profile & Identity ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Profile Name & Identity", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Displayed across your personal dashboard and exported with all books, documents, and progress.",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = profileNameInput,
                    onValueChange = { profileNameInput = it },
                    label = { Text("Profile Name", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_settings_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val trimmed = profileNameInput.trim().ifEmpty { "User" }
                        repository.setProfileName(trimmed)
                        Toast.makeText(context, "Profile name updated to $trimmed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_name_button")
                ) {
                    Text("Update Profile Name", color = Color.White)
                }
            }
        }

        // --- 2. AI Integration (Google Gemini default & OpenAI-compatible providers) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Provider & Local API Key", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keys are stored locally on device only. Default is Google Gemini (free tier resets daily).",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                Button(
                    onClick = {
                        val updated = settings.copy(
                            aiProvider = providerChoice,
                            aiApiKey = apiKeyInput.trim(),
                            aiBaseUrl = baseUrlInput.trim(),
                            aiModelName = modelNameInput.trim()
                        )
                        repository.updateSettings(updated)
                        Toast.makeText(context, "AI configuration saved", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_ai_settings_button")
                ) {
                    Text("Save AI Settings", color = Color.White)
                }
            }
        }

        // --- 3. Theme & Accessibility Customization ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("Appearance & Themes", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Pick a dark, high contrast, or light palette.", color = colors.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val themes = listOf(
                    "OBSIDIAN_PURPLE" to "Dark",
                    "OLED_BLACK" to "OLED Black",
                    "HIGH_CONTRAST" to "High Contrast",
                    "OBSIDIAN_LIGHT" to "Light (Default)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themes.forEach { (id, label) ->
                        val isSelected = settings.theme == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                .clickable {
                                    val updated = settings.copy(theme = id)
                                    repository.updateSettings(updated)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    val updated = settings.copy(theme = id)
                                    repository.updateSettings(updated)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, color = if (isSelected) colors.primary else colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Accent Colour", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val accents = listOf(
                        "PURPLE" to "Purple",
                        "BLUE" to "Blue",
                        "TEAL" to "Teal",
                        "GREEN" to "Green",
                        "AMBER" to "Amber",
                        "ROSE" to "Rose",
                        "ORANGE" to "Orange"
                    )
                    accents.forEach { (id, label) ->
                        val selected = settings.accentColor == id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) accentSwatch(id).copy(alpha = 0.18f)
                                    else colors.surfaceElevated
                                )
                                .clickable { repository.updateSettings(settings.copy(accentColor = id)) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Reduce motion toggle (Section UI requirement)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce Motion", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Minimizes transitions and keeps CPU usage minimal on older hardware.", color = colors.textMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.reduceMotion,
                        onCheckedChange = {
                            val updated = settings.copy(reduceMotion = it)
                            repository.updateSettings(updated)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.primary)
                    )
                }
            }
        }

        // --- 4. Lockdown Mode Rules Customization (Section 10 requirement) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("Lockdown & Break Rules", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Configure grace period before strikes and scheduled break frequency.", color = colors.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gracePeriodInput,
                        onValueChange = { gracePeriodInput = it },
                        label = { Text("Grace Period (sec)", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = breakDurInput,
                        onValueChange = { breakDurInput = it },
                        label = { Text("Break Length (min)", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val grace = gracePeriodInput.toIntOrNull() ?: 15
                        val breakDur = breakDurInput.toIntOrNull() ?: 10
                        val updated = settings.copy(gracePeriodSeconds = grace, breakDurationMinutes = breakDur)
                        repository.updateSettings(updated)
                        Toast.makeText(context, "Timer rules updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Lockdown Rules", color = colors.primary)
                }
            }
        }

        // --- 5. Data Privacy & Offline Wi-Fi Backup (Section 14 requirement) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("Data & Privacy (Offline-First)", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("No account or cloud dependency. All your books, documents, reading progress, chat sessions, notes, logs, and profile remain local. Export and import over Wi-Fi/file:", color = colors.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { importExportDialogState = "EXPORT" },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("export_data_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            backupJsonInput = ""
                            importExportDialogState = "IMPORT"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("import_data_button")
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Backup", color = colors.textPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 6. App Source Code & Export Options ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Source Code & Installation", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "100% open native Android Kotlin codebase. You can export the full source code anytime:",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "â€¢ Download ZIP: In the Google AI Studio menu (top bar), click 'Export project as ZIP' to download the entire Android project.\nâ€¢ GitHub Sync: Export directly to your GitHub repository.\nâ€¢ APK Installation: Download the built debug APK to install directly on your tablet or phone.",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // --- 6. About ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About Fio", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\u201cI was bored, so I made my private study app.\u201d",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(10.dp))
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
                AboutRow(label = "Version", value = BuildConfig.VERSION_NAME)

                Spacer(modifier = Modifier.height(10.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Wingman chatbot history stats + clear action
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wingman Chatbot History",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (chatHistory.isEmpty()) {
                                "No chatbot messages stored yet."
                            } else {
                                "${chatHistory.size} message(s) stored on this device only"
                            },
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (chatHistory.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                repository.clearChatHistory()
                                Toast.makeText(context, "Chatbot history cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear History", color = colors.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
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
