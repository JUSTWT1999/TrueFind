package com.truefind.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CaptureCacheManager(private val context: Context) {

    private val capturesDir: File
        get() = File(context.cacheDir, CAPTURES_SUBDIR).apply { if (!exists()) mkdirs() }

    fun saveBitmap(bitmap: Bitmap, prefix: String = "cap"): File {
        val file = File(capturesDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        cleanup()
        return file
    }

    fun overwriteFile(file: File, bitmap: Bitmap): File {
        val temp = File(capturesDir, "crop_${System.currentTimeMillis()}.png")
        FileOutputStream(temp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (file.exists() && file.absolutePath != temp.absolutePath) {
            file.delete()
        }
        cleanup()
        return temp
    }

    fun copyFromInputStream(input: InputStream, prefix: String = "shared"): File {
        val file = File(capturesDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> input.copyTo(out) }
        cleanup()
        return file
    }

    fun getShareUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun cleanup(nowMs: Long = System.currentTimeMillis()) {
        cleanupDirectory(capturesDir, nowMs = nowMs, ttlMs = DEFAULT_TTL_MS, maxKeep = MAX_KEEP_FILES)
    }

    companion object {
        const val CAPTURES_SUBDIR = "captures"
        const val DEFAULT_TTL_MS = 60L * 60L * 1000L // 1 hour
        const val MAX_KEEP_FILES = 3

        /** Pure filesystem cleanup helper for JVM unit testing. */
        fun cleanupDirectory(
            dir: File,
            nowMs: Long,
            ttlMs: Long = DEFAULT_TTL_MS,
            maxKeep: Int = MAX_KEEP_FILES
        ): Int {
            if (!dir.exists() || !dir.isDirectory) return 0
            val files = dir.listFiles()?.filter { it.isFile } ?: return 0
            var deletedCount = 0

            val surviving = mutableListOf<File>()
            for (file in files) {
                if (nowMs - file.lastModified() > ttlMs) {
                    if (file.delete()) deletedCount++
                } else {
                    surviving.add(file)
                }
            }

            if (surviving.size > maxKeep) {
                val sortedOldestFirst = surviving.sortedBy { it.lastModified() }
                val overflow = sortedOldestFirst.size - maxKeep
                for (i in 0 until overflow) {
                    if (sortedOldestFirst[i].delete()) deletedCount++
                }
            }
            return deletedCount
        }
    }
}
