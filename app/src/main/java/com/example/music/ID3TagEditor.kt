package com.example.music

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

object ID3TagEditor {

    fun updateTags(
        filePath: String,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        coverUri: Uri?,
        context: Context
    ): Boolean {
        val originalFile = File(filePath)
        if (!originalFile.exists()) return false

        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)

        // Получаем текущие данные только если новые значения не указаны
        val finalTitle = title ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: File(filePath).nameWithoutExtension
        val finalArtist = artist ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
        val finalAlbum = album ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        val finalGenre = genre ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""

        // Получаем обложку
        var coverBytes: ByteArray? = null
        if (coverUri != null) {
            coverBytes = getCoverBytesFromUri(context, coverUri)
        } else {
            coverBytes = retriever.embeddedPicture
        }

        retriever.release()

        // Создаём временный файл
        val tempFile = File(context.cacheDir, "temp_edit_${System.currentTimeMillis()}.mp3")

        try {
            // Копируем оригинал во временный файл
            FileInputStream(originalFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Редактируем временный файл
            writeID3v2Tags(tempFile, finalTitle, finalArtist, finalAlbum, finalGenre, coverBytes)

            // Пробуем прямой доступ к файлу
            val success = tryDirectFileAccess(filePath, tempFile, context)
                ?: tryMediaStoreAccess(filePath, tempFile, context)

            tempFile.delete()
            return success

        } catch (e: Exception) {
            tempFile.delete()
            Log.e("ID3TagEditor", "Error updating tags", e)
            throw e
        }
    }
    private fun tryMediaStoreAccess(originalPath: String, tempFile: File, context: Context): Boolean {
        return try {
            Log.d("ID3TagEditor", "Using MediaStore access for: $originalPath")

            val contentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(originalPath)

            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val fileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Пробрасываем SecurityException наверх для обработки
                    contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("ID3TagEditor", "MediaStore access successful")
                    return true
                }
            }
            Log.e("ID3TagEditor", "MediaStore access failed - file not found")
            false
        } catch (e: SecurityException) {
            Log.e("ID3TagEditor", "MediaStore access blocked by SecurityException", e)
            throw e // Пробрасываем SecurityException для обработки в Activity
        } catch (e: Exception) {
            Log.e("ID3TagEditor", "Error in MediaStore access", e)
            false
        }
    }
    private fun tryDirectFileAccess(originalPath: String, tempFile: File, context: Context): Boolean? {
        return try {
            Log.d("ID3TagEditor", "Attempting direct file access for: $originalPath")

            val originalFile = File(originalPath)
            if (originalFile.canWrite()) {
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(originalFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("ID3TagEditor", "Direct file access successful")
                true
            } else {
                Log.d("ID3TagEditor", "Direct file access not available - file not writable")
                null
            }
        } catch (e: SecurityException) {
            Log.d("ID3TagEditor", "Direct file access blocked by SecurityException")
            null
        } catch (e: Exception) {
            Log.e("ID3TagEditor", "Error in direct file access", e)
            null
        }
    }
    // Добавим новый метод для массового обновления без прямого доступа
    fun updateTagsWithoutDirectAccess(
        filePath: String,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        coverUri: Uri?,
        context: Context
    ): Boolean {
        val originalFile = File(filePath)
        if (!originalFile.exists()) return false

        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)

        // Получаем текущие данные только если новые значения не указаны
        val finalTitle = title ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: File(filePath).nameWithoutExtension
        val finalArtist = artist ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
        val finalAlbum = album ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        val finalGenre = genre ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""

        // Получаем обложку
        var coverBytes: ByteArray? = null
        if (coverUri != null) {
            coverBytes = getCoverBytesFromUri(context, coverUri)
        } else {
            coverBytes = retriever.embeddedPicture
        }

        retriever.release()

        // Создаём временный файл
        val tempFile = File(context.cacheDir, "temp_edit_${System.currentTimeMillis()}.mp3")

        try {
            // Копируем оригинал во временный файл
            FileInputStream(originalFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Редактируем временный файл
            writeID3v2Tags(tempFile, finalTitle, finalArtist, finalAlbum, finalGenre, coverBytes)

            // Используем тот же подход, что и для одиночного трека - копируем через MediaStore
            val success = tryMediaStoreAccess(filePath, tempFile, context)

            tempFile.delete()
            return success

        } catch (e: Exception) {
            tempFile.delete()
            Log.e("ID3TagEditor", "Error updating tags without direct access", e)
            return false
        }
    }
    private fun getCoverBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val resizedBitmap = resizeBitmap(bitmap, 600, 600)

            val stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Log.e("ID3TagEditor", "Error getting cover", e)
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }

        val ratio = Math.min(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun writeID3v2Tags(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        cover: ByteArray?
    ) {
        val raf = RandomAccessFile(file, "rw")

        try {
            // Проверяем наличие ID3v2
            val header = ByteArray(10)
            raf.read(header)

            var tagSize = 0
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                // ID3v2 уже есть, вычисляем размер
                tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)
                tagSize += 10
            }

            // Читаем аудио данные (после тэгов)
            raf.seek(tagSize.toLong())
            val audioData = ByteArray((file.length() - tagSize).toInt())
            raf.read(audioData)

            // Создаём новые тэги
            val frames = mutableListOf<ByteArray>()

            if (!title.isNullOrEmpty()) {
                frames.add(createTextFrame("TIT2", title))
            }
            if (!artist.isNullOrEmpty()) {
                frames.add(createTextFrame("TPE1", artist))
            }
            if (!album.isNullOrEmpty()) {
                frames.add(createTextFrame("TALB", album))
            }
            if (!genre.isNullOrEmpty()) {
                frames.add(createTextFrame("TCON", genre))
            }
            if (cover != null) {
                frames.add(createPictureFrame(cover))
            }

            // Собираем все фреймы
            val framesData = ByteArrayOutputStream()
            frames.forEach { framesData.write(it) }

            val newTagSize = framesData.size()

            // Создаём новый заголовок ID3v2.3
            val newHeader = ByteArray(10)
            newHeader[0] = 'I'.code.toByte()
            newHeader[1] = 'D'.code.toByte()
            newHeader[2] = '3'.code.toByte()
            newHeader[3] = 3
            newHeader[4] = 0
            newHeader[5] = 0

            // Размер в synchsafe integer
            newHeader[6] = ((newTagSize shr 21) and 0x7F).toByte()
            newHeader[7] = ((newTagSize shr 14) and 0x7F).toByte()
            newHeader[8] = ((newTagSize shr 7) and 0x7F).toByte()
            newHeader[9] = (newTagSize and 0x7F).toByte()

            // Записываем новый файл
            raf.seek(0)
            raf.write(newHeader)
            raf.write(framesData.toByteArray())
            raf.write(audioData)
            raf.setLength(raf.filePointer)

        } finally {
            raf.close()
        }
    }

    private fun createTextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val frameSize = textBytes.size + 1

        val frame = ByteArrayOutputStream()
        frame.write(frameId.toByteArray())
        frame.write(intToBytes(frameSize))
        frame.write(0)
        frame.write(0)
        frame.write(3) // UTF-8 encoding
        frame.write(textBytes)

        return frame.toByteArray()
    }

    private fun createPictureFrame(imageData: ByteArray): ByteArray {
        val mimeType = "image/jpeg"
        val mimeBytes = mimeType.toByteArray()

        val frameSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageData.size

        val frame = ByteArrayOutputStream()
        frame.write("APIC".toByteArray())
        frame.write(intToBytes(frameSize))
        frame.write(0)
        frame.write(0)
        frame.write(0)
        frame.write(mimeBytes)
        frame.write(0)
        frame.write(3)
        frame.write(0)
        frame.write(imageData)

        return frame.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}