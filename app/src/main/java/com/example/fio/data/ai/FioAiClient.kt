package com.example.fio.data.ai

import com.example.fio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AiResult<out T> {
    data class Success<T>(val data: T) : AiResult<T>()
    data class Error(val message: String, val isNetworkError: Boolean = false) : AiResult<Nothing>()
}

class FioAiClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendPrompt(
        prompt: String,
        systemInstruction: String,
        settings: AppSettings,
        chunks: List<DocumentChunk> = emptyList(),
        imageBase64: String? = null
    ): AiResult<String> = withContext(Dispatchers.IO) {
        if (settings.aiApiKey.isBlank()) {
            return@withContext AiResult.Error("API Key is missing. Please configure your key in Settings.")
        }

        try {
            val root = JSONObject()
            root.put("model", settings.aiModelName)

            val messages = JSONArray()

            // Context from chunks
            val contextBuilder = StringBuilder()
            if (chunks.isNotEmpty()) {
                contextBuilder.append("\n=== RELEVANT COURSE MATERIAL CHUNKS ===\n")
                chunks.take(4).forEachIndexed { index, chunk ->
                    contextBuilder.append("[Chunk ${index + 1}: ${chunk.title}]\n${chunk.text}\n\n")
                }
            }

            val fullSystem = "$systemInstruction\n$contextBuilder\nRespond in clean markdown, direct, academic, concise tone."

            val sysMsg = JSONObject()
            sysMsg.put("role", "system")
            sysMsg.put("content", fullSystem)
            messages.put(sysMsg)

            val userMsg = JSONObject()
            userMsg.put("role", "user")

            // Multimodal image support
            if (imageBase64 != null) {
                val contentArray = JSONArray()
                val textObj = JSONObject().put("type", "text").put("text", prompt)
                val imgObj = JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageBase64))
                contentArray.put(textObj)
                contentArray.put(imgObj)
                userMsg.put("content", contentArray)
            } else {
                userMsg.put("content", prompt)
            }
            messages.put(userMsg)

            root.put("messages", messages)
            root.put("temperature", 0.7)

            val url = if (settings.aiBaseUrl.endsWith("/")) "${settings.aiBaseUrl}chat/completions" else "${settings.aiBaseUrl}/chat/completions"

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${settings.aiApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(root.toString().toRequestBody(jsonMediaType))

            if (url.contains("openrouter")) {
                requestBuilder.addHeader("HTTP-Referer", "https://fio.local")
                requestBuilder.addHeader("X-Title", "Fio Wingman AI")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext AiResult.Error("API Error ${response.code}: $bodyString", isNetworkError = false)
            }

            val responseJson = JSONObject(bodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val msg = firstChoice.getJSONObject("message")
                val content = msg.getString("content")
                AiResult.Success(content)
            } else {
                AiResult.Error("Empty response from AI provider")
            }
        } catch (e: java.net.UnknownHostException) {
            AiResult.Error("Device is offline. Connect to the internet to use AI features.", isNetworkError = true)
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.Error("Request timed out. Please verify your connection or model speed.", isNetworkError = true)
        } catch (e: Exception) {
            AiResult.Error(e.localizedMessage ?: "Unknown error connecting to AI provider")
        }
    }

    // Wingman Agent: Complete App Control & Multimodal Vision
    suspend fun executeWingmanAgent(
        userPrompt: String,
        imageBase64: String?,
        stateSummary: String,
        settings: AppSettings
    ): AiResult<AgentExecutionResult> {
        val system = """You are Wingman, the personal life planner and executive AI companion in Fio.
You have FULL CONTROL of the app and can read uploaded images (lecture slides, notes, past exam questions, food photos, books).
When the user asks you to perform an action or if an action naturally helps them, you can generate executable app actions.

CURRENT APP STATE:
$stateSummary

AVAILABLE APP ACTIONS YOU CAN DISPATCH:
- ADD_EVENT: title, startMinutesFromNow, durationMinutes, category (STUDY, GYM, JOG, REST, CLASS)
- LOG_MEAL: note, hoursAgo (0 for now)
- LOG_SLEEP: hours, type (NIGHT or NAP)
- TOGGLE_HABIT: habitName
- ADD_HABIT: habitName, description
- CREATE_NOTE: title, content
- ADD_GOAL: title, targetValue, unit, category

You must respond in JSON with format:
{
  "reply": "Clear, friendly, concise message explaining what you did or answering the question.",
  "actions": [
    {
      "type": "ACTION_TYPE",
      "summary": "Short readable summary of action (e.g. Schedule gym at 5pm)",
      "payload": {
        "key": "value"
      }
    }
  ]
}
If no action is required, set "actions": [].
Do NOT wrap with markdown fences if possible, or use standard ```json.
"""
        return when (val res = sendPrompt(userPrompt, system, settings, emptyList(), imageBase64)) {
            is AiResult.Success -> {
                try {
                    val raw = res.data.trim()
                    val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val obj = JSONObject(cleaned)
                    val reply = obj.optString("reply", raw)
                    val actionList = mutableListOf<AgentAction>()
                    val actionsArray = obj.optJSONArray("actions")
                    if (actionsArray != null) {
                        for (i in 0 until actionsArray.length()) {
                            val actObj = actionsArray.getJSONObject(i)
                            val type = actObj.optString("type", "")
                            val summary = actObj.optString("summary", type)
                            val payloadObj = actObj.optJSONObject("payload")
                            val payloadMap = mutableMapOf<String, String>()
                            if (payloadObj != null) {
                                val keys = payloadObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    payloadMap[k] = payloadObj.optString(k, "")
                                }
                            }
                            actionList.add(AgentAction(type, summary, payloadMap))
                        }
                    }
                    AiResult.Success(AgentExecutionResult(reply, actionList))
                } catch (e: Exception) {
                    // Fallback to text response if model returned conversational markdown
                    AiResult.Success(AgentExecutionResult(res.data, emptyList()))
                }
            }
            is AiResult.Error -> AiResult.Error(res.message, res.isNetworkError)
        }
    }

    // Propose calendar adjustment
    suspend fun proposeScheduleChange(
        requestDescription: String,
        currentScheduleSummary: String,
        settings: AppSettings
    ): AiResult<ProposedScheduleChange> {
        val system = """You are Wingman, Fio's schedule planner. The user wants to adjust their calendar.
You MUST analyze the request and return ONLY a valid JSON object without markdown formatting, with fields:
{
  "title": "Short event title",
  "action": "ADD" (or "MOVE" or "REMOVE"),
  "reason": "Why this change is suggested",
  "startTimeMinutesFromNow": 60,
  "durationMinutes": 60,
  "eventType": "STUDY" (or "REST", "GYM", "JOG", "CLASS")
}
Current schedule context: $currentScheduleSummary
"""
        return when (val res = sendPrompt(requestDescription, system, settings)) {
            is AiResult.Success -> {
                try {
                    val raw = res.data.trim()
                    val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val obj = JSONObject(cleaned)
                    val now = System.currentTimeMillis()
                    val offsetMin = obj.optLong("startTimeMinutesFromNow", 60)
                    val durationMin = obj.optLong("durationMinutes", 60)
                    val start = now + (offsetMin * 60 * 1000L)
                    val end = start + (durationMin * 60 * 1000L)
                    val typeStr = obj.optString("eventType", "STUDY")
                    val type = try { EventType.valueOf(typeStr) } catch (e: Exception) { EventType.STUDY }

                    AiResult.Success(
                        ProposedScheduleChange(
                            title = obj.getString("title"),
                            action = obj.getString("action"),
                            reason = obj.getString("reason"),
                            startTimeMillis = start,
                            endTimeMillis = end,
                            eventType = type
                        )
                    )
                } catch (e: Exception) {
                    AiResult.Error("Could not parse schedule proposal: ${e.message}")
                }
            }
            is AiResult.Error -> AiResult.Error(res.message, res.isNetworkError)
        }
    }
}
