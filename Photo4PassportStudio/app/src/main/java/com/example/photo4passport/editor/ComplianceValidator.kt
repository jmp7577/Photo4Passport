package com.example.photo4passport.editor

import com.example.photo4passport.data.CountrySpec
import com.example.photo4passport.ml.FaceMeasurement
import com.example.photo4passport.state.ComplianceState

class ComplianceValidator {

    fun validate(countrySpec: CountrySpec?, faceMeasurement: FaceMeasurement?): ComplianceState {
        if (countrySpec == null) {
            return ComplianceState.Fail("Please select a country standard first.")
        }
        if (faceMeasurement == null) {
            return ComplianceState.Warning(
                "No face detected. Ensure the subject is well-lit, facing forward, " +
                "and centred in the frame, then tap Validate again."
            )
        }

        val ratio = faceMeasurement.headHeightRatio
        val minPct = (countrySpec.minHeadHeightRatio * 100).toInt()
        val maxPct = (countrySpec.maxHeadHeightRatio * 100).toInt()
        val actualPct = (ratio * 100).toInt()

        return when {
            ratio < countrySpec.minHeadHeightRatio ->
                ComplianceState.Warning(
                    "Head too small for ${countrySpec.displayName} (detected ~$actualPct%, " +
                    "need $minPct–$maxPct%). Use Auto Crop or zoom in."
                )
            ratio > countrySpec.maxHeadHeightRatio ->
                ComplianceState.Warning(
                    "Head too large for ${countrySpec.displayName} (detected ~$actualPct%, " +
                    "need $minPct–$maxPct%). Use Auto Crop or zoom out."
                )
            else ->
                ComplianceState.Pass(
                    "✓ Compliant with ${countrySpec.displayName} (head ~$actualPct%, " +
                    "required $minPct–$maxPct%)."
                )
        }
    }
}
