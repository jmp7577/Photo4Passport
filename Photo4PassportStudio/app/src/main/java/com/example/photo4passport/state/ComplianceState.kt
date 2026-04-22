package com.example.photo4passport.state

sealed class ComplianceState(val message: String) {
    data object NotChecked : ComplianceState("Run validation to check the current crop.")
    data class Pass(val detail: String) : ComplianceState(detail)
    data class Warning(val detail: String) : ComplianceState(detail)
    data class Fail(val detail: String) : ComplianceState(detail)
}
