package com.shade.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imagesDir: File
        get() = File(context.filesDir, "shade_images").also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, "shade_thumbnails").also { it.mkdirs() }

    fun saveDecryptedImage(messageId: String, imageBytes: ByteArray): String {
        val file = File(imagesDir, "$messageId.jpg")
        file.writeBytes(imageBytes)
        return file.absolutePath
    }

    fun saveThumbnail(messageId: String, thumbnailBytes: ByteArray): String {
        val file = File(thumbnailsDir, "$messageId.jpg")
        file.writeBytes(thumbnailBytes)
        return file.absolutePath
    }

    fun getImageFile(messageId: String): File? {
        val file = File(imagesDir, "$messageId.jpg")
        return if (file.exists()) file else null
    }

    fun getThumbnailFile(messageId: String): File? {
        val file = File(thumbnailsDir, "$messageId.jpg")
        return if (file.exists()) file else null
    }

    fun deleteImageFiles(messageId: String) {
        File(imagesDir, "$messageId.jpg").delete()
        File(thumbnailsDir, "$messageId.jpg").delete()
    }
}