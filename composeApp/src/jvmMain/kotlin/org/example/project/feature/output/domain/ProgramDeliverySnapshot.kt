package org.example.project.feature.output.domain

data class ProgramDeliverySnapshot(
    val pending: Int,
    val blocked: Int,
) {
    val allDelivered: Boolean get() = pending == 0 && blocked == 0
}
