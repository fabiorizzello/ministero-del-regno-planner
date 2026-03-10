package org.example.project.feature.output.domain

import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

@JvmInline
value class SlipDeliveryId(val value: String)

data class SlipDelivery(
    val id: SlipDeliveryId,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: WeekPlanId,
    val studentName: String,
    val assistantName: String?,
    val sentAt: Instant,
    val cancelledAt: Instant?,
) {
    val isActive: Boolean get() = cancelledAt == null
}

enum class SlipDeliveryStatus {
    DA_INVIARE,
    INVIATO,
    DA_REINVIARE,
}

data class SlipDeliveryInfo(
    val status: SlipDeliveryStatus,
    val activeDelivery: SlipDelivery?,
    val previousStudentName: String?,
)
