package com.example.fio.data.transfer

import android.content.Context
import com.example.fio.alarms.TimetableAlarms
import com.example.fio.data.storage.FioRepository
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Device-to-Device profile cloning over the local Wi-Fi network (Xender-style).
 *
 * A COMPLETE clone travels, leaving nothing behind:
 *  - every course, book & PDF/EPUB original, note (with images), chat (with photos),
 *    gallery photos, flashcards, tutor sessions, focus session logs, stats inputs
 *    (events, habits, goals, sleep, meals), timetable + its alarms,
 *    settings and the AI provider/API key
 *
 * The sender packs a single zip (state.json + manifest.json + every stored file),
 * listens on the LAN; the receiver downloads it and imports it as-is.
 */

/** Default transfer port; bumped automatically if already in use. */
const val FIO_TRANSFER_PORT = 44841

/** UDP port both apps use to find each other on the same Wi-Fi (no URLs). */
const val FIO_DISCOVERY_PORT = 44842
private const val FIO_DISCOVERY_MAGIC = "FIO_DISCOVER_V1"

/** A sender discovered on the local network. */
data class DiscoveredDevice(
    val displayName: String,
    val ip: String,
    val port: Int,
    val summary: TransferSummary,
    val bundleBytes: Long = 0L
) {
    val address: String get() = "http://$ip:$port"
}

