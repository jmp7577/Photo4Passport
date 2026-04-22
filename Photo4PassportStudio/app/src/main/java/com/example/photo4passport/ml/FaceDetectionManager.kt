package com.example.photo4passport.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceDetectionManager(context: Context) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .enableTracking()
            .build()
    )

    suspend fun detectFace(bitmap: Bitmap): FaceMeasurement? = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(detector.process(image))
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return@withContext null
        val box = face.boundingBox
        val normalized = RectF(
            box.left / bitmap.width.toFloat(),
            box.top / bitmap.height.toFloat(),
            box.right / bitmap.width.toFloat(),
            box.bottom / bitmap.height.toFloat()
        )
        FaceMeasurement(normalized, normalized.height())
    }
}
