package org.example.project.ui.diagnostics

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

internal fun selectJsonFileForSeedImport(): File? {
    val dialog = FileDialog(null as Frame?, "Seleziona file JSON seed applicazione", FileDialog.LOAD).apply {
        directory = File(System.getProperty("user.home") ?: ".").absolutePath
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val selectedName = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    val selected = File(selectedDirectory, selectedName)
    if (!selected.isFile) return null
    if (!selected.name.endsWith(".json", ignoreCase = true)) return null
    return selected
}
