package com.example.fio.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.ai.FriendlyAiError
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioPrimaryButton
import com.example.fio.ui.theme.FioType
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided first-run setup: name → AI key → ready, with the current step
 * highlighted. Fresh installs are gated here until they finish (or Cancel);
 * "Import profile" restores a full backup from any Fio install instead.
 */
@Composable
fun OnboardingScreen(
    repository: FioRepository,
    aiClient: FioAiClient
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsState()
    val profileName by repository.profileName.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }
    var providerChoice by remember { mutableStateOf("Google Gemini") }
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    var testMessage by remember { mutableStateOf("") }

    // Pre-fill from a profile that was imported while standing here.
    LaunchedEffect(profileName) { if (nameInput.isBlank()) nameInput = profileName }
    LaunchedEffect(settings.aiApiKey) { if (keyInput.isBlank()) keyInput = settings.aiApiKey }
    LaunchedEffect(settings.aiProvider) { providerChoice = settings.aiProvider }

    // Import profile: a full backup JSON from this or any other Fio install.
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (json.isNullOrBlank() || !repository.importFromJson(json)) {
                    Toast.makeText(context, "Could not read that backup file", Toast.LENGTH_SHORT).show()
                } else {
                    repository.setOnboarded(true)
                    Toast.makeText(context, "Profile imported — welcome back!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 34.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo + wordmark
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Fio",
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Welcome to Fio", color = colors.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("effio • to realize & know", color = colors.textMuted, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(18.dp))

        // Step pills — the highlighted tab walks through setup
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Your name", "AI key", "Ready").forEachIndexed { index, label ->
                val active = index == step
                val done = index < step
                Text(
                    text = if (done) "✓ ${label}" else "${index + 1} · ${label}",
                    color = if (active) Color.White else colors.textMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) colors.primary else colors.surfaceElevated)
                        .border(1.dp, if (active) colors.primary else colors.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            // ---------------------------------------------------------- 1 · name
            0 -> {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Your name", style = FioType.section, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "What should Fio & Wingman call you? It personalizes greetings and chats.",
                        style = FioType.small,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text("Enter your name...", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_profile_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FioPrimaryButton(
                        text = "Continue",
                        onClick = {
                            repository.setProfileName(nameInput.trim().ifBlank { "User" })
                            step = 1
                        },
                        enabled = nameInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_save_button")
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { repository.setOnboarded(true) }) {
                        Text("Cancel — skip for now", color = colors.textMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        runCatching {
                            importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        }
                    }) {
                        Text("Import profile", color = colors.primary, fontSize = 12.sp)
                    }
                }
                Text(
                    "Cancel keeps Fio fully usable — you can finish setup later in Settings.",
                    color = colors.textMuted,
                    fontSize = 10.5.sp
                )
            }

            // ------------------------------------------------------- 2 · AI key
            1 -> {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AI assistant", style = FioType.section, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Wingman runs on your own free API key. It stays on this phone — Fio never sees it.",
                        style = FioType.small,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Provider picker (same defaults as Settings)
                    val providers = listOf(
                        "Google Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
                        "OpenRouter" to "https://openrouter.ai/api/v1",
                        "Custom" to ""
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        providers.forEach { (name, url) ->
                            val isSelected = providerChoice == name
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) colors.primary else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        providerChoice = name
                                        val updated = when (name) {
                                            "Google Gemini" -> settings.copy(
                                                aiProvider = name,
                                                aiBaseUrl = url,
                                                aiModelName = if (settings.aiModelName.contains("/")) "gemini-3.8-flash" else settings.aiModelName
                                            )
                                            "OpenRouter" -> settings.copy(
                                                aiProvider = name,
                                                aiBaseUrl = url,
                                                aiModelName = if (!settings.aiModelName.contains("/")) "google/gemini-2.0-flash-001" else settings.aiModelName
                                            )
                                            else -> settings.copy(aiProvider = name)
                                        }
                                        repository.updateSettings(updated)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (name == "Google Gemini") "Gemini" else name,
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
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    val keyUrl = when (providerChoice) {
                        "Google Gemini" -> "https://aistudio.google.com/apikey" to "Get a free Gemini API key →"
                        "OpenRouter" -> "https://openrouter.ai/keys" to "Get a free OpenRouter key →"
                        else -> null
                    }
                    if (keyUrl != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = keyUrl.second,
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl.first)))
                                    }
                                }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .testTag("get_gemini_key_link")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    // Live connection test against the pasted key
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                testing = true
                                testOk = null
                                repository.updateSettings(settings.copy(aiApiKey = keyInput.trim()))
                                val res = aiClient.sendPrompt(
                                    "Reply with just: OK",
                                    "You are a connection test. Reply with one word.",
                                    repository.settings.value
                                )
                                testing = false
                                when (res) {
                                    is com.example.fio.data.ai.AiResult.Success -> {
                                        testOk = true
                                        testMessage = "Working — Wingman can answer."
                                    }
                                    is com.example.fio.data.ai.AiResult.Error -> {
                                        testOk = false
                                        testMessage = FriendlyAiError.message(res.message)
                                    }
                                }
                            }
                        },
                        enabled = keyInput.isNotBlank() && !testing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_connection_button")
                    ) {
                        Text(if (testing) "Testing…" else "Test connection", fontSize = 12.sp)
                    }
                    when {
                        testOk == true -> Text(
                            testMessage,
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        testOk == false -> Text(
                            testMessage,
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    FioPrimaryButton(
                        text = "Continue",
                        onClick = {
                            repository.updateSettings(
                                settings.copy(aiApiKey = keyInput.trim())
                            )
                            step = 2
                        },
                        enabled = keyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = { step = 2 }) {
                    Text("Skip for now — add it later in Settings", color = colors.textMuted, fontSize = 12.sp)
                }
            }

            // -------------------------------------------------------- 3 · ready
            else -> {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "You're all set${profileName.substringBefore(' ').takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}!",
                            style = FioType.section,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (settings.aiApiKey.isNotBlank())
                            "Wingman is connected and ready — say hi from the Study tab."
                        else
                            "You can add your AI key anytime in Settings → AI assistant.",
                        style = FioType.small,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FioPrimaryButton(
                        text = "Start using Fio",
                        onClick = { repository.setOnboarded(true) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
