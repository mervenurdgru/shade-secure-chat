package com.shade.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun compressImage(uri: Uri, maxSizeBytes: Long = 5_242_880L): ByteArray =
        withContext(Dispatchers.IO) {
            val originalBitmap = decodeBitmap(uri)
            val scaledBitmap = scaleDown(originalBitmap, 2048)

            var quality = 85
            var result: ByteArray

            do {
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                result = stream.toByteArray()
                quality -= 10
            } while (result.size > maxSizeBytes && quality > 10)

            if (scaledBitmap != originalBitmap) originalBitmap.recycle()
            scaledBitmap.recycle()

            result
        }

    suspend fun generateThumbnail(
        uri: Uri,
        maxDimension: Int = 200,
        maxSizeKb: Int = 20
    ): ByteArray = withContext(Dispatchers.IO) {
        val originalBitmap = decodeBitmap(uri)
        val thumbnail = scaleDown(originalBitmap, maxDimension)

        var quality = 60
        var result: ByteArray

        do {
            val stream = ByteArrayOutputStream()
            thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            result = stream.toByteArray()
            quality -= 10
        } while (result.size > maxSizeKb * 1024 && quality > 10)

        if (thumbnail != originalBitmap) originalBitmap.recycle()
        thumbnail.recycle()

        result
    }

    fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        return Pair(options.outWidth, options.outHeight)
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it)
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}