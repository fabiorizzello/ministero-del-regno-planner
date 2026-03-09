package org.example.project.feature.output.application

import java.nio.file.Path

interface FileOpener {
    fun open(path: Path)
}
