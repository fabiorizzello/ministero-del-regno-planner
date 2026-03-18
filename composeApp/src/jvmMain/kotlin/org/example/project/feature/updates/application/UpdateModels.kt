package org.example.project.feature.updates.application

import java.time.Instant
import java.nio.file.Path

data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

enum class UpdateSource {
    GITHUB,
    LOCAL,
}

data class UpdateRelease(
    val version: String,
    val title: String?,
    val notes: String?,
    val asset: UpdateAsset?,
    val source: UpdateSource = UpdateSource.GITHUB,
)

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val asset: UpdateAsset?,
    val releaseTitle: String?,
    val releaseNotes: String?,
    val source: UpdateSource?,
    val checkedAt: Instant,
)

data class UpdateInstallResult(
    val installerPath: Path,
    val restartRequired: Boolean,
    val updaterCommand: List<String>,
)

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { downloadedBytes.toFloat() / it.toFloat() }
            ?.coerceIn(0f, 1f)
}
