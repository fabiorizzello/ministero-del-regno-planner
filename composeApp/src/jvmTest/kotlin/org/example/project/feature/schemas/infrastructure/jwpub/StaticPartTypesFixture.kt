package org.example.project.feature.schemas.infrastructure.jwpub

import org.example.project.feature.weeklyparts.domain.PartType

internal object StaticPartTypesFixture {
    fun all(): List<PartType> = StaticMeetingWorkbookPartTypes.all()
}
