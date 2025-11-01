package com.example.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

object DiskImageCache {
    private const val FOLDER_NAME = "images"
    // 1 GB limit
    private const val LIMIT_BYTES: Long = 1024L * 1024L * 1024L

    private lateinit var cacheDir: File
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        cacheDir = File(context.cacheDir, FOLDER_NAME)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        initialized = true
    }

    fun clear() {
        if (!initialized) return
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun getBitmap(key: String): Bitmap? {
        if (!initialized) return null
        val f = fileFor(key)
        if (!f.exists()) return null
        // touch for LRU
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun removeFromCache(key: String) {
        if (!initialized) return
        val f = fileFor(key)
        if (f.exists()) {
            runCatching { f.delete() }
        }
    }

    // Метод для массового удаления по префиксу (для групп)
    fun removeByPrefix(prefix: String) {
        if (!initialized) return
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(md5(prefix))) {
                runCatching { file.delete() }
            }
        }
    }

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (!initialized) return
        val f = fileFor(key)
        // write temp then rename
        val tmp = File(f.parentFile, f.name + ".tmp")
        runCatching {
            FileOutputStream(tmp).use { out ->
                // Prefer WEBP lossy with quality ~85, fallback JPEG
                val ok = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
                if (!ok) {
                    out.flush()
                }
            }
            if (f.exists()) f.delete()
            tmp.renameTo(f)
            f.setLastModified(System.currentTimeMillis())
        }.onFailure {
            runCatching { tmp.delete() }
        }
        prune()
    }

    private fun prune() {
        var total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (total <= LIMIT_BYTES) return
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (file in files) {
            if (total <= LIMIT_BYTES) break
            val len = file.length()
            if (file.delete()) total -= len
        }
    }

    private fun fileFor(key: String): File {
        val name = md5(key) + ".webp"
        return File(cacheDir, name)
    }

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(s.toByteArray())
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format(Locale.US, "%02x", b))
        return sb.toString()
    }
}
