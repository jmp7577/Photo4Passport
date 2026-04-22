package com.example.photo4passport.data

enum class BackgroundMode {
    WHITE,
    LIGHT_BLUE
}

data class CountrySpec(
    val code: String,
    val displayName: String,
    val widthMm: Float,
    val heightMm: Float,
    val minHeadHeightRatio: Float,
    val maxHeadHeightRatio: Float,
    val backgroundMode: BackgroundMode
) {
    val aspectRatio: Float = widthMm / heightMm
    override fun toString(): String = displayName
}
