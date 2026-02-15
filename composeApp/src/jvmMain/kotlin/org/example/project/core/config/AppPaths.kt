package org.example.project.core.config

import java.nio.file.Path

data class AppPaths(
    val rootDir: Path,
    val dbFile: Path,
    val logsDir: Path,
    val exportsDir: Path,
)