/** Snapshot of what a bundle contains, for UI summaries. */
data class TransferSummary(
    val courses: Int = 0,
    val documents: Int = 0,
    val gallery: Int = 0,
    val images: Int = 0,
    val notes: Int = 0,
    val chats: Int = 0,
    val events: Int = 0,
    val habits: Int = 0,
    val sleepLogs: Int = 0,
    val mealLogs: Int = 0,
    val flashcards: Int = 0,
    val tutorSessions: Int = 0,
    val sessionLogs: Int = 0,
    val totalFiles: Int = 0,
    val bundleBytes: Long = 0
) {
    private fun part(count: Int, label: String): String? =
        if (count <= 0) null else "$count $label${if (count != 1) "s" else ""}"

    /** e.g. "9 courses · 12 books · 8 gallery photos · 210 chats" */
    fun line(): String {
        val parts = listOfNotNull(
            part(courses, "course"),
            part(documents, "book"),
            part(gallery, "gallery photo"),
            part(images, "chat/AI image"),
            part(notes, "note"),
            part(chats, "chat"),
            part(events, "calendar event"),
            part(habits, "habit"),
            part(tutorSessions, "tutor session"),
            part(flashcards, "flashcard"),
            part(sleepLogs, "sleep log"),
            part(mealLogs, "meal log"),
            part(sessionLogs, "focus session")
        )
        val text = parts.joinToString(" · ")
        val filesNote = if (totalFiles > 0) " ($totalFiles files, ${formatBytes(bundleBytes)})" else ""
        return if (text.isBlank()) "empty profile$filesNote" else text + filesNote
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun summaryJson(): JSONObject = JSONObject()
        .put("courses", courses).put("documents", documents).put("gallery", gallery)
        .put("images", images).put("notes", notes).put("chats", chats)
        .put("events", events).put("habits", habits).put("sleepLogs", sleepLogs)
        .put("mealLogs", mealLogs).put("flashcards", flashcards)
        .put("tutorSessions", tutorSessions).put("sessionLogs", sessionLogs)
        .put("totalFiles", totalFiles)

    companion object {
        fun fromJson(obj: JSONObject): TransferSummary = TransferSummary(
            courses = obj.optInt("courses", 0),
            documents = obj.optInt("documents", 0),
            gallery = obj.optInt("gallery", 0),
            images = obj.optInt("images", 0),
            notes = obj.optInt("notes", 0),
            chats = obj.optInt("chats", 0),
            events = obj.optInt("events", 0),
            habits = obj.optInt("habits", 0),
            sleepLogs = obj.optInt("sleepLogs", 0),
            mealLogs = obj.optInt("mealLogs", 0),
            flashcards = obj.optInt("flashcards", 0),
            tutorSessions = obj.optInt("tutorSessions", 0),
            sessionLogs = obj.optInt("sessionLogs", 0),
            totalFiles = obj.optInt("totalFiles", 0)
        )
    }
}

/** Builds and restores the full-profile transfer bundle. */
object FioBundle {
    private const val MANIFEST = "manifest.json"
    private const val STATE = "state.json"
    private const val FILES_PREFIX = "f/"

    /** Packs the ENTIRE profile into a zip: JSON state + every stored binary file. */
    fun build(context: Context, repo: FioRepository, out: File): TransferSummary {
        val filesDir = context.filesDir
        if (out.exists()) out.delete()

        // Every stored binary behind the profile. Document contents & settings
        // travel inside state.json; these are the physical files.
        val packed = mutableListOf<Pair<String, File>>()
        File(filesDir, "fio_doc_files").listFiles()?.forEach { f ->
            if (f.isFile) packed += "fio_doc_files/${f.name}" to f // original PDFs/EPUBs/<id>.<ext>
        }
        repo.galleryImages.value.forEach { g ->
            val f = File(File(filesDir, "fio_gallery"), g.fileName)
            if (f.isFile) packed += "fio_gallery/${g.fileName}" to f
        }
        File(filesDir, "fio_images").listFiles()?.forEach { f ->
            if (f.isFile) packed += "fio_images/${f.name}" to f // chat photos + downloaded AI images
        }

        val summary = TransferSummary(
            courses = repo.courses.value.size,
            documents = repo.documents.value.size,
            gallery = repo.galleryImages.value.size,
            images = File(filesDir, "fio_images").listFiles()?.count { it.isFile } ?: 0,
            notes = repo.notes.value.size,
            chats = repo.chatHistory.value.size,
            events = repo.events.value.size,
            habits = repo.habits.value.size,
            sleepLogs = repo.sleepLogs.value.size,
            mealLogs = repo.mealLogs.value.size,
            flashcards = repo.flashcards.value.size,
            tutorSessions = repo.tutorSessions.value.size,
            sessionLogs = repo.sessionLogs.value.size,
            totalFiles = packed.size
        )

        val manifest = JSONObject()
            .put("app", "Fio")
            .put("bundleVersion", 1)
            .put("oldRoot", filesDir.absolutePath)
            .put("summary", summary.summaryJson())

        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Full state JSON: profile, events/timetable/stats, courses, documents
            // (with content), notes, chats, gallery metadata, settings incl. AI key.
            zip.putNextEntry(ZipEntry(STATE))
            zip.write(repo.exportAllToJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((rel, file) in packed) {
                zip.putNextEntry(ZipEntry(FILES_PREFIX + rel))
                file.inputStream().use { input -> input.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
        return summary.copy(bundleBytes = out.length())
    }

    /**
     * Imports a bundle received from another device. Copies every stored file into
     * this device's internal storage, rewrites the sender's absolute paths to the
     * local ones, then restores the whole state through the standard import path.
     *
     * @param merge when true the incoming records merge ("newest wins") instead of
     *              replacing the device's current progress — the Sync flow.
     */
    fun restore(context: Context, repo: FioRepository, zip: File, merge: Boolean = false): TransferSummary {
        val tmpRoot = File(context.cacheDir, "fio_restore_${System.currentTimeMillis()}")
        tmpRoot.mkdirs()
        try {
            var manifestObj: JSONObject? = null
            var stateText: String? = null
            val filesDirPath = context.filesDir.canonicalPath
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zin ->
                var entry: ZipEntry? = zin.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST -> manifestObj = JSONObject(zin.readBytes().toString(Charsets.UTF_8))
                        STATE -> stateText = zin.readBytes().toString(Charsets.UTF_8)
                        else -> {
                            val rel = entry.name.removePrefix(FILES_PREFIX)
                            val target = File(context.filesDir, rel)
                            // Path-traversal guard: the entry must stay inside filesDir.
                            if (!target.canonicalPath.startsWith(filesDirPath)) {
                                throw IllegalStateException("Unsafe bundle entry: ${entry.name}")
                            }
                            target.parentFile?.mkdirs()
                            zin.copyTo(BufferedOutputStream(FileOutputStream(target)), 64 * 1024)
                        }
                    }
                    entry = zin.nextEntry
                }
            }
            val manifest = manifestObj ?: throw IllegalStateException("Corrupt transfer bundle (no manifest).")
            val json = stateText ?: throw IllegalStateException("Corrupt transfer bundle (no state).")

            // Rewrite the sender's storage root to this device's (chat/note image paths).
            val oldRoot = manifest.optString("oldRoot", "")
            val rewritten = if (oldRoot.isNotBlank() &&
                oldRoot != filesDirPath && json.contains(oldRoot)
            ) json.replace(oldRoot, filesDirPath) else json

            val imported = if (merge) repo.mergeFromJson(rewritten) else repo.importFromJson(rewritten)
            if (!imported) throw IllegalStateException("Could not import the received profile.")

            // Class-period alarms ring on this device too, from now on.
            runCatching { TimetableAlarms.rescheduleAll(context) }

            return TransferSummary.fromJson(manifest.optJSONObject("summary") ?: JSONObject())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }
}

/** The sending side: serves the bundle over plain HTTP on the LAN. */
class TransferServer(
    private val context: Context,
    private val repo: FioRepository,
    private val onLog: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var discoverySocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null

    @Volatile private var running = false
    @Volatile var activePort: Int = FIO_TRANSFER_PORT
        private set

    private val bundleLock = Any()
    private var bundleFile: File? = null
    private var bundleSummary: TransferSummary? = null

    val isRunning: Boolean get() = running

    /** Positions to show: http://<wifi-ip>:<activePort> */
    fun address(): String = "http://${FioWifi.localIp() ?: "0.0.0.x"}:$activePort"

    fun start() {
        if (running) return
        running = true
        acceptThread = Thread({
            try {
                // Actually pack everything BEFORE binding, so the address the user
                // sees is only shown once downloads can truly succeed.
                onLog("Packing full profile (books, photos, chats)…")
                val bundle = File(context.cacheDir, "fio_transfer_bundle.zip")
                val summary = FioBundle.build(context, repo, bundle)
                synchronized(bundleLock) {
                    bundleFile = bundle
                    bundleSummary = summary
                }
                onLog("Bundle ready — ${summary.line()}")

                var server: ServerSocket? = null
                var port = FIO_TRANSFER_PORT
                while (server == null && port < FIO_TRANSFER_PORT + 20) {
                    server = runCatching {
                        ServerSocket(port, 8, InetAddress.getByName("0.0.0.0"))
                    }.getOrNull()
                    if (server == null) port++
                }
                if (server == null) {
                    onLog("Could not open a network port. Disconnect/reconnect Wi-Fi and retry.")
                    running = false
                    return@Thread
                }
                activePort = port
                serverSocket = server
                startDiscovery()
                onLog("Ready to be discovered on this Wi-Fi — no address needed on the other device.")
                onLog("Listening on http://${FioWifi.localIp() ?: "Lan"}:$port — keep this screen open.")

                while (running) {
                    val client = server.accept()
                    if (!running) { runCatching { client.close() }; break }
                    Thread({
                        runCatching { handleClient(client) }
                            .onFailure { onLog("Connection error: ${it.message ?: "unknown"}") }
                    }, "fio-transfer-client").start()
                }
            } catch (e: Exception) {
                if (running) onLog("Server stopped: ${e.message ?: "unknown"}")
            } finally {
                running = false
            }
        }, "fio-transfer-server").apply { start() }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        runCatching { discoveryThread?.interrupt() }
        bundleFile?.delete()
        bundleFile = null
    }

    /**
     * Answers "who is sending?" broadcasts so the receiving device can list this
     * phone/tablet by name — the Xender-style flow with no address typing.
     */
    private fun startDiscovery() {
        discoveryThread = Thread({
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(FIO_DISCOVERY_PORT))
                }
                discoverySocket = socket
                val buffer = ByteArray(512)
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (text != FIO_DISCOVERY_MAGIC) continue
                        val summary = synchronized(bundleLock) { bundleSummary }
                        val reply = JSONObject()
                            .put("app", "Fio")
                            .put("device", repo.profileName.value.ifBlank { "Fio device" })
                            .put("port", activePort)
                            .put("summary", summary?.summaryJson() ?: JSONObject())
                            .put("bundleBytes", synchronized(bundleLock) { bundleFile?.length() ?: 0L })
                        val bytes = reply.toString().toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                        onLog("Nearby device found: ${packet.address?.hostAddress}")
                    } catch (e: Exception) {
                        if (!running) break
                    }
                }
            } catch (e: Exception) {
                if (running) onLog("Nearby discovery unavailable — use the address below.")
            }
        }, "fio-transfer-discovery").apply { start() }
    }

    private fun handleClient(client: Socket) {
        val peer = client.inetAddress?.hostAddress ?: "unknown"
        client.use { socket ->
            val input = socket.getInputStream()
            val requestLine = readRequestLine(input) ?: return
            var header = readRequestLine(input)
            while (!header.isNullOrEmpty()) header = readRequestLine(input)

            val tokens = requestLine.split(" ")
            if (tokens.size < 2) return
            val path = tokens[1].substringBefore('?')
            when {
                tokens[0] == "HEAD" && path == "/backup.zip" -> {
                    onLog("Device $peer connected (checking size).")
                    respondZipHeaders(socket)
                }
                path == "/" || path == "/index.html" -> {
                    onLog("Device $peer connected via browser.")
                    respondText(socket, 200, "text/html; charset=utf-8", landingPage())
                }
                path == "/fio-transfer.json" -> {
                    val summary = synchronized(bundleLock) { bundleSummary }
                    val body = JSONObject()
                        .put("app", "Fio")
                        .put("profile", repo.profileName.value)
                        .put("summary", summary?.summaryJson() ?: JSONObject())
                        .put("bundleBytes", synchronized(bundleLock) { bundleFile?.length() ?: 0L })
                    respondText(socket, 200, "application/json", body.toString())
                }
                path == "/backup.zip" -> {
                    onLog("→ Sending the full profile to $peer…")
                    val file = synchronized(bundleLock) { bundleFile } ?: run {
                        respondText(socket, 503, "text/plain", "Bundle not ready yet")
                        return
                    }
                    if (!respondZipHeaders(socket, file)) return
                    FileInputStream(file).use { input ->
                        input.copyTo(socket.getOutputStream(), 64 * 1024)
                    }
                    socket.getOutputStream().flush()
                    onLog("✓ Profile delivered to $peer.")
                }
                else -> respondText(socket, 404, "text/plain", "Unknown resource")
            }
        }
    }

    private fun respondZipHeaders(socket: Socket, file: File? = null): Boolean {
        val existing = synchronized(bundleLock) { bundleFile }
        val target = file ?: existing ?: return false
        val head = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/zip\r\n" +
            "Content-Length: ${target.length()}\r\n" +
            "Content-Disposition: attachment; filename=\"Fio_profile.zip\"\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().write(head.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
        return true
    }

    private fun respondText(socket: Socket, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun landingPage(): String = """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Fio Profile Transfer</title></head>
        <body style="font-family:sans-serif;max-width:480px;margin:40px auto;padding:0 16px;text-align:center">
        <h2>Fio Profile Transfer</h2>
        <p>This device is sharing its complete Fio profile: courses, books &amp; PDFs,
        notes with photos, chats, gallery, stats, timetable and settings.</p>
        <p><a href="/backup.zip" download="Fio_profile.zip"
        style="display:inline-block;padding:14px 22px;background:#6c5ce7;color:#fff;border-radius:12px;text-decoration:none">
        Download full profile (.zip)</a></p>
        <p style="color:#777">Prefer the in-app flow? On the receiving device open
        Fio → Settings → Device to Device Transfer → <b>Receive</b>, then type this
        device's address there for a one-tap clone.</p>
        </body></html>
    """.trimIndent()

    /** Header reader that never touches decoders (binary-safe). */
    private fun readRequestLine(input: InputStream): String? {
        val buf = StringBuilder(128)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.isEmpty()) null else buf.toString()
            if (b == '\n'.code) return buf.toString().trimEnd()
            if (buf.length < 8192) buf.append(b.toChar())
        }
    }
}

