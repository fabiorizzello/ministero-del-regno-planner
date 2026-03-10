package org.example.project.feature.updates.application

import arrow.core.Either
import org.example.project.core.config.UpdateChannel
import org.example.project.core.domain.DomainError

interface UpdateReleaseSource {
    suspend fun fetchLatestRelease(channel: UpdateChannel): Either<DomainError, UpdateRelease?>
}
