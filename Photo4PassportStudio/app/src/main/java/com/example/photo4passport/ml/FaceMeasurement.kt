package com.example.photo4passport.ml

import android.graphics.RectF

data class FaceMeasurement(
    val normalizedBounds: RectF,
    val headHeightRatio: Float
)
