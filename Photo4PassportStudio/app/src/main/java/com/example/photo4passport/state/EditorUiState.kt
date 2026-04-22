package com.example.photo4passport.state

import android.graphics.Bitmap
import android.net.Uri
import com.example.photo4passport.data.BackgroundMode
import com.example.photo4passport.data.CountrySpec
import com.example.photo4passport.ml.FaceMeasurement

data class EditorUiState(
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val selectedCountry: CountrySpec? = null,
    val backgroundMode: BackgroundMode = BackgroundMode.WHITE,
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val faceMeasurement: FaceMeasurement? = null,
    val complianceState: ComplianceState = ComplianceState.NotChecked,
    val exportBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null
)
