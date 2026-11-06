package com.example.fio.data.ai

import android.content.Context
import com.example.fio.data.storage.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches 1–2 related illustrations for AI answers (Wingman & Tutor).
 *
 * The model is told (via system prompt when the user enables images) to end
 * replies that would benefit from a picture with a single marker line:
 * `[IMG: short image search query]`. The app strips the marker, searches the
 * web — DuckDuckGo image endpoint (no API key), falling back to Wikimedia
 * Commons — downloads a small thumbnail and stores it locally, so the reply
 * bubble shows real related images. Everything degrades to text-only when a
 * network is unavailable.
 */
object AiImageHelper {

    data class AiImage(val localPath: String, val sourceUrl: String, val title: String)

    private val client: OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Lines like `[IMG: newton cradle]` / `[Image: wave interference]`. */
    private val IMG_LINE = Regex(
        """^\s*\[\s*(?:IMG|IMAGE)\s*[:：]\s*([^\]]*?)\s*\]\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Splits `[IMG: …]` marker lines out of a reply. Returns (cleanText, firstQueryOrNull). */
    fun extractImageQuery(text: String): Pair<String, String?> {
        var query: String? = null
        val clean = text.lines()
            .filter { line ->
                val match = IMG_LINE.find(line)
                if (match != null) {
                    if (query == null) query = match.groupValues[1].trim().take(120).ifBlank { null }
                    false
                } else true
            }
            .joinToString("\n")
            .trim()
        return (clean.ifBlank { text.trim() }) to query
    }

    /** Math formatting rule appended to the system prompt (Wingman + Tutor). */
    const val MATH_RULES =
        "Formatting rules you must follow in every reply: " +
            "write all math as LaTeX wrapped in $...$ for inline or $$...$$ on its own line for display equations; " +
            "use \\frac{a}{b} for fractions, \\sqrt{x} for roots and ^ / _ for powers and indices; " +
            "keep LaTeX commands inside the $...$ wrappers. "

    /** Image-search rule appended only when the user has images enabled. */
    const val IMAGE_RULE =
        "Related-image rule: when the answer is about an abstract concept, object, machine, organism, " +
            "map, structure or diagram that a picture would genuinely clarify, END your reply with " +
            "exactly one line of the form [IMG: 2-4 word image search query]. " +
            "The query must name the concrete subject of the question " +
            "(for a question about the human heart write [IMG: human heart anatomy]; " +
            "for the water cycle write [IMG: water cycle diagram]). " +
            "Never use a school subject alone such as math, chemistry, physics or history " +
            "as the query, and never search for a formula or calculation. " +
            "Do not add the marker for definitions, calculations, advice, list steps or anything purely textual. " +
            "Never more than one marker. "

    /**
     * Fetches up to [count] thumbnails for [query]. DuckDuckGo first; falls back
     * to Wikimedia Commons. Returns whatever downloaded successfully.
     */
    suspend fun fetchRelatedImages(
        context: Context,
        query: String,
        count: Int = 2
    ): List<AiImage> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val urlCandidates = duckduckgo(q)
        val combined = (urlCandidates + wikimedia(q)).filter { it.second.isNotBlank() }
        val saved = mutableListOf<AiImage>()
        val seen = HashSet<String>()
        // Drop results whose title shares nothing with the query — keeps
        // generic drift (unrelated stock images) out of the answer.
        val qWords = q.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
        for ((title, imageUrl) in combined) {
            if (saved.size >= count) break
            if (!seen.add(imageUrl)) continue
            if (qWords.isNotEmpty()) {
                val tWords = title.lowercase().split(Regex("[^a-z0-9]+")).toSet()
                val relevant = tWords.any { it in qWords } ||
                    qWords.any { w -> tWords.any { it.startsWith(w) } }
                if (!relevant) continue
            }
            val key = md5(q + "|" + imageUrl)
            val path = downloadAndStore(context, imageUrl, "ai_$key.jpg")
            if (path != null) saved.add(AiImage(path, imageUrl, title))
        }
        saved
    }

    // --- providers ------------------------------------------------------------

    /** DuckDuckGo image search: vqd token + i.js JSON endpoint. No API key needed. */
    private fun duckduckgo(query: String): List<Pair<String, String>> {
        return try {
            val vqdHtml = httpGet("https://duckduckgo.com/?q=${enc(query)}&ia=images&iax=images")
            val vqd = Regex("""vqd=(-?[\da-zA-Z-]+)""").find(vqdHtml)?.groupValues?.get(1)
            if (vqd.isNullOrBlank()) return emptyList()
            val json = httpGet(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=${enc(query)}&vqd=$vqd&f=,,,&p=1",
                headers = mapOf(
                    "Referer" to "https://duckduckgo.com/",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Sec-Fetch-Mode" to "cors"
                )
            )
            val array = JSONArray(json)
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val thumb = o.optString("image", "")
                val title = o.optString("title", "").take(80)
                val width = o.optInt("width", 0)
                if (thumb.startsWith("http") && width >= 200) out.add(if (title.isBlank()) query to thumb else title to thumb)
                if (out.size >= 6) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Wikimedia Commons search — always available, clean licensing. */
    private fun wikimedia(query: String): List<Pair<String, String>> {
        return try {
            val url = "https://commons.wikimedia.org/w/api.php" +
                "?action=query&format=json&formatversion=2&generator=search" +
                "&gsrsearch=${enc(query)}&gsrnamespace=6&gsrlimit=6" +
                "&prop=imageinfo&iiprop=url|size&iiurlwidth=640"
            val json = httpGet(url)
            val pages = org.json.JSONObject(json).optJSONObject("query")?.optJSONArray("pages")
                ?: return emptyList()
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until pages.length()) {
                val p = pages.getJSONObject(i)
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val thumb = info.optString("thumburl", info.optString("url", ""))
                val title = p.optString("title", "")
                    .removePrefix("File:").substringBeforeLast('.').replace('_', ' ')
                if (thumb.startsWith("http")) out.add(title to thumb)
                if (out.size >= 6) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- plumbing ---------------------------------------------------------------

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json,*/*;q=0.8")
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body ?: throw IllegalStateException("Empty body")
            return body.string()
        }
    }

    private suspend fun downloadAndStore(
        context: Context,
        imageUrl: String,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://duckduckgo.com/")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.size > 4_000_000) return@withContext null
                ImageStorage.storeRemoteImage(context, bytes, fileName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value.trim(), "UTF-8")

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
