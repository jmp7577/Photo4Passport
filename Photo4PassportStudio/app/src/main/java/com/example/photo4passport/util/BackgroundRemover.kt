package com.example.photo4passport.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackgroundRemover {

    suspend fun replaceBackground(source: Bitmap, bgColor: Int): Bitmap =
        withContext(Dispatchers.Default) {
            val segmenter = Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
            )

            val image = InputImage.fromBitmap(source, 0)
            val result = Tasks.await(segmenter.process(image))
            val mask = result.buffer
            val maskWidth = result.width
            val maskHeight = result.height

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(bgColor)

            val scaledMask = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            mask.rewind()
            for (y in 0 until maskHeight) {
                for (x in 0 until maskWidth) {
                    val confidence = mask.float
                    val alpha = (confidence * 255).toInt().coerceIn(0, 255)
                    scaledMask.setPixel(x, y, Color.argb(alpha, 0, 0, 0))
                }
            }

            // Scale mask to match source bitmap dimensions
            val scaledMaskFull = Bitmap.createScaledBitmap(scaledMask, source.width, source.height, true)
            scaledMask.recycle()

            // Draw source pixels where mask has high confidence
            val srcPixels = IntArray(source.width * source.height)
            val maskPixels = IntArray(source.width * source.height)
            source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
            scaledMaskFull.getPixels(maskPixels, 0, source.width, 0, 0, source.width, source.height)
            scaledMaskFull.recycle()

            val outPixels = IntArray(source.width * source.height)
            for (i in srcPixels.indices) {
                val maskAlpha = Color.alpha(maskPixels[i])
                val srcColor = srcPixels[i]
                outPixels[i] = if (maskAlpha > 128) srcColor else bgColor
            }

            output.setPixels(outPixels, 0, source.width, 0, 0, source.width, source.height)
            segmenter.close()
            output
        }
}