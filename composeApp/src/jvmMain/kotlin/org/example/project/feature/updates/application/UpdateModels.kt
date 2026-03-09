package org.example.project.feature.updates.application

import java.time.Instant
import org.example.project.core.config.UpdateChannel

data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class UpdateRelease(
    val version: String,
    val title: String?,
    val notes: String?,
    val asset: UpdateAsset?,
)

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val asset: UpdateAsset?,
    val checkedAt: Instant,
)
