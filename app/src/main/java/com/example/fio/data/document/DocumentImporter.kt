package com.example.fio.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.fio.data.model.StudyDocument
import com.example.fio.data.model.deriveDocumentChunks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import java.io.File

/**
 * Extracts readable text from imported files.
 *
 * Everything here is bounded (byte caps, char caps, row caps) so a malformed or huge
 * file can never hang the import or blow up the reader later on.
 */
object DocumentImporter {

    /** Hard ceiling on extracted text so a bad file can never OOM the app. */
    private const val MAX_CONTENT_CHARS = 4_000_000

    /** Raw byte ceiling when sniffing binary formats (PDF/XLSX/DOCX). */
    private const val MAX_BINARY_BYTES = 30 * 1024 * 1024

    /** Individual zip entries larger than this are skipped (images, fonts, ...). */
    private const val MAX_ENTRY_BYTES = 2_000_000

    /** Cap on inflated PDF stream size per stream. */
    private const val MAX_INFLATED_BYTES = 2_000_000

    private const val MAX_SHEET_ROWS = 2_000
    private const val MAX_SHEET_COLS = 60

    /** Ceiling for the stored original file (the reader opens it directly). */
    private const val MAX_ORIGINAL_BYTES = 200L * 1024 * 1024

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        courseId: String
    ): StudyDocument? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val title = fileName.substringBeforeLast('.')
            val extension = fileName.substringAfterLast('.', "").lowercase()

            val format = when (extension) {
                "pdf" -> "PDF"
                "epub" -> "EPUB"
                "md", "markdown" -> "MD"
                "csv", "tsv", "xlsx", "xls" -> "SPREADSHEET"
                "doc", "docx" -> "DOCX"
                "ppt", "pptx" -> "SLIDES"
                "fb2" -> "FB2"
                else -> "TXT"
            }

            var rawText = when (extension) {
                "epub" -> extractEpubText(context, uri)
                "docx" -> extractDocxText(context, uri)
                "xlsx" -> extractXlsxText(context, uri)
                "pptx" -> extractPptxText(context, uri)
                "ppt" -> "Legacy .ppt presentations are not supported.\n\nPlease re-save the file as .pptx and import it again."
                "pdf" -> extractPdfText(context, uri)
                "csv", "tsv" -> extractCsvText(context, uri)
                "xls" -> "Legacy .xls spreadsheets are not supported.\n\nPlease re-save the file as .xlsx or .csv and import it again."
                "doc" -> "Legacy .doc files are not supported.\n\nPlease re-save the file as .docx and import it again."
                else -> readPlainText(context, uri)
            }

            if (rawText.length > MAX_CONTENT_CHARS) {
                rawText = rawText.substring(0, MAX_CONTENT_CHARS) +
                    "\n\n[... document truncated: this file is very large ...]"
            }

            val content = if (rawText.isNotBlank()) {
                rawText
            } else {
                "# $fileName\n\n[Imported $format document: $fileName]"
            }

            val document = StudyDocument(
                courseId = courseId,
                title = title,
                author = "Local Import",
                format = format,
                content = content,
                chunks = deriveDocumentChunks(title, content),
                readingProgressPercent = 0
            )
            // Keep the original file too: the Areada-based reader renders the real
            // EPUB chapters / PDF pages / FB2 sections instead of plain text.
            storeOriginalFile(context, uri, document.id, extension)
            // Word / PowerPoint / spreadsheets: build a styled HTML preview so those
            // formats keep their headings, bold text, lists and tables when reading.
            val richHtml = runCatching {
                when (extension) {
                    "docx" -> extractDocxHtml(context, uri, document.id)
                    "pptx" -> extractPptxHtml(context, uri, document.id)
                    "xlsx" -> extractXlsxHtml(context, uri)
                    "csv", "tsv" -> extractCsvHtml(context, uri)
                    else -> ""
                }
            }.getOrDefault("")
            if (richHtml.isNotBlank()) {
                saveRichPreview(context, document.id, richHtml)
            }
            document
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Copies the picked file into filesDir/fio_doc_files/<docId>.<ext> (size capped). */
    private fun storeOriginalFile(context: Context, uri: Uri, docId: String, extension: String) {
        try {
            val dir = File(context.filesDir, "fio_doc_files")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, "$docId.${extension.ifBlank { "txt" }}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_ORIGINAL_BYTES) {
                            output.close()
                            target.delete()
                            return
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            // Original-file storage is best-effort: the extracted text still works.
            e.printStackTrace()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
        }
        return name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Document"
    }

    // ---------------------------------------------------------------------
    // Plain text
    // ---------------------------------------------------------------------

    private fun readPlainText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        return readTextCapped(inputStream, MAX_CONTENT_CHARS)
    }

    private fun readTextCapped(input: InputStream, maxChars: Int): String {
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val sb = StringBuilder(minOf(maxChars, 64 * 1024))
        val buf = CharArray(8192)
        try {
            while (sb.length < maxChars) {
                val n = reader.read(buf, 0, minOf(buf.size, maxChars - sb.length))
                if (n < 0) break
                sb.append(buf, 0, n)
            }
        } catch (e: Exception) {
            // Truncated/broken stream: keep what we managed to read.
        } finally {
            runCatching { reader.close() }
        }
        return sb.toString().trim()
    }

    private fun readEntryCapped(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
        val buf = ByteArray(32 * 1024)
        var total = 0
        while (total < maxBytes) {
            val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------------
    // CSV / TSV
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // EPUB (spine-ordered chapters, paragraphs preserved)
    // ---------------------------------------------------------------------

    private val FULL_PATH_REGEX = Regex("full-path\\s*=\\s*\"([^\"]+)\"")
    private val ITEM_TAG_REGEX = Regex("""<item\b[^>]*>""")
    private val ITEMREF_TAG_REGEX = Regex("""<itemref\b[^>]*>""")
    private val ID_ATTR_REGEX = Regex("\\bid\\s*=\\s*\"([^\"]+)\"")
    private val HREF_ATTR_REGEX = Regex("\\bhref\\s*=\\s*\"([^\"]+)\"")
    private val IDREF_ATTR_REGEX = Regex("\\bidref\\s*=\\s*\"([^\"]+)\"")

    private fun extractEpubText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zip = ZipInputStream(inputStream)

        // One pass over the archive: keep only chapters + package metadata.
        val chapters = LinkedHashMap<String, ByteArray>()
        var containerXml: ByteArray? = null
        val opfEntries = LinkedHashMap<String, ByteArray>()

        try {
            var entry = zip.nextEntry
            while (entry != null) {
                val lower = entry.name.lowercase()
                if (!entry.isDirectory) {
                    when {
                        lower == "meta-inf/container.xml" ->
                            containerXml = readEntryCapped(zip, MAX_ENTRY_BYTES)
                        lower.endsWith(".opf") -> {
                            val bytes = readEntryCapped(zip, MAX_ENTRY_BYTES)
                            opfEntries[entry.name] = bytes
                        }
                        lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm") -> {
                            val bytes = readEntryCapped(zip, MAX_ENTRY_BYTES)
                            if (bytes.isNotEmpty()) chapters[entry.name] = bytes
                        }
                        else -> { /* skip images, fonts, css */ }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { zip.close() }
        }

        if (chapters.isEmpty()) return ""

        val ordered = resolveSpineOrder(containerXml, opfEntries, chapters.keys)

        val sb = StringBuilder()
        for (name in ordered) {
            if (sb.length >= MAX_CONTENT_CHARS) break
            val bytes = chapters[name] ?: continue
            val chapter = htmlToText(String(bytes, Charsets.UTF_8))
            if (chapter.isNotBlank()) {
                sb.append(chapter).append("\n\n")
            }
        }
        return sb.toString().trim()
    }

    /** Reads the OPF spine so chapters come out in reading order, not zip order. */
    private fun resolveSpineOrder(
        containerXml: ByteArray?,
        opfEntries: Map<String, ByteArray>,
        chapterNames: Collection<String>
    ): List<String> {
        try {
            val containerText = containerXml?.toString(Charsets.UTF_8) ?: ""
            val opfName = FULL_PATH_REGEX.find(containerText)?.groupValues?.get(1)
                ?: opfEntries.keys.firstOrNull()
                ?: return chapterNames.toList()
            val opfText = opfEntries[opfName]?.toString(Charsets.UTF_8) ?: return chapterNames.toList()
            val opfDir = opfName.substringBeforeLast('/', "")

            val items = HashMap<String, String>()
            for (tag in ITEM_TAG_REGEX.findAll(opfText)) {
                val id = ID_ATTR_REGEX.find(tag.value)?.groupValues?.get(1) ?: continue
                val href = HREF_ATTR_REGEX.find(tag.value)?.groupValues?.get(1) ?: continue
                items[id] = href
            }

            val spineHrefs = ArrayList<String>()
            for (tag in ITEMREF_TAG_REGEX.findAll(opfText)) {
                val idref = IDREF_ATTR_REGEX.find(tag.value)?.groupValues?.get(1) ?: continue
                items[idref]?.let { spineHrefs.add(it) }
            }
            if (spineHrefs.isEmpty()) return chapterNames.toList()

            val lookup = chapterNames.associateBy { normalizeZipPath(it) }
            val resolved = spineHrefs.mapNotNull { href ->
                val clean = href.substringBefore('#').substringBefore('?')
                val full = if (opfDir.isEmpty()) clean else "$opfDir/$clean"
                lookup[normalizeZipPath(full)] ?: lookup[normalizeZipPath(clean)]
            }
            if (resolved.isEmpty()) return chapterNames.toList()

            val remaining = chapterNames.filterNot { it in resolved }
            return resolved + remaining
        } catch (e: Exception) {
            return chapterNames.toList()
        }
    }

    private fun normalizeZipPath(path: String): String =
        path.replace('\\', '/').removePrefix("./").lowercase()

    // ---------------------------------------------------------------------
    // HTML -> text (block tags become real paragraph breaks)
    // ---------------------------------------------------------------------

    private val SCRIPT_STYLE_REGEX = Regex("(?is)<(script|style|head)\\b[^>]*>.*?</(script|style|head)>")
    private val BLOCK_END_REGEX = Regex("(?is)</(p|div|h[1-6]|li|tr|blockquote|section|article|table|figure)>")
    private val BLOCK_START_REGEX = Regex("(?is)<(p|div|h[1-6]|li|tr|blockquote|section|article|table|figure)\\b[^>]*>")
    private val BR_REGEX = Regex("(?is)<br\\s*/?>")
    private val TAG_REGEX = Regex("(?s)<[^>]*>")
    private val HEX_ENTITY_REGEX = Regex("(?i)&#x([0-9a-f]+);")
    private val DECIMAL_ENTITY_REGEX = Regex("&#(\\d+);")
    private val MULTISPACE_REGEX = Regex("[ \\t\\x0B\\f]+")
    private val SPACE_AROUND_NEWLINE_REGEX = Regex(" *\\n *")
    private val MANY_NEWLINES_REGEX = Regex("\\n{3,}")

    private fun htmlToText(html: String): String {
        var s = html
        s = SCRIPT_STYLE_REGEX.replace(s, "\n")
        s = BR_REGEX.replace(s, "\n")
        s = BLOCK_END_REGEX.replace(s, "\n\n")
        s = BLOCK_START_REGEX.replace(s, "\n\n")
        s = TAG_REGEX.replace(s, " ")
        s = decodeEntities(s)
        s = s.replace("\u00a0", " ")
        s = MULTISPACE_REGEX.replace(s, " ")
        s = SPACE_AROUND_NEWLINE_REGEX.replace(s, "\n")
        s = MANY_NEWLINES_REGEX.replace(s, "\n\n")
        return s.trim()
    }

    private fun decodeEntities(text: String): String {
        var s = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
        s = HEX_ENTITY_REGEX.replace(s) { m ->
            codePointToString(m.groupValues[1].toIntOrNull(16)) ?: m.value
        }
        s = DECIMAL_ENTITY_REGEX.replace(s) { m ->
            codePointToString(m.groupValues[1].toIntOrNull()) ?: m.value
        }
        // "&amp;" is decoded last on purpose so escaped entities survive.
        return s.replace("&amp;", "&")
    }

    private fun codePointToString(code: Int?): String? {
        if (code == null || !Character.isValidCodePoint(code) || code == 0) return null
        return String(Character.toChars(code))
    }

    // ---------------------------------------------------------------------
    // DOCX
    // ---------------------------------------------------------------------

    private fun extractDocxText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zip = ZipInputStream(inputStream)
        var xml: String? = null

        try {
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                    xml = String(readEntryCapped(zip, 8 * 1024 * 1024), Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { zip.close() }
        }

        val document = xml ?: return ""
        val text = document
            .replace(Regex("<w:tab\\s*/>"), "\t")
            .replace(Regex("<w:br[^>]*/>"), "\n")
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:p\\b[^>]*>"), "")
            .replace(Regex("</w:tr>"), "\n")
            .replace(Regex("<w:tc\\b[^>]*>"), " | ")
            .replace(TAG_REGEX, " ")
        val decoded = decodeEntities(text)
        return MANY_NEWLINES_REGEX
            .replace(SPACE_AROUND_NEWLINE_REGEX.replace(MULTISPACE_REGEX.replace(decoded, " "), "\n"), "\n\n")
            .trim()
    }

    // ---------------------------------------------------------------------
    // XLSX
    // ---------------------------------------------------------------------

    private val SI_REGEX = Regex("(?is)<si\\b[^>]*>(.*?)</si>")
    private val TEXT_TAG_REGEX = Regex("(?is)<t(?:\\s[^>]*)?>(.*?)</t>")
    private val ROW_REGEX = Regex("(?is)<row\\b[^>]*>(.*?)</row>")
    private val CELL_REGEX = Regex("(?is)<c\\b([^>]*)>(.*?)</c>")
    private val CELL_REF_REGEX = Regex("\\br=\"([A-Z]+)\\d+\"")
    private val CELL_TYPE_REGEX = Regex("\\bt=\"([^\"]+)\"")
    private val VALUE_REGEX = Regex("(?is)<v>(.*?)</v>")

    private fun extractXlsxText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zip = ZipInputStream(inputStream)

        var sharedXml: ByteArray? = null
        val sheets = ArrayList<ByteArray>()
        var keptBytes = 0

        try {
            var entry = zip.nextEntry
            while (entry != null && keptBytes < 24 * 1024 * 1024) {
                val lower = entry.name.lowercase()
                if (!entry.isDirectory) {
                    when {
                        lower == "xl/sharedstrings.xml" ->
                            sharedXml = readEntryCapped(zip, 8 * 1024 * 1024)
                        lower.startsWith("xl/worksheets/") && lower.endsWith(".xml") -> {
                            val bytes = readEntryCapped(zip, 8 * 1024 * 1024)
                            if (bytes.isNotEmpty()) {
                                sheets.add(bytes)
                                keptBytes += bytes.size
                            }
                        }
                        else -> { /* skip styles, theme, media */ }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { zip.close() }
        }

        if (sheets.isEmpty()) return ""

        val shared = ArrayList<String>()
        sharedXml?.let { bytes ->
            val text = String(bytes, Charsets.UTF_8)
            for (si in SI_REGEX.findAll(text)) {
                val value = TEXT_TAG_REGEX.findAll(si.groupValues[1])
                    .joinToString("") { m -> decodeEntities(m.groupValues[1]) }
                shared.add(value)
            }
        }

        val sb = StringBuilder("# Spreadsheet Table Data\n\n")
        for (sheetBytes in sheets) {
            if (sb.length >= MAX_CONTENT_CHARS) break
            val sheetText = String(sheetBytes, Charsets.UTF_8)
            val rows = ROW_REGEX.findAll(sheetText).iterator()
            var rowCount = 0
            var firstRowInSheet = true
            while (rows.hasNext() && rowCount < MAX_SHEET_ROWS) {
                val rowMatch = rows.next()
                val values = HashMap<Int, String>()
                var maxCol = -1
                var fallbackCol = 0
                for (cell in CELL_REGEX.findAll(rowMatch.groupValues[1])) {
                    val attrs = cell.groupValues[1]
                    val inner = cell.groupValues[2]
                    val col = CELL_REF_REGEX.find(attrs)?.groupValues?.get(1)
                        ?.let { columnToIndex(it) }
                        ?: fallbackCol
                    fallbackCol = col + 1

                    val type = CELL_TYPE_REGEX.find(attrs)?.groupValues?.get(1)
                    val value = when (type) {
                        "s" -> {
                            val idx = VALUE_REGEX.find(inner)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                            shared.getOrNull(idx) ?: ""
                        }
                        "inlineStr" -> TEXT_TAG_REGEX.findAll(inner)
                            .joinToString("") { m -> decodeEntities(m.groupValues[1]) }
                        else -> VALUE_REGEX.find(inner)?.groupValues?.get(1)
                            ?.let { decodeEntities(it) } ?: ""
                    }
                    if (value.isNotBlank() && col in 0 until MAX_SHEET_COLS) {
                        values[col] = value
                        if (col > maxCol) maxCol = col
                    }
                }
                rowCount++
                if (maxCol >= 0) {
                    val line = (0..maxCol).joinToString(" | ") { values[it] ?: "" }
                    if (line.isBlank()) continue
                    sb.append("| ").append(line).append(" |\n")
                    if (firstRowInSheet) {
                        sb.append("| ").append((0..maxCol).joinToString(" | ") { "---" }).append(" |\n")
                        firstRowInSheet = false
                    }
                }
            }
            sb.append('\n')
        }
        return sb.toString().trim()
    }

    private fun columnToIndex(column: String): Int {
        var index = 0
        for (ch in column) {
            if (ch !in 'A'..'Z') return -1
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }

    // ---------------------------------------------------------------------
    // PDF (lightweight text extraction: inflate streams + read text operators)
    // ---------------------------------------------------------------------

    private val STREAM_TOKEN = "stream".toByteArray(Charsets.ISO_8859_1)
    private val ENDSTREAM_TOKEN = "endstream".toByteArray(Charsets.ISO_8859_1)
    private val OP_TJ = "Tj".toByteArray(Charsets.ISO_8859_1)
    private val OP_TJ_UPPER = "TJ".toByteArray(Charsets.ISO_8859_1)
    private val OP_BT = "BT".toByteArray(Charsets.ISO_8859_1)

    private fun extractPdfText(context: Context, uri: Uri): String {
        val bytes = readBinaryCapped(context, uri, MAX_BINARY_BYTES) ?: return ""
        if (bytes.isEmpty()) return ""

        val out = StringBuilder()
        var cursor = 0
        while (cursor < bytes.size - STREAM_TOKEN.size && out.length < MAX_CONTENT_CHARS) {
            val streamStart = findToken(bytes, STREAM_TOKEN, cursor) ?: break
            // Skip the "stream" inside "endstream".
            if (streamStart >= 3 &&
                bytes[streamStart - 3] == 'e'.code.toByte() &&
                bytes[streamStart - 2] == 'n'.code.toByte() &&
                bytes[streamStart - 1] == 'd'.code.toByte()
            ) {
                cursor = streamStart + STREAM_TOKEN.size
                continue
            }

            var dataStart = streamStart + STREAM_TOKEN.size
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val streamEnd = findToken(bytes, ENDSTREAM_TOKEN, dataStart) ?: break
            val rawLength = streamEnd - dataStart

            if (rawLength in 1..MAX_ENTRY_BYTES) {
                val raw = bytes.copyOfRange(dataStart, streamEnd)
                val decoded = inflate(raw, nowrap = false)
                    ?: inflate(raw, nowrap = true)
                    ?: raw
                val text = extractTextFromContentStream(decoded)
                if (text.isNotBlank()) {
                    out.append(text)
                    if (out.last() != '\n') out.append('\n')
                }
            }
            cursor = streamEnd + ENDSTREAM_TOKEN.size
        }

        val rawText = out.toString()
        if (rawText.isBlank()) {
            return "[No selectable text could be found in this PDF. It may be a scanned document or image-only PDF.]"
        }
        return reflowPdfText(rawText)
    }

    private fun readBinaryCapped(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            val out = ByteArrayOutputStream(minOf(maxBytes, 1024 * 1024))
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (total < maxBytes) {
                val n = stream.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            out.toByteArray()
        }
    }

    private fun findToken(bytes: ByteArray, token: ByteArray, from: Int): Int? {
        val limit = bytes.size - token.size
        if (limit < 0) return null
        var i = from.coerceAtLeast(0)
        while (i <= limit) {
            var j = 0
            while (j < token.size && bytes[i + j] == token[j]) j++
            if (j == token.size) return i
            i++
        }
        return null
    }

    private fun inflate(data: ByteArray, nowrap: Boolean): ByteArray? {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(minOf(data.size * 4, 256 * 1024))
            val buf = ByteArray(32 * 1024)
            var stalls = 0
            while (!inflater.finished() && stalls < 4) {
                val n = inflater.inflate(buf)
                if (n > 0) {
                    stalls = 0
                    if (out.size() + n > MAX_INFLATED_BYTES) break
                    out.write(buf, 0, n)
                } else {
                    stalls++
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
            }
            if (out.size() == 0) null else out.toByteArray()
        } catch (e: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun isWhitespaceByte(b: Byte): Boolean {
        val v = b.toInt()
        return v == 0 || v == 9 || v == 10 || v == 12 || v == 13 || v == 32
    }

    private fun printableRatio(data: ByteArray): Float {
        if (data.isEmpty()) return 0f
        var printable = 0
        for (b in data) {
            val v = b.toInt() and 0xFF
            if (v in 32..126 || v == 9 || v == 10 || v == 13) printable++
        }
        return printable.toFloat() / data.size
    }

    /**
     * Minimal PDF content-stream interpreter: pulls the strings shown by
     * Tj/TJ/'/" operators and inserts line breaks for the positioning operators.
     */
    private fun extractTextFromContentStream(data: ByteArray): String {
        if (data.isEmpty()) return ""
        if (findToken(data, OP_TJ, 0) == null &&
            findToken(data, OP_TJ_UPPER, 0) == null &&
            findToken(data, OP_BT, 0) == null
        ) return ""
        if (printableRatio(data) < 0.85f) return "" // images / embedded fonts

        val out = StringBuilder(2048)
        val operands = ArrayList<String>(4)
        val n = data.size
        var i = 0

        while (i < n) {
            val b = data[i]
            val v = b.toInt() and 0xFF
            when {
                v == '('.code -> {
                    val (text, next) = parseLiteralString(data, i)
                    operands.add(text)
                    i = next
                }
                v == '<'.code && i + 1 < n && data[i + 1].toInt() != '<'.code -> {
                    val (text, next) = parseHexString(data, i)
                    operands.add(text)
                    i = next
                }
                v == '<'.code && i + 1 < n && data[i + 1].toInt() == '<'.code -> {
                    // Dictionary: skip it entirely.
                    i += 2
                    while (i + 1 < n &&
                        !(data[i].toInt() == '>'.code && data[i + 1].toInt() == '>'.code)
                    ) i++
                    i = minOf(i + 2, n)
                }
                v == '%'.code -> {
                    while (i < n && data[i].toInt() != '\n'.code && data[i].toInt() != '\r'.code) i++
                }
                v == '['.code || v == ']'.code -> i++
                isWhitespaceByte(b) -> i++
                else -> {
                    val start = i
                    while (i < n) {
                        val c = data[i].toInt()
                        if (isWhitespaceByte(data[i]) ||
                            c == '('.code || c == '<'.code || c == '['.code ||
                            c == ']'.code || c == '%'.code
                        ) break
                        i++
                    }
                    if (i == start) {
                        i++
                        continue
                    }
                    val token = String(data, start, i - start, Charsets.ISO_8859_1)
                    handlePdfOperator(token, operands, out)
                }
            }
        }
        return out.toString()
    }

    private fun handlePdfOperator(token: String, operands: MutableList<String>, out: StringBuilder) {
        when (token) {
            "Tj", "TJ" -> {
                for (op in operands) out.append(op)
                operands.clear()
            }
            "'", "\"" -> {
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                for (op in operands) out.append(op)
                out.append('\n')
                operands.clear()
            }
            "Td", "TD", "T*", "BT", "ET" -> {
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                operands.clear()
            }
            else -> {
                // Numbers, names (/Font0), Tf, Tm, ... - nothing to render.
            }
        }
    }

    private fun parseLiteralString(data: ByteArray, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        var depth = 1
        val n = data.size
        while (i < n) {
            val v = data[i].toInt() and 0xFF
            when {
                v == '\\'.code -> {
                    if (i + 1 >= n) break
                    val next = data[i + 1].toInt() and 0xFF
                    when {
                        next == 'n'.code -> { sb.append('\n'); i += 2 }
                        next == 'r'.code -> { sb.append('\r'); i += 2 }
                        next == 't'.code -> { sb.append('\t'); i += 2 }
                        next == 'b'.code || next == 'f'.code -> i += 2
                        next == '('.code || next == ')'.code || next == '\\'.code -> {
                            sb.append(next.toChar())
                            i += 2
                        }
                        next == '\r'.code -> {
                            i += 2
                            if (i < n && data[i].toInt() == '\n'.code) i++
                        }
                        next == '\n'.code -> i += 2
                        next in '0'.code..'7'.code -> {
                            var value = 0
                            var count = 0
                            while (i + 1 < n && count < 3) {
                                val digit = data[i + 1].toInt() and 0xFF
                                if (digit !in '0'.code..'7'.code) break
                                value = value * 8 + (digit - '0'.code)
                                i++
                                count++
                            }
                            sb.append((value and 0xFF).toChar())
                            i++
                        }
                        else -> { sb.append(next.toChar()); i += 2 }
                    }
                }
                v == '('.code -> { depth++; sb.append('('); i++ }
                v == ')'.code -> {
                    depth--
                    i++
                    if (depth == 0) return sb.toString() to i
                }
                else -> { sb.append(v.toChar()); i++ }
            }
        }
        return sb.toString() to i
    }

    private fun parseHexString(data: ByteArray, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        var pendingHigh = -1
        val n = data.size
        while (i < n) {
            val v = data[i].toInt() and 0xFF
            if (v == '>'.code) {
                i++
                break
            }
            val digit = hexDigit(v)
            if (digit >= 0) {
                if (pendingHigh < 0) {
                    pendingHigh = digit
                } else {
                    sb.append(((pendingHigh shl 4) or digit).toChar())
                    pendingHigh = -1
                }
            }
            i++
        }
        if (pendingHigh >= 0) sb.append(pendingHigh.toChar())
        return sb.toString() to i
    }

    private fun hexDigit(byteValue: Int): Int = when {
        byteValue in '0'.code..'9'.code -> byteValue - '0'.code
        byteValue in 'a'.code..'f'.code -> byteValue - 'a'.code + 10
        byteValue in 'A'.code..'F'.code -> byteValue - 'A'.code + 10
        else -> -1
    }

    /**
     * PDFs are extracted as one line per typeset row, which reads terribly on a
     * phone screen. Re-flow the rows into real paragraphs:
     *  - rows inside a paragraph are joined with spaces,
     *  - "end-of-word-\ncontinuation" is rejoined without the hyphen,
     *  - a clearly short trailing row ends its paragraph,
     *  - a sentence that ends on a line end, followed by a capitalized row,
     *    also breaks the paragraph (classic typeset paragraph shape).
     */
    private fun reflowPdfText(raw: String): String {
        val lines = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        val sortedLengths = lines.map { it.length }.sorted()
        val avgLine = sortedLengths[sortedLengths.size / 2]
        val shortLine = maxOf(24, (avgLine * 0.55f).toInt())

        val out = StringBuilder(raw.length)
        val para = StringBuilder()
        val flush = {
            if (para.isNotBlank()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append(para.toString().trim())
            }
            para.setLength(0)
            Unit
        }

        for (i in lines.indices) {
            val line = lines[i]
            val next = if (i + 1 < lines.size) lines[i + 1] else null

            when {
                // "complet-\nion" was one word split across two typeset rows.
                para.isNotEmpty() && para.last() == '-' &&
                    line.first().isLowerCase() -> {
                    para.deleteCharAt(para.length - 1)
                    para.append(line)
                }
                para.isNotEmpty() -> para.append(' ').append(line)
                else -> para.append(line)
            }

            val endChar = line.last()
            val sentenceEnded = endChar == '.' || endChar == '!' || endChar == '?' ||
                endChar == '"' || endChar == '\u201d' || endChar == '\''
            val nextStartsUpper = next != null && next.first().isUpperCase()

            // Short row = last line of a typeset paragraph; long rows + sentence end
            // followed by a capitalized row = most likely a paragraph boundary too.
            if (next == null || line.length < shortLine ||
                (sentenceEnded && nextStartsUpper && para.length > 200) ||
                para.length > 700
            ) {
                flush()
            }
        }
        flush()
        return out.toString()
    }

    // ---------------------------------------------------------------------
    // Rich previews: Word / PowerPoint / spreadsheets rendered as styled HTML
    // ---------------------------------------------------------------------

    private fun saveRichPreview(context: Context, docId: String, html: String) {
        try {
            val dir = File(context.filesDir, "fio_reader_text")
            if (!dir.exists()) dir.mkdirs()
            // v2 = rewritten Office converter (proper tags, tables, images)
            File(dir, "$docId.v2.md").writeText(html)
            File(dir, "$docId.md").delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Builds the styled HTML preview for an already-stored original file.
     * Used lazily so documents imported before rich previews existed still render well.
     */
    fun buildRichPreviewForFile(context: Context, docId: String, source: File): Boolean {
        if (!source.isFile) return false
        val html = runCatching {
            when (source.extension.lowercase()) {
                "docx" -> extractDocxHtml(context, Uri.fromFile(source), docId)
                "pptx" -> extractPptxHtml(context, Uri.fromFile(source), docId)
                "xlsx" -> extractXlsxHtml(context, Uri.fromFile(source))
                "csv", "tsv" -> extractCsvHtml(context, Uri.fromFile(source))
                else -> ""
            }
        }.getOrDefault("")
        if (html.isBlank()) return false
        saveRichPreview(context, docId, html)
        return true
    }

    private fun readZipEntryText(context: Context, uri: Uri, entryName: String): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var result: String? = null
                while (entry != null) {
                    if (entry.name.equals(entryName, ignoreCase = true)) {
                        result = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                        break
                    }
                    entry = zip.nextEntry
                }
                result
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun escapeHtmlText(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun decodeXmlEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // ---- Word (.docx) ----

    private fun extractDocxHtml(context: Context, uri: Uri, docId: String): String {
        val xml = readZipEntryText(context, uri, "word/document.xml") ?: return ""
        val relsXml = readZipEntryText(context, uri, "word/_rels/document.xml.rels") ?: ""
        val rels = parseRelationships(relsXml)
        val mediaDir = extractOfficeMedia(context, uri, docId, "word/")
        val body = Regex("(?s)<w:body(?:\\s[^>]*)?>(.*)</w:body>").find(xml)?.groupValues?.get(1) ?: xml
        return docxBodyToHtml(body, rels, mediaDir)
    }

    private fun parseRelationships(relsXml: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (match in Regex("<Relationship\\b[^>]*>").findAll(relsXml)) {
            val tag = match.value
            if (tag.contains("TargetMode=\"External\"")) continue
            val id = Regex("Id=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: continue
            val target = Regex("Target=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: continue
            map[id] = target
        }
        return map
    }

    /** Extracts embedded images next to the preview so the reader can show them. */
    private fun extractOfficeMedia(context: Context, uri: Uri, docId: String, prefix: String): String {
        val dirName = "${docId}_media"
        val outDir = File(context.filesDir, "fio_reader_text/$dirName")
        outDir.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name.startsWith(prefix) &&
                            Regex("(?i)\\.(png|jpe?g|gif|webp|bmp|svg)$").containsMatchIn(name)
                        ) {
                            val relative = name.substring(prefix.length)
                            val target = File(outDir, relative.replace('/', '_'))
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out -> zip.copyTo(out) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dirName
    }

    private fun mediaFileName(target: String): String =
        target.replace("../", "").replace('/', '_')

    private fun docxBodyToHtml(
        body: String,
        rels: Map<String, String>,
        mediaDir: String,
    ): String {
        val out = StringBuilder()
        var listOpen = false
        fun closeList() {
            if (listOpen) {
                out.append("</ul>")
                listOpen = false
            }
        }
        val paragraphTag = Regex("<w:p(?:\\s[^>]*)?>")
        val tableTag = Regex("<w:tbl(?:\\s[^>]*)?>")
        var index = 0
        while (index < body.length) {
            val nextParagraph = paragraphTag.find(body, index)
            val nextTable = tableTag.find(body, index)
            if (nextParagraph == null && nextTable == null) break
            val useTable = nextTable != null &&
                (nextParagraph == null || nextTable.range.first <= nextParagraph.range.first)
            if (useTable) {
                val tagEnd = nextTable!!.range.last
                val end = body.indexOf("</w:tbl>", tagEnd + 1)
                if (end < 0) break
                closeList()
                out.append(docxTableToHtml(body.substring(tagEnd + 1, end), rels, mediaDir))
                index = end + "</w:tbl>".length
            } else {
                val tagEnd = nextParagraph!!.range.last
                val end = body.indexOf("</w:p>", tagEnd + 1)
                if (end < 0) break
                val (html, isBullet) = docxParagraphToHtml(body.substring(tagEnd + 1, end), rels, mediaDir)
                if (html.isNotEmpty()) {
                    if (isBullet) {
                        if (!listOpen) {
                            out.append("<ul>")
                            listOpen = true
                        }
                        out.append(html)
                    } else {
                        closeList()
                        out.append(html)
                    }
                }
                index = end + "</w:p>".length
            }
        }
        closeList()
        return out.toString()
    }

    private fun docxParagraphToHtml(
        paragraphXml: String,
        rels: Map<String, String>,
        mediaDir: String,
    ): Pair<String, Boolean> {
        val style = Regex("<w:pStyle\\b[^>]*w:val=\"([^\"]+)\"").find(paragraphXml)
            ?.groupValues?.get(1) ?: ""
        val isBullet = paragraphXml.contains("<w:numPr")
        val html = StringBuilder()

        Regex("<a:blip\\b[^>]*r:embed=\"([^\"]+)\"").findAll(paragraphXml).forEach { blip ->
            val target = rels[blip.groupValues[1]] ?: return@forEach
            html.append("<img src=\"")
                .append(mediaDir)
                .append('/')
                .append(mediaFileName(target))
                .append("\">")
        }

        for (run in Regex("(?s)<w:r(?:\\s[^>]*)?>.*?</w:r>").findAll(paragraphXml)) {
            html.append(docxRunToHtml(run.value))
        }
        if (html.isBlank()) return "" to false

        val level = when {
            style.startsWith("Heading", ignoreCase = true) ->
                style.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 4) ?: 2
            style.equals("Title", ignoreCase = true) -> 1
            style.equals("Subtitle", ignoreCase = true) -> 2
            else -> 0
        }
        return when {
            level > 0 -> "<h$level>$html</h$level>" to false
            isBullet -> "<li>$html</li>" to true
            else -> "<p>$html</p>" to false
        }
    }

    private fun docxRunToHtml(runXml: String): String {
        val propBlock = Regex("(?s)<w:rPr>(.*?)</w:rPr>").find(runXml)?.groupValues?.get(1) ?: ""
        val bold = Regex("<w:b(?:\\s+w:val=\"(?!0|false)[^\"]*\")?\\s*/>").containsMatchIn(propBlock)
        val italic = Regex("<w:i(?:\\s+w:val=\"(?!0|false)[^\"]*\")?\\s*/>").containsMatchIn(propBlock)
        val underline = Regex("<w:u\\b[^>]*/>").containsMatchIn(propBlock)
        val color = Regex("<w:color\\b[^>]*w:val=\"([0-9A-Fa-f]{6})\"").find(propBlock)
            ?.groupValues?.get(1)

        val text = StringBuilder()
        Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br\\s*/>").findAll(runXml).forEach { token ->
            when {
                token.value.startsWith("<w:tab") -> text.append(' ')
                token.value.startsWith("<w:br") -> text.append("<br>")
                else -> text.append(escapeHtmlText(decodeXmlEntities(token.groupValues[1])))
            }
        }
        if (text.isBlank()) return ""
        var piece = text.toString()
        if (bold) piece = "<strong>$piece</strong>"
        if (italic) piece = "<em>$piece</em>"
        if (underline) piece = "<u>$piece</u>"
        if (color != null) piece = "<span style=\"color:#$color\">$piece</span>"
        return piece
    }

    private fun docxTableToHtml(
        tableXml: String,
        rels: Map<String, String>,
        mediaDir: String,
    ): String {
        val out = StringBuilder("<table>")
        var rowIndex = 0
        for (row in Regex("(?s)<w:tr(?:\\s[^>]*)?>.*?</w:tr>").findAll(tableXml)) {
            out.append("<tr>")
            val cellTag = if (rowIndex == 0) "th" else "td"
            for (cell in Regex("(?s)<w:tc(?:\\s[^>]*)?>.*?</w:tc>").findAll(row.value)) {
                val span = Regex("<w:gridSpan\\b[^>]*w:val=\"(\\d+)\"").find(cell.value)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val paragraphs = Regex("(?s)<w:p(?:\\s[^>]*)?>.*?</w:p>").findAll(cell.value)
                val text = if (paragraphs.any()) {
                    paragraphs.joinToString("<br>") { paragraph ->
                        docxCellText(paragraph.value)
                    }
                } else {
                    docxCellText(cell.value)
                }
                out.append("<").append(cellTag)
                if (span > 1) out.append(" colspan=\"$span\"")
                out.append(">").append(text).append("</").append(cellTag).append(">")
            }
            out.append("</tr>")
            rowIndex++
        }
        out.append("</table>")
        return out.toString()
    }

    private fun docxCellText(xml: String): String =
        Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>").findAll(xml)
            .joinToString("") { escapeHtmlText(decodeXmlEntities(it.groupValues[1])) }
            .trim()

    // ---- PowerPoint (.pptx) ----

    private fun extractPptxSlides(context: Context, uri: Uri): List<Pair<Int, List<String>>> {
        val slides = mutableListOf<Pair<Int, List<String>>>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val match = Regex("^ppt/slides/slide(\\d+)\\.xml$").find(name)
                        if (match != null) {
                            val idx = match.groupValues[1].toIntOrNull() ?: 0
                            val xml = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                            val texts = Regex("(?s)<a:t[^>]*>(.*?)</a:t>").findAll(xml)
                                .map { decodeXmlEntities(it.groupValues[1]).trim() }
                                .filter { it.isNotEmpty() }
                                .toList()
                            slides.add(idx to texts)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return slides.sortedBy { it.first }
    }

    private fun extractPptxText(context: Context, uri: Uri): String {
        val slides = extractPptxSlides(context, uri)
        if (slides.isEmpty()) return ""
        return slides.joinToString("\n\n") { (idx, texts) ->
            "Slide $idx\n" + texts.joinToString("\n")
        }
    }

    private fun extractPptxHtml(context: Context, uri: Uri, docId: String): String {
        val mediaDir = extractOfficeMedia(context, uri, docId, "ppt/")
        val slideXml = HashMap<Int, String>()
        val slideRels = HashMap<Int, Map<String, String>>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val slideMatch = Regex("^ppt/slides/slide(\\d+)\\.xml$").find(name)
                        val relsMatch = Regex("^ppt/slides/_rels/slide(\\d+)\\.xml\\.rels$").find(name)
                        if (slideMatch != null) {
                            val idx = slideMatch.groupValues[1].toIntOrNull() ?: 0
                            slideXml[idx] = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                        } else if (relsMatch != null) {
                            val idx = relsMatch.groupValues[1].toIntOrNull() ?: 0
                            val xml = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                            slideRels[idx] = parseRelationships(xml)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (slideXml.isEmpty()) return ""
        val out = StringBuilder()
        slideXml.keys.sorted().forEachIndexed { order, index ->
            val xml = slideXml[index] ?: return@forEachIndexed
            out.append("<h2>Slide ${order + 1}</h2>")

            val rels = slideRels[index] ?: emptyMap()
            Regex("<a:blip\\b[^>]*r:embed=\"([^\"]+)\"").findAll(xml).forEach { blip ->
                rels[blip.groupValues[1]]?.let { target ->
                    out.append("<img src=\"")
                        .append(mediaDir)
                        .append('/')
                        .append(mediaFileName(target))
                        .append("\">")
                }
            }

            var titleWritten = false
            val bullets = mutableListOf<String>()
            Regex("(?s)<a:p(?:\\s[^>]*)?>.*?</a:p>").findAll(xml).forEach { paragraph ->
                val text = Regex("<a:t(?:\\s[^>]*)?>(.*?)</a:t>").findAll(paragraph.value)
                    .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                    .trim()
                if (text.isEmpty()) return@forEach
                if (!titleWritten) {
                    out.append("<h3>").append(escapeHtmlText(text)).append("</h3>")
                    titleWritten = true
                } else {
                    bullets.add(text)
                }
            }
            if (bullets.isNotEmpty()) {
                out.append("<ul>")
                bullets.forEach { out.append("<li>").append(escapeHtmlText(it)).append("</li>") }
                out.append("</ul>")
            }
        }
        return out.toString()
    }

    // ---- Spreadsheets (.xlsx / .csv / .tsv) ----

    private fun extractXlsxHtml(context: Context, uri: Uri): String {
        var shared = emptyList<String>()
        val sheetNames = mutableListOf<String>()
        val sheets = mutableListOf<Pair<Int, String>>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        when {
                            name == "xl/sharedstrings.xml" -> {
                                val xml = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                                shared = Regex("(?s)<si>(.*?)</si>").findAll(xml).map { si ->
                                    Regex("(?s)<t[^>]*>(.*?)</t>").findAll(si.groupValues[1])
                                        .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                                }.toList()
                            }
                            name == "xl/workbook.xml" -> {
                                val xml = readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8)
                                sheetNames.addAll(
                                    Regex("<sheet\\b[^>]*\\bname=\"([^\"]*)\"").findAll(xml)
                                        .map { decodeXmlEntities(it.groupValues[1]) }
                                )
                            }
                            Regex("^xl/worksheets/sheet(\\d+)\\.xml$").matches(name) -> {
                                val idx = Regex("sheet(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                sheets.add(idx to readEntryCapped(zip, MAX_ENTRY_BYTES).toString(Charsets.UTF_8))
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (sheets.isEmpty()) return ""
        val out = StringBuilder()
        sheets.sortedBy { it.first }.forEachIndexed { index, (_, xml) ->
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
            out.append("<h2>").append(escapeHtmlText(sheetName)).append("</h2>")
            out.append("<table>")
            for (row in Regex("(?s)<row\\b[^>]*>(.*?)</row>").findAll(xml)) {
                out.append("<tr>")
                for (cell in Regex("(?s)<c\\b([^>]*)>(.*?)</c>").findAll(row.groupValues[1])) {
                    val attrs = cell.groupValues[1]
                    val inner = cell.groupValues[2]
                    val isString = attrs.contains("t=\"s\"")
                    val rawValue = Regex("<v>(.*?)</v>").find(inner)?.groupValues?.get(1)
                    val inlineValue = Regex("<t[^>]*>(.*?)</t>").find(inner)?.groupValues?.get(1)
                    val value = when {
                        inlineValue != null -> decodeXmlEntities(inlineValue)
                        rawValue != null && isString ->
                            shared.getOrNull(rawValue.toIntOrNull() ?: -1) ?: ""
                        rawValue != null -> decodeXmlEntities(rawValue)
                        else -> ""
                    }
                    out.append("<td>").append(escapeHtmlText(value.trim())).append("</td>")
                }
                out.append("</tr>")
            }
            out.append("</table>")
        }
        return out.toString()
    }

    private fun extractCsvHtml(context: Context, uri: Uri): String {
        val plain = readPlainText(context, uri)
        if (plain.isBlank()) return ""
        val delimiter = if (plain.lineSequence().firstOrNull()?.contains('\t') == true) '\t' else ','
        val out = StringBuilder("<h2>Table</h2><table>")
        plain.lines()
            .filter { it.isNotBlank() }
            .take(500)
            .forEach { line ->
                out.append("<tr>")
                line.split(delimiter).forEach { cell ->
                    out.append("<td>").append(escapeHtmlText(cell.trim().trim('"'))).append("</td>")
                }
                out.append("</tr>")
            }
        out.append("</table>")
        return out.toString()
    }
}
