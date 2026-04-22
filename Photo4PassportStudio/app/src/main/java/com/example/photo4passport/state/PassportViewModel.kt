package com.example.photo4passport.state

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photo4passport.data.BackgroundMode
import com.example.photo4passport.data.CountrySpec
import com.example.photo4passport.data.CountrySpecRepository
import com.example.photo4passport.editor.ComplianceValidator
import com.example.photo4passport.export.ExportSheetBuilder
import com.example.photo4passport.ml.FaceDetectionManager
import com.example.photo4passport.util.BitmapUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.photo4passport.util.BackgroundRemover
class PassportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CountrySpecRepository()
    private val validator = ComplianceValidator()
    private val faceDetectionManager = FaceDetectionManager(application.applicationContext)
    private val exportSheetBuilder = ExportSheetBuilder()

    private val _uiState = MutableStateFlow(
        EditorUiState(
            selectedCountry = repository.defaultCountry(),
            backgroundMode = repository.defaultCountry().backgroundMode
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val countries: List<CountrySpec> = repository.getAll()

    fun selectCountry(countrySpec: CountrySpec) {
        _uiState.value = _uiState.value.copy(
            selectedCountry = countrySpec,
            backgroundMode = countrySpec.backgroundMode,
            complianceState = ComplianceState.NotChecked,
            exportBitmap = null
        )
    }

    fun setBitmap(bitmap: Bitmap, uri: Uri? = null) {
        _uiState.value = _uiState.value.copy(
            bitmap = bitmap,
            imageUri = uri,
            scale = 1f,
            translationX = 0f,
            translationY = 0f,
            complianceState = ComplianceState.NotChecked,
            exportBitmap = null,
            faceMeasurement = null
        )
        detectFace(bitmap)
        removeAndReplaceBackground()
    }

    private fun detectFace(bitmap: Bitmap) {
        viewModelScope.launch {
            val measurement = faceDetectionManager.detectFace(bitmap)
            _uiState.value = _uiState.value.copy(faceMeasurement = measurement)
        }
    }

    fun updateTransform(
        scale: Float? = null,
        translationX: Float? = null,
        translationY: Float? = null
    ) {
        val state = _uiState.value
        _uiState.value = state.copy(
            scale = scale ?: state.scale,
            translationX = translationX ?: state.translationX,
            translationY = translationY ?: state.translationY,
            exportBitmap = null  // invalidate export when transform changes
        )
    }

    fun resetTransform() {
        _uiState.value = _uiState.value.copy(
            scale = 1f,
            translationX = 0f,
            translationY = 0f,
            exportBitmap = null
        )
    }
    fun autoCrop() {
        val state = _uiState.value
        val source = state.bitmap ?: return
        val country = state.selectedCountry ?: repository.defaultCountry()

        viewModelScope.launch {
            val targetRatio = (country.minHeadHeightRatio + country.maxHeadHeightRatio) / 2f

            // Re-detect face on current crop so scale calculation is always accurate
            val outputSize = 600
            val outputHeight = (outputSize / country.aspectRatio).toInt().coerceAtLeast(1)
            val currentCrop = renderCurrentCrop(source, state, outputSize, outputHeight)
            val currentMeasurement = faceDetectionManager.detectFace(currentCrop)
                ?: state.faceMeasurement  // fallback to original if detection fails
                ?: return@launch

            val hairBuffer = currentMeasurement.normalizedBounds.height() * 0.12f
            val faceCenterX = currentMeasurement.normalizedBounds.centerX()
            val faceCenterY = currentMeasurement.normalizedBounds.centerY() - hairBuffer

            // Calculate how much MORE scale is needed from current position
            val currentRatio = currentMeasurement.headHeightRatio * 1.12f
            val scaleAdjustment = (targetRatio / currentRatio).coerceIn(0.5f, 2.0f)
            val newScale = (state.scale * scaleAdjustment).coerceIn(0.5f, 5.0f)

            val translationX = (0.5f - faceCenterX) * newScale * 600f
            val targetFacePositionY = 0.38f
            val translationY = (targetFacePositionY - faceCenterY) * newScale * 600f

            _uiState.value = state.copy(
                translationX = translationX,
                translationY = translationY,
                scale = newScale,
                exportBitmap = null,
                complianceState = ComplianceState.NotChecked
            )
        }
    }
    fun applyBackgroundMode(mode: BackgroundMode) {
        _uiState.value = _uiState.value.copy(backgroundMode = mode, exportBitmap = null)
        removeAndReplaceBackground()  // re-run segmentation with new color
    }
    fun removeAndReplaceBackground() {
        val source = _uiState.value.bitmap ?: return
        val color = when (_uiState.value.backgroundMode) {
            BackgroundMode.WHITE -> Color.WHITE
            BackgroundMode.LIGHT_BLUE -> Color.parseColor("#DCEBFF")
        }
        viewModelScope.launch {
            val result = BackgroundRemover.replaceBackground(source, color)
            _uiState.value = _uiState.value.copy(processedBitmap = result)
        }
    }
    fun validate() {
        val state = _uiState.value
        val source = state.bitmap ?: return
        val country = state.selectedCountry ?: repository.defaultCountry()

        viewModelScope.launch {
            // Render the current pan/zoom crop into a bitmap for accurate face detection
            val outputSize = 600
            val outputHeight = (outputSize / country.aspectRatio).toInt().coerceAtLeast(1)
            val cropped = renderCurrentCrop(source, state, outputSize, outputHeight)

            val measurement = faceDetectionManager.detectFace(cropped)
            _uiState.value = _uiState.value.copy(
                faceMeasurement = measurement,
                complianceState = validator.validate(state.selectedCountry, measurement)
            )
        }
    }

    private fun renderCurrentCrop(
        source: Bitmap,
        state: EditorUiState,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(android.graphics.Color.WHITE)

        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()

        // Must match applyMatrix() in EditorFragment — use minOf for fit-center
        val baseScale = minOf(outputWidth / imageWidth, outputHeight / imageHeight)
        val totalScale = baseScale * state.scale

        val matrix = android.graphics.Matrix()
        matrix.postScale(totalScale, totalScale)

        val scaledW = imageWidth * totalScale
        val scaledH = imageHeight * totalScale
        val dx = (outputWidth - scaledW) / 2f + (state.translationX / 600f) * outputWidth
        val dy = (outputHeight - scaledH) / 2f + (state.translationY / 600f) * outputHeight
        matrix.postTranslate(dx, dy)

        canvas.drawBitmap(source, matrix, null)
        return result
    }
    /**
     * Render a passport photo bitmap that respects the user's pan/zoom edits,
     * then build a printable sheet with the correct aspect ratio.

     * The export bitmap is rendered at 600 px on the shorter side so that it
     * maintains the country aspect ratio (e.g. 600×771 for India 35×45 mm).
     */
    fun buildExport(sheetCount: Int) {
        val state = _uiState.value
        val source = state.processedBitmap ?: state.bitmap ?: return
        val country = state.selectedCountry ?: repository.defaultCountry()

        val density = getApplication<android.app.Application>().resources.displayMetrics.density

        // Match exact editor view dimensions
        val editorPx = 420f * density

        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()

        // Match applyMatrix() exactly — minOf fit center
        val baseScale = minOf(editorPx / imageWidth, editorPx / imageHeight)
        val totalScale = baseScale * state.scale
        val scaledW = imageWidth * totalScale
        val scaledH = imageHeight * totalScale

        // Same as applyMatrix()
        val imgLeft = (editorPx - scaledW) / 2f + state.translationX
        val imgTop = (editorPx - scaledH) / 2f + state.translationY

        // Crop guide rect — matches CropGuideView 0.88f factor
        val guideW = editorPx * 0.88f
        val guideH = guideW / country.aspectRatio
        val guideLeft = (editorPx - guideW) / 2f
        val guideTop = (editorPx - guideH) / 2f

        // Output passport bitmap
        val outputWidth = 600
        val outputHeight = (outputWidth / country.aspectRatio).toInt().coerceAtLeast(1)

        val passport = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(passport)

        val bgColor = when (state.backgroundMode) {
            BackgroundMode.WHITE -> android.graphics.Color.WHITE
            BackgroundMode.LIGHT_BLUE -> android.graphics.Color.parseColor("#DCEBFF")
        }
        canvas.drawColor(bgColor)

        // Scale factor from guide rect to output bitmap
        val scaleX = outputWidth / guideW
        val scaleY = outputHeight / guideH

        // Image position relative to guide rect, scaled to output
        val finalLeft = (imgLeft - guideLeft) * scaleX
        val finalTop = (imgTop - guideTop) * scaleY
        val finalW = scaledW * scaleX
        val finalH = scaledH * scaleY

        val matrix = android.graphics.Matrix()
        matrix.postScale(finalW / imageWidth, finalH / imageHeight)
        matrix.postTranslate(finalLeft, finalTop)

        canvas.drawBitmap(source, matrix, null)

        val sheet = exportSheetBuilder.buildSheet(passport, sheetCount, country.aspectRatio)
        passport.recycle()

        _uiState.value = state.copy(exportBitmap = sheet)
    }

    fun resetWorkflow() {
        _uiState.value = EditorUiState(
            selectedCountry = repository.defaultCountry(),
            backgroundMode = repository.defaultCountry().backgroundMode
        )
    }
}
