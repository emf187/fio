package com.example.fio.ui.reader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.areada.data.DocumentResolver
import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReaderDocument
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderStateStore
import app.areada.data.reader.ReaderThemeMode
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingBookmarkActions
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.renderPalette
import app.areada.reader.epub.EpubBook
import app.areada.reader.epub.EpubEngine
import app.areada.reader.fb2.Fb2Book
import app.areada.reader.fb2.Fb2Engine
import app.areada.reader.markdown.MarkdownBook
import app.areada.reader.markdown.MarkdownEngine
import app.areada.ui.reader.EpubReaderScreen
import app.areada.ui.reader.Fb2ReaderScreen
import app.areada.ui.reader.MarkdownReaderScreen
import app.areada.ui.reader.PdfReaderScreen
import app.areada.ui.reader.ReaderScreen
import app.areada.ui.reader.TextReaderScreen
import app.areada.ui.theme.ReaderTheme
import com.example.fio.data.document.DocumentImporter
import com.example.fio.data.model.StudyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Bridges a Fio [StudyDocument] into the Areada e-reader screens.
 *
 * Fio stores the extracted text for AI/search plus (since the Areada port) the
 * original imported file next to it. The original file is what gets rendered:
 * EPUB/FB2/Markdown become chapter-based HTML, PDF becomes real rendered pages,
 * TXT/MD fall back to the extracted text when no original is available.
 */
private sealed interface HostState {
    data object Loading : HostState
    data class Failed(val message: String) : HostState
    data class Ready(
        val document: ReaderDocument,
        val preferences: ReaderPreferences,
        val bookmarks: List<ReadingBookmark>,
        val progress: ReadingProgress?,
        val epubBook: EpubBook? = null,
        val fb2Book: Fb2Book? = null,
        val markdownBook: MarkdownBook? = null,
        val textContent: String? = null,
    ) : HostState
}

