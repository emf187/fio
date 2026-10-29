package com.example.fio.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.fio.data.model.AppSettings
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors

@Composable
fun SettingsScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by repository.settings.collectAsState()
    val profileName by repository.profileName.collectAsState()

    // Local mutable state
    var profileNameInput by remember(profileName) { mutableStateOf(profileName) }
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
                                shape = RoundedCornerShape(6.dp),
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
                                shape = RoundedCornerShape(6.dp),
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
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_name_button")
                ) {
                    Text("Update Profile Name", color = Color.White)
                }
            }
        }

        // --- 2. AI Integration (OpenRouter & OpenAI-compatible) ---
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
                    text = "Provider-agnostic. Keys are stored locally on device only. Default is OpenRouter.",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
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
                            aiApiKey = apiKeyInput.trim(),
                            aiBaseUrl = baseUrlInput.trim(),
                            aiModelName = modelNameInput.trim()
                        )
                        repository.updateSettings(updated)
                        Toast.makeText(context, "AI configuration saved", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(6.dp),
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
                Text("Select an Obsidian-inspired palette, high contrast, or light mode.", color = colors.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val themes = listOf(
                    "OBSIDIAN_PURPLE" to "Obsidian Purple (Default)",
                    "OLED_BLACK" to "OLED Black",
                    "HIGH_CONTRAST" to "High Contrast",
                    "OBSIDIAN_LIGHT" to "Obsidian Light"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themes.forEach { (id, label) ->
                        val isSelected = settings.theme == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
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
                    shape = RoundedCornerShape(6.dp),
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
                        shape = RoundedCornerShape(6.dp),
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
                        shape = RoundedCornerShape(6.dp),
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
                    text = "• Download ZIP: In the Google AI Studio menu (top bar), click 'Export project as ZIP' to download the entire Android project.\n• GitHub Sync: Export directly to your GitHub repository.\n• APK Installation: Download the built debug APK to install directly on your tablet or phone.",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
