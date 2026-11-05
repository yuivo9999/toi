package com.toimg.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageCompressUtil {

    /**
     * Reads image from content Uri, scales down to maxDimension (e.g. 1024),
     * compresses to JPEG with quality, and encodes to Base64 string.
     */
    suspend fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 80
    ): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            // 1. Decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // 2. Calculate inSampleSize (power of 2)
            var sampleSize = 1
            val maxOriginal = max(origWidth, origHeight)
            while (maxOriginal / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // 3. Decode scaled down bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // saves memory
            }
            inputStream = context.contentResolver.openInputStream(uri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap == null) return@withContext null

            // 4. Fine scale if still above maxDimension
            val currentMax = max(bitmap.width, bitmap.height)
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax
                val targetW = (bitmap.width * scale).toInt()
                val targetH = (bitmap.height * scale).toInt()
                val fineScaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (fineScaled != bitmap) {
                    bitmap.recycle()
                    bitmap = fineScaled
                }
            }

            // 5. Compress to JPEG and Base64 encode
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            bitmap.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    suspend fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024
    ): Bitmap? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxOriginal = max(origWidth, origHeight)
            while (maxOriginal / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
    }
}

