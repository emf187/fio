package com.example.fio.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.fio.data.model.DocumentChunk
import com.example.fio.data.model.StudyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object DocumentImporter {

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        courseId: String
    ): StudyDocument? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val extension = fileName.substringAfterLast('.', "").lowercase()

            val format = when (extension) {
                "pdf" -> "PDF"
                "epub" -> "EPUB"
                "md", "markdown" -> "MD"
                "csv", "tsv", "xlsx", "xls" -> "SPREADSHEET"
                "doc", "docx" -> "DOCX"
                else -> "TXT"
            }

            val rawText = when (extension) {
                "epub" -> extractEpubText(context, uri)
                "docx" -> extractDocxText(context, uri)
                "csv", "tsv" -> extractCsvText(context, uri)
                else -> readPlainText(context, uri)
            }

            val content = if (rawText.isNotBlank()) {
                rawText
            } else {
                "# $fileName\n\n[Imported $format document: ${fileName}]"
            }

            // Create logical chunks of ~600 chars for AI context
            val chunks = content.chunked(600).mapIndexed { idx, chk ->
                DocumentChunk(
                    id = "chunk_${idx + 1}",
                    title = "$fileName - Part ${idx + 1}",
                    text = chk
                )
            }

            StudyDocument(
                courseId = courseId,
                title = fileName.substringBeforeLast('.'),
                author = "Local Import",
                format = format,
                content = content,
                chunks = chunks,
                readingProgressPercent = 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Document"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: "Document"
                }
            }
        }
        return name
    }

    private fun readPlainText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        reader.useLines { lines ->
            lines.forEach { line ->
                sb.append(line).append("\n")
            }
        }
        return sb.toString().trim()
    }

    private fun extractCsvText(context: Context, uri: Uri): String {
        val plain = readPlainText(context, uri)
        if (plain.isBlank()) return ""
        val lines = plain.lines()
        val sb = StringBuilder("# Spreadsheet Table Data\n\n")
        lines.take(200).forEachIndexed { i, line ->
            val cols = line.split(",", "\t")
            sb.append("| ").append(cols.joinToString(" | ")).append(" |\n")
            if (i == 0) {
                sb.append("| ").append(cols.joinToString(" | ") { "---" }).append(" |\n")
            }
        }
        return sb.toString()
    }

    // Lightweight EPUB extractor using standard Java ZipInputStream (Zero external libraries!)
    private fun extractEpubText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zip = ZipInputStream(inputStream)
        val sb = StringBuilder()

        try {
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (entryName.endsWith(".html") || entryName.endsWith(".xhtml") || entryName.endsWith(".htm")) {
                    val reader = BufferedReader(InputStreamReader(zip))
                    val chapterContent = reader.readText()
                    // Strip HTML tags for clean markdown reading
                    val cleanText = chapterContent
                        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                        .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    if (cleanText.isNotBlank()) {
                        sb.append(cleanText).append("\n\n")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            zip.close()
        }
        return sb.toString().trim()
    }

    // Lightweight DOCX text extractor using standard ZipInputStream
    private fun extractDocxText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zip = ZipInputStream(inputStream)
        val sb = StringBuilder()

        try {
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                    val reader = BufferedReader(InputStreamReader(zip))
                    val xml = reader.readText()
                    val cleanText = xml
                        .replace(Regex("<w:p[^>]*>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                    sb.append(cleanText)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            zip.close()
        }
        return sb.toString().trim()
    }
}
