package com.example.photo4passport.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object BitmapUtils {

    /**
     * Scale and centre-crop [source] to exactly [outputWidth] × [outputHeight] pixels.
     * The image is scaled so it fills the output dimensions (no letterboxing), and any
     * overflow is clipped.
     */
    fun scaleAndCenterCrop(source: Bitmap, outputWidth: Int, outputHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val scale = maxOf(
            outputWidth.toFloat() / source.width,
            outputHeight.toFloat() / source.height
        )
        val matrix = Matrix().apply {
            postScale(scale, scale)
            val dx = (outputWidth - source.width * scale) / 2f
            val dy = (outputHeight - source.height * scale) / 2f
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(source, matrix, null)
        return result
    }

    /**
     * Composite [source] onto a solid [color] background.
     * Useful for replacing transparent or coloured backgrounds.
     */
    fun tintBackground(source: Bitmap, color: Int): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(color)
        canvas.drawBitmap(source, 0f, 0f, null)
        return output
    }

    /**
     * Save [bitmap] to the public Pictures/Photo4Passport gallery directory.
     * Returns the [Uri] of the saved file, or null if saving failed.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Photo4Passport"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Write [bitmap] to the app's cache directory as a PNG.
     * Used for sharing via FileProvider.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }
}