/** LAN utilities shared by both sides. */
object FioWifi {
    private val cacheLock = Any()
    private var cachedIp: String? = null
    private var cachedAt: Long = 0

    /** Best-effort site-local IPv4 of this device (e.g. 192.168.1.34). */
    fun localIp(): String? {
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            if (cachedIp != null && now - cachedAt < 10_000) return cachedIp
        }
        val found = runCatching {
            val candidates = NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .map { it.hostAddress }
                .toList()
            candidates.firstOrNull { it?.startsWith("192.168.") == true }
                ?: candidates.firstOrNull { it?.startsWith("10.") == true }
                ?: candidates.firstOrNull()
        }.getOrNull()
        synchronized(cacheLock) {
            cachedIp = found
            cachedAt = System.currentTimeMillis()
        }
        return found
    }
}

/** The receiving side: probe an address, then download + one-tap restore. */
object FioTransferClient {

    /**
     * Scans the Wi-Fi for Fio devices currently sending a profile — Xender style,
     * no address typing. Blocking for up to [timeoutMs]; call on a background thread.
     * Devices are reported as soon as they answer.
     */
    fun discoverDevices(
        timeoutMs: Long = 4500,
        onFound: (DiscoveredDevice) -> Unit
    ) {
        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 500
        }
        try {
            val payload = FIO_DISCOVERY_MAGIC.toByteArray(Charsets.UTF_8)
            val targets = mutableListOf<InetAddress>()
            runCatching { targets += InetAddress.getByName("255.255.255.255") }
            // Also aim at this subnet's directed broadcast (some networks drop 255.255.255.255).
            FioWifi.localIp()?.substringBeforeLast('.')?.let { prefix ->
                runCatching { targets += InetAddress.getByName("$prefix.255") }
            }
            targets.distinctBy { it.hostAddress }.forEach { target ->
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, target, FIO_DISCOVERY_PORT))
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val seen = mutableSetOf<String>()
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val json = runCatching {
                        JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                    }.getOrNull() ?: continue
                    if (json.optString("app") != "Fio") continue
                    val ip = packet.address?.hostAddress ?: continue
                    val port = json.optInt("port", FIO_TRANSFER_PORT)
                    val key = "$ip:$port"
                    if (!seen.add(key)) continue
                    onFound(
                        DiscoveredDevice(
                            displayName = json.optString("device", "Fio device"),
                            ip = ip,
                            port = port,
                            summary = TransferSummary.fromJson(
                                json.optJSONObject("summary") ?: JSONObject()
                            ),
                            bundleBytes = json.optLong("bundleBytes", 0L)
                        )
                    )
                } catch (_: java.net.SocketTimeoutException) {
                    // keep scanning until the deadline
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Blocking: wrap in a background dispatcher. Sender's base + content offer. */
    fun probe(rawAddress: String): Pair<String, TransferSummary> {
        val base = normalizeAddress(rawAddress).trimEnd('/')
        val info = httpGetText("$base/fio-transfer.json")
        val infoObj = runCatching { JSONObject(info) }.getOrNull()
            ?: throw IllegalArgumentException("That address is not running Fio transfer.")
        val summary = TransferSummary.fromJson(infoObj.optJSONObject("summary") ?: JSONObject())
        return base to summary
    }

    /** Blocking: downloads the sender's bundle and restores it completely. */
    fun downloadAndRestore(
        context: Context,
        repo: FioRepository,
        base: String,
        merge: Boolean,
        onPhase: (String, Long, Long) -> Unit
    ): TransferSummary {
        onPhase("downloading", 0, 0)
        val zip = File(context.cacheDir, "fio_incoming_${System.currentTimeMillis()}.zip")
        try {
            httpDownload("$base/backup.zip", zip) { received, size ->
                onPhase("downloading", received, size)
            }
            onPhase("restoring", zip.length(), zip.length())
            return FioBundle.restore(context, repo, zip, merge)
        } finally {
            zip.delete()
        }
    }

    /** "fio://x", "192.168.1.2", "ip:port", "http://…" → "http://…" */
    private fun normalizeAddress(raw: String): String {
        var address = raw.trim()
        if (address.isEmpty()) throw IllegalArgumentException("Type the address shown on the other device.")
        if (address.startsWith("fio://")) address = address.removePrefix("fio://")
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            address = "http://$address"
        }
        return address
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun httpGetText(urlString: String): String {
        val conn = openConnection(urlString)
        try {
            conn.requestMethod = "GET"
            return if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                throw IllegalArgumentException("Device responded with error ${conn.responseCode}.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownload(urlString: String, target: File, onProgress: (Long, Long) -> Unit) {
        val conn = openConnection(urlString)
        try {
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) {
                throw IllegalArgumentException("Download failed (HTTP ${conn.responseCode}).")
            }
            val total = conn.contentLengthLong
            val input = conn.inputStream
            var received = 0L
            var lastReport = 0L
            BufferedOutputStream(FileOutputStream(target)).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    received += read
                    if (received - lastReport > 128 * 1024) {
                        lastReport = received
                        onProgress(received, total)
                    }
                }
                out.flush()
            }
            onProgress(received, if (total > 0) total else received)
        } finally {
            conn.disconnect()
        }
    }
}
