package com.example.fio.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageStorage {
    /**
     * Saves an already-downloaded image (Wingman/Tutor related pictures) to
     * internal storage, scaled to a memory-friendly size. Downloads are cached:
     * the same [fileName] reuses the stored copy.
     * Returns the stored file path, or null on failure.
     */
    suspend fun storeRemoteImage(context: Context, bytes: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "fio_images")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            if (target.exists() && target.length() > 0L) return@withContext target.absolutePath

            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val maxDimension = 1024
            val scale = if (original.width > maxDimension || original.height > maxDimension) {
                maxDimension.toFloat() / max(original.width, original.height)
            } else 1.0f
            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                )
            } else original

            val outputStream = FileOutputStream(target)
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
            outputStream.flush()
            outputStream.close()

            if (scaled !== original) scaled.recycle()
            original.recycle()
            target.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Downscales, compresses, and saves an image Uri to internal storage.
     * Returns a pair of:
     * - localFilePath: String (stored persistent path)
     * - base64DataUri: String (data:image/jpeg;base64,... for AI vision)
     */
    suspend fun processAndStoreImage(context: Context, imageUri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext null

            // Downscale to max 1024px dimension to maintain minimal RAM usage
            val maxDimension = 1024
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                maxDimension.toFloat() / max(width, height)
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else originalBitmap

            // Prepare directory
            val dir = File(context.filesDir, "fio_images")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val file = File(dir, fileName)

            // Compress to JPEG with 80% quality
            val outputStream = FileOutputStream(file)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()

            // Generate Base64 for AI Multimodal input
            val byteStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, byteStream)
            val bytes = byteStream.toByteArray()
            val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64String"

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            Pair(file.absolutePath, dataUri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
