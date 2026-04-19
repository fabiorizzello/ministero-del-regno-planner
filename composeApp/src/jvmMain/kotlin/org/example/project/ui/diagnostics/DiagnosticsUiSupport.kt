package org.example.project.ui.diagnostics

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.charset.Charset

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

internal fun selectDirectoryForDatabaseMove(initialDirectory: File): File? {
    if (!isWindows()) return null

    val escapedInitialDirectory = initialDirectory.absolutePath.replace("'", "''")
    val script = """
        Add-Type -AssemblyName System.Windows.Forms
        ${'$'}dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        ${'$'}dialog.Description = 'Seleziona la cartella in cui salvare il database'
        ${'$'}dialog.UseDescriptionForTitle = ${'$'}true
        ${'$'}dialog.ShowNewFolderButton = ${'$'}true
        ${'$'}dialog.SelectedPath = '$escapedInitialDirectory'
        if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Write-Output ${'$'}dialog.SelectedPath
        }
    """.trimIndent()

    val process = ProcessBuilder(
        "powershell",
        "-NoProfile",
        "-STA",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        script,
    )
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader(Charset.forName("UTF-8")).use { it.readText().trim() }
    val exitCode = process.waitFor()
    if (exitCode != 0 || output.isBlank()) return null
    val selected = File(output.lineSequence().last().trim())
    return selected.takeIf { it.isDirectory }
}

private fun isWindows(): Boolean =
    (System.getProperty("os.name") ?: "").contains("windows", ignoreCase = true)

internal fun selectDatabaseFile(initialDirectory: File): File? {
    val dialog = FileDialog(null as Frame?, "Seleziona file database da usare come sorgente dati", FileDialog.LOAD).apply {
        directory = initialDirectory.absolutePath
        filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".sqlite", ignoreCase = true) || name.endsWith(".db", ignoreCase = true)
        }
        isVisible = true
    }
    val selectedName = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    val selected = File(selectedDirectory, selectedName)
    return selected.takeIf { it.isFile }
}