@Composable
fun FioReaderHost(
    document: StudyDocument,
    onClose: (percent: Int, position: Int) -> Unit,
    onProgress: (percent: Int, position: Int) -> Unit,
) {
    val context = LocalContext.current.applicationContext

    var state by remember(document.id) { mutableStateOf<HostState>(HostState.Loading) }
    var latestPercent by remember(document.id) { mutableIntStateOf(0) }
    var latestPosition by remember(document.id) { mutableIntStateOf(0) }

    LaunchedEffect(document.id) {
        state = HostState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                val file = resolveDocumentFile(context, document)
                val uri = Uri.fromFile(file)
                val type = DocumentResolver.detectSupportedType(
                    null,
                    originalDisplayName(context, document, file)
                ) ?: DocumentType.TXT
                val readerDocument = ReaderDocument(
                    uri = uri,
                    uriString = uri.toString(),
                    title = document.title,
                    type = type,
                )
                val preferences = ReaderStateStore.loadPreferences(context)
                val bookmarks = ReaderStateStore.loadBookmarks(context)
                val progress = ReaderStateStore.loadProgress(context)[readerDocument.uriString]

                // Word / PowerPoint / spreadsheet previews are stored as styled HTML
                // and read through the Markdown/HTML reader so formatting is preserved.
                val richPreview = File(context.filesDir, "fio_reader_text/${document.id}.v2.md")
                val richFormat = document.format.uppercase() in setOf("DOCX", "SLIDES", "SPREADSHEET")
                if (richFormat && !richPreview.isFile) {
                    // Older imports: generate the styled preview on demand from the
                    // stored original file, then open it below.
                    DocumentImporter.buildRichPreviewForFile(context, document.id, file)
                }
                if (richFormat && richPreview.isFile) {
                    val previewUri = Uri.fromFile(richPreview)
                    val previewDocument = readerDocument.copy(
                        uri = previewUri,
                        uriString = previewUri.toString(),
                        type = DocumentType.MARKDOWN,
                    )
                    val previewProgress = ReaderStateStore.loadProgress(context)[previewDocument.uriString]
                    val book = MarkdownEngine.parse(context, previewUri, document.title)
                    return@withContext HostState.Ready(
                        previewDocument,
                        preferences,
                        bookmarks,
                        previewProgress,
                        markdownBook = book,
                    )
                }

                when (type) {
                    DocumentType.EPUB -> {
                        val book = EpubEngine.parse(context, uri, document.title)
                        if (book.chapters.isEmpty()) {
                            HostState.Failed("This EPUB has no readable chapters.")
                        } else {
                            HostState.Ready(readerDocument, preferences, bookmarks, progress, epubBook = book)
                        }
                    }
                    DocumentType.FB2 -> {
                        val book = Fb2Engine.parse(context, uri, document.title)
                        if (book.chapters.isEmpty()) {
                            HostState.Failed("This FB2 file has no readable sections.")
                        } else {
                            HostState.Ready(readerDocument, preferences, bookmarks, progress, fb2Book = book)
                        }
                    }
                    DocumentType.MARKDOWN -> {
                        val book = MarkdownEngine.parse(context, uri, document.title)
                        HostState.Ready(readerDocument, preferences, bookmarks, progress, markdownBook = book)
                    }
                    DocumentType.PDF -> {
                        HostState.Ready(readerDocument, preferences, bookmarks, progress)
                    }
                    else -> {
                        // TXT and formats Areada cannot render natively (DOCX, XLSX, CSV):
                        // use Fio's already-extracted text.
                        val text = if (file.extension.equals("txt", true)) {
                            runCatching { file.readText() }.getOrElse { document.content }
                        } else {
                            document.content
                        }
                        HostState.Ready(
                            readerDocument.copy(type = DocumentType.TXT),
                            preferences,
                            bookmarks,
                            progress,
                            textContent = text.ifBlank { document.content },
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e("FioReader", "Could not open document ${document.title}", t)
                HostState.Failed(t.message ?: "Could not open this document.")
            }
        }
        val ready = state as? HostState.Ready
        if (ready != null) {
            Log.d("FioReader", "Opened ${document.title} as ${ready.document.type} (${ready.document.uriString})")
            val progress = ready.progress
            if (progress != null) {
                latestPercent = progressPercent(progress).coerceAtLeast(document.readingProgressPercent)
                latestPosition = progress.positionIndex()
            }
        } else if (state is HostState.Failed) {
            Log.e("FioReader", "Reader failed: ${(state as HostState.Failed).message}")
        }
    }

    fun persistProgress(progress: ReadingProgress) {
        ReaderStateStore.saveProgress(
            context,
            ReaderStateStore.loadProgress(context) + (progress.uriString to progress)
        )
        latestPercent = progressPercent(progress)
        latestPosition = progress.positionIndex()
        onProgress(latestPercent, latestPosition)
    }

    fun close() {
        onClose(latestPercent, latestPosition)
    }

    when (val current = state) {
        is HostState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Opening ${document.title}...",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        is HostState.Failed -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { close() }) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        is HostState.Ready -> {
            // Resolve "Follow system" once so the chrome, the WebView palette and the
            // window background all agree: ANDROID can otherwise pick dark chrome vs
            // light page colours and make the reader appear to flash between themes.
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val resolvedMode =
                if (current.preferences.themeMode == ReaderThemeMode.ANDROID) {
                    if (systemDark) ReaderThemeMode.DARK else ReaderThemeMode.LIGHT
                } else {
                    current.preferences.themeMode
                }
            val palette = resolvedMode.renderPalette()
            val paletteColor = Color(parseColorSafe(palette.backgroundHex))
            Box(modifier = Modifier.fillMaxSize().background(paletteColor)) {
                ReaderTheme(mode = resolvedMode) {
                when {
                    current.epubBook != null -> EpubReaderScreen(
                        screen = ReaderScreen.Epub(
                            document = current.document,
                            book = current.epubBook,
                            initialChapterIndex = current.progress?.epubChapterIndex
                                ?.coerceIn(0, current.epubBook.chapters.lastIndex) ?: 0,
                            initialScrollFraction = current.progress?.epubScrollFraction
                                ?.coerceIn(0f, 1f) ?: 0f,
                        ),
                        preferences = current.preferences,
                        bookmarks = current.bookmarks,
                        onBack = { close() },
                        onPreferencesChange = { updated ->
                            ReaderStateStore.savePreferences(context, updated)
                            state = current.copy(preferences = updated)
                        },
                        onOpenBookNote = {},
                        onToggleBookmark = { chapterIndex, chapterCount, scrollFraction, chapterTitle ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createEpubBookmark(
                                        current.document, chapterIndex, chapterCount,
                                        scrollFraction, chapterTitle
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onSaveProgress = { chapterIndex, chapterCount, scrollFraction ->
                            persistProgress(
                                ReadingProgress(
                                    uriString = current.document.uriString,
                                    type = DocumentType.EPUB,
                                    epubChapterIndex = chapterIndex,
                                    epubChapterCount = chapterCount,
                                    epubScrollFraction = scrollFraction,
                                )
                            )
                        },
                    )

                    current.fb2Book != null -> Fb2ReaderScreen(
                        screen = ReaderScreen.Fb2(
                            document = current.document,
                            book = current.fb2Book,
                            initialChapterIndex = current.progress?.epubChapterIndex
                                ?.coerceIn(0, current.fb2Book.chapters.lastIndex) ?: 0,
                            initialScrollFraction = current.progress?.epubScrollFraction
                                ?.coerceIn(0f, 1f) ?: 0f,
                        ),
                        preferences = current.preferences,
                        bookmarks = current.bookmarks,
                        onBack = { close() },
                        onPreferencesChange = { updated ->
                            ReaderStateStore.savePreferences(context, updated)
                            state = current.copy(preferences = updated)
                        },
                        onOpenBookNote = {},
                        onToggleBookmark = { chapterIndex, chapterCount, scrollFraction, chapterTitle ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createEpubBookmark(
                                        current.document, chapterIndex, chapterCount,
                                        scrollFraction, chapterTitle
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onSaveProgress = { chapterIndex, chapterCount, scrollFraction ->
                            persistProgress(
                                ReadingProgress(
                                    uriString = current.document.uriString,
                                    type = DocumentType.FB2,
                                    epubChapterIndex = chapterIndex,
                                    epubChapterCount = chapterCount,
                                    epubScrollFraction = scrollFraction,
                                )
                            )
                        },
                    )

                    current.markdownBook != null -> MarkdownReaderScreen(
                        screen = ReaderScreen.Markdown(
                            document = current.document,
                            book = current.markdownBook,
                            initialChapterIndex = 0,
                            initialScrollFraction = current.progress?.epubScrollFraction
                                ?.coerceIn(0f, 1f) ?: 0f,
                        ),
                        preferences = current.preferences,
                        bookmarks = current.bookmarks,
                        onBack = { close() },
                        onPreferencesChange = { updated ->
                            ReaderStateStore.savePreferences(context, updated)
                            state = current.copy(preferences = updated)
                        },
                        onOpenBookNote = {},
                        onToggleBookmark = { chapterIndex, chapterCount, scrollFraction, chapterTitle ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createEpubBookmark(
                                        current.document, chapterIndex, chapterCount,
                                        scrollFraction, chapterTitle
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onSaveProgress = { chapterIndex, chapterCount, scrollFraction ->
                            persistProgress(
                                ReadingProgress(
                                    uriString = current.document.uriString,
                                    type = DocumentType.MARKDOWN,
                                    epubChapterIndex = chapterIndex,
                                    epubChapterCount = chapterCount,
                                    epubScrollFraction = scrollFraction,
                                )
                            )
                        },
                    )

                    current.document.type == DocumentType.PDF -> PdfReaderScreen(
                        screen = ReaderScreen.Pdf(
                            document = current.document,
                            initialPageIndex = current.progress?.pdfPageIndex?.coerceAtLeast(0) ?: 0,
                            initialZoomScale = current.progress?.pdfZoomScale?.coerceIn(1f, 5f) ?: 1f,
                            initialExtractedTextEnabled = current.progress?.pdfExtractedTextEnabled ?: false,
                            initialExtractedTextPageIndex = current.progress?.pdfExtractedTextPageIndex ?: 0,
                            initialExtractedTextScrollMode = current.progress?.pdfExtractedTextScrollMode ?: false,
                        ),
                        preferences = current.preferences,
                        bookmarks = current.bookmarks,
                        onBack = { close() },
                        onPreferencesChange = { updated ->
                            ReaderStateStore.savePreferences(context, updated)
                            state = current.copy(preferences = updated)
                        },
                        onOpenBookNote = {},
                        onToggleBookmark = { pageIndex, pageCount ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createPdfBookmark(
                                        current.document, pageIndex, pageCount
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onToggleExtractedTextBookmark = { sectionIndex, sectionCount, scrollFraction ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createPdfExtractedTextBookmark(
                                        current.document, sectionIndex, sectionCount, scrollFraction
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onSaveProgress = { pageIndex, pageCount, zoomScale ->
                            persistProgress(
                                ReadingProgress(
                                    uriString = current.document.uriString,
                                    type = DocumentType.PDF,
                                    pdfPageIndex = pageIndex,
                                    pdfPageCount = pageCount,
                                    pdfZoomScale = zoomScale,
                                    pdfExtractedTextEnabled = current.progress?.pdfExtractedTextEnabled ?: false,
                                    pdfExtractedTextPageIndex = current.progress?.pdfExtractedTextPageIndex ?: 0,
                                    pdfExtractedTextScrollMode = current.progress?.pdfExtractedTextScrollMode ?: false,
                                )
                            )
                        },
                        onSaveExtractedTextState = { enabled, pageIndex, scrollMode ->
                            val progress = ReadingProgress(
                                uriString = current.document.uriString,
                                type = DocumentType.PDF,
                                pdfPageIndex = current.progress?.pdfPageIndex ?: 0,
                                pdfPageCount = current.progress?.pdfPageCount ?: 0,
                                pdfZoomScale = current.progress?.pdfZoomScale ?: 1f,
                                pdfExtractedTextEnabled = enabled,
                                pdfExtractedTextPageIndex = pageIndex,
                                pdfExtractedTextScrollMode = scrollMode,
                            )
                            ReaderStateStore.saveProgress(
                                context,
                                ReaderStateStore.loadProgress(context) + (progress.uriString to progress)
                            )
                            state = current.copy(progress = progress)
                        },
                    )

                    else -> TextReaderScreen(
                        screen = ReaderScreen.Text(
                            document = current.document,
                            initialText = current.textContent.orEmpty(),
                            initialScrollFraction = current.progress?.txtScrollFraction
                                ?.coerceIn(0f, 1f) ?: 0f,
                            editable = false,
                            sectionedNote = false,
                        ),
                        preferences = current.preferences,
                        bookmarks = current.bookmarks,
                        onBack = { close() },
                        onPreferencesChange = { updated ->
                            ReaderStateStore.savePreferences(context, updated)
                            state = current.copy(preferences = updated)
                        },
                        onSaveText = { /* read-only */ },
                        onSaveProgress = { scrollFraction ->
                            persistProgress(
                                ReadingProgress(
                                    uriString = current.document.uriString,
                                    type = DocumentType.TXT,
                                    txtScrollFraction = scrollFraction,
                                )
                            )
                        },
                        onToggleBookmark = { scrollFraction ->
                            toggleBookmark(
                                context,
                                current,
                                ReaderSideEffect { bookmarks ->
                                    ReadingBookmarkActions.createTextBookmark(
                                        current.document, scrollFraction
                                    )
                                }
                            ) { updated -> state = current.copy(bookmarks = updated) }
                        },
                        onDiscardText = { close() },
                        onRenameText = { _, _ -> },
                        onNoteSectionSelected = { },
                        onOpenBookNote = null,
                    )
                }
            }
                }
            }
        }
    }

/** Small helper so each screen's bookmark callback can reuse one toggle path. */
private class ReaderSideEffect(private val create: (List<ReadingBookmark>) -> ReadingBookmark) {
    fun invoke(bookmarks: List<ReadingBookmark>): ReadingBookmark = create(bookmarks)
}

private fun toggleBookmark(
    context: Context,
    state: HostState.Ready,
    effect: ReaderSideEffect,
    update: (List<ReadingBookmark>) -> Unit,
) {
    val bookmark = effect.invoke(state.bookmarks)
    val updated = ReadingBookmarkActions.toggled(state.bookmarks, bookmark)
    ReaderStateStore.saveBookmarks(context, updated)
    update(updated)
}

private fun progressPercent(progress: ReadingProgress): Int = when (progress.type) {
    DocumentType.EPUB, DocumentType.FB2, DocumentType.MARKDOWN -> if (progress.epubChapterCount > 0) {
        (((progress.epubChapterIndex + progress.epubScrollFraction.coerceIn(0f, 1f)) /
            progress.epubChapterCount) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        0
    }
    DocumentType.PDF -> if (progress.pdfPageCount > 0) {
        (((progress.pdfPageIndex + 1f) / progress.pdfPageCount) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        (progress.pdfZoomScale * 0f).toInt()
    }
    else -> (progress.txtScrollFraction.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
}

private fun ReadingProgress.positionIndex(): Int = when (type) {
    DocumentType.PDF -> pdfPageIndex
    else -> epubChapterIndex
}

/** Fio keeps the original imported file in filesDir/fio_doc_files/<id>.<ext>. */
private fun resolveDocumentFile(context: Context, document: StudyDocument): File {
    val originalsDir = File(context.filesDir, "fio_doc_files")
    val original = originalsDir.listFiles()
        ?.firstOrNull { it.isFile && it.name.startsWith("${document.id}.") }
    if (original != null) return original

    // No original stored (older imports / unsupported containers): fall back to
    // Fio's extracted text, written once to a cache file the engines can read.
    val textDir = File(context.filesDir, "fio_reader_text").apply { mkdirs() }
    val textFile = File(textDir, "${document.id}.txt")
    if (!textFile.isFile || textFile.length() == 0L) {
        textFile.writeText(document.content)
    }
    return textFile
}

/** Original display name (with extension) so format detection sees e.g. ".epub". */
private fun originalDisplayName(context: Context, document: StudyDocument, file: File): String =
    when (document.format.uppercase()) {
        "PDF" -> "${document.title}.pdf"
        "EPUB" -> "${document.title}.epub"
        "MD" -> "${document.title}.md"
        "FB2" -> "${document.title}.fb2"
        else -> file.name
    }

/** Android's parse throws on malformed hex; fall back to a dark neutral. */
private fun parseColorSafe(hex: String): Int =
    runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(0xFF191919.toInt())
