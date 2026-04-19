package org.example.project.ui.diagnostics

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.example.project.core.config.AppRuntime
import org.example.project.core.config.AppVersion
import org.example.project.core.config.AppRelauncher
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.core.config.PathsResolver
import org.example.project.core.config.UserConfigStore
import org.example.project.feature.people.application.CercaProclamatoriUseCase
import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.example.project.feature.diagnostics.application.ImportaSeedApplicazioneDaJsonUseCase
import org.example.project.feature.diagnostics.application.StoricoPreview
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperation
import org.example.project.ui.components.successNotice

private const val LOG_EXPORT_WINDOW_DAYS = 14L
private val BUNDLE_NAME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ITALIAN)

internal enum class DiagnosticsRetentionOption(
    val label: String,
    val months: Long,
) {
    SIX_MONTHS("6 mesi", 6),
    ONE_YEAR("1 anno", 12),
    TWO_YEARS("2 anni", 24),
    ;

    fun cutoffDate(referenceDate: LocalDate = LocalDate.now()): LocalDate = referenceDate.minusMonths(months)
}

internal typealias HistoricalCleanupPreview = StoricoPreview

internal data class DiagnosticsUiState(
    val appVersion: String = AppVersion.current,
    val dbPath: String = "n/d",
    val logsPath: String = "n/d",
    val exportsPath: String = "n/d",
    val dbSizeBytes: Long = 0L,
    val logsSizeBytes: Long = 0L,
    val selectedRetention: DiagnosticsRetentionOption = DiagnosticsRetentionOption.SIX_MONTHS,
    val cleanupPreview: HistoricalCleanupPreview = HistoricalCleanupPreview(),
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val isExporting: Boolean = false,
    val isImportingSeed: Boolean = false,
    val isApplyingDataSourceChange: Boolean = false,
    val canImportSeed: Boolean = false,
    val showCleanupConfirmDialog: Boolean = false,
    val notice: FeedbackBannerModel? = null,
)

internal class DiagnosticsViewModel(
    private val scope: CoroutineScope,
    private val cercaProclamatori: CercaProclamatoriUseCase,
    private val contaStorico: ContaStoricoUseCase,
    private val eliminaStorico: EliminaStoricoUseCase,
    private val importaSeedApplicazione: ImportaSeedApplicazioneDaJsonUseCase,
    private val userConfigStore: UserConfigStore,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun onScreenEntered() {
        refreshStorageUsage()
        refreshCleanupPreview()
        refreshSeedAvailability()
    }

    fun refreshStorageUsage() {
        scope.launch {
            _state.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { state, usage ->
                    state.copy(
                        isLoading = false,
                        dbSizeBytes = usage.dbSizeBytes,
                        logsSizeBytes = usage.logsSizeBytes,
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isLoading = false,
                        notice = errorNotice("Errore nel calcolo spazio disco: ${error.message}"),
                    )
                },
                operation = { loadStorageUsage() },
            )
        }
    }

    fun exportDiagnosticsBundle() {
        if (_state.value.isExporting) return
        scope.launch {
            _state.executeAsyncOperation(
                loadingUpdate = { it.copy(isExporting = true) },
                successUpdate = { state, bundlePath ->
                    state.copy(
                        isExporting = false,
                        notice = successNotice("Bundle creato: ${bundlePath.fileName}"),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isExporting = false,
                        notice = errorNotice("Export diagnostica non completato: ${error.message}"),
                    )
                },
                operation = { createDiagnosticsBundle() },
            )
        }
    }

    fun startSeedImport() {
        val current = _state.value
        if (current.isImportingSeed || current.isCleaning || current.isExporting) return
        scope.launch {
            _state.update { it.copy(isImportingSeed = true) }
            val selectedFile = selectJsonFileForSeedImport()
            if (selectedFile == null) {
                _state.update { it.copy(isImportingSeed = false) }
                return@launch
            }
            importSeedFileInternal(selectedFile)
        }
    }

    fun selectRetention(option: DiagnosticsRetentionOption) {
        _state.update { it.copy(selectedRetention = option) }
        refreshCleanupPreview()
    }

    fun refreshCleanupPreview() {
        scope.launch {
            _state.executeAsyncOperation(
                loadingUpdate = { it },
                successUpdate = { state, preview -> state.copy(cleanupPreview = preview) },
                errorUpdate = { state, error ->
                    state.copy(notice = errorNotice("Errore nel calcolo anteprima pulizia: ${error.message}"))
                },
                operation = { loadCleanupPreview(_state.value.selectedRetention.cutoffDate()) },
            )
        }
    }

    fun requestCleanup() {
        if (!_state.value.cleanupPreview.hasData || _state.value.isCleaning || _state.value.isExporting) return
        _state.update { it.copy(showCleanupConfirmDialog = true) }
    }

    fun dismissCleanupDialog() {
        _state.update { it.copy(showCleanupConfirmDialog = false) }
    }

    fun confirmCleanup() {
        val option = _state.value.selectedRetention
        val cutoffDate = option.cutoffDate()
        scope.launch {
            _state.executeEitherOperation(
                loadingUpdate = { it.copy(isCleaning = true, showCleanupConfirmDialog = false) },
                successUpdate = { state, result ->
                    val baseDetails = buildString {
                        append("Eliminate settimane: ${result.deleted.weekPlans}")
                        append(" | Parti: ${result.deleted.weeklyParts}")
                        append(" | Assegnazioni: ${result.deleted.assignments}")
                    }
                    val details = if (result.vacuumExecuted) {
                        "$baseDetails | Ottimizzazione archivio completata"
                    } else {
                        "$baseDetails | Ottimizzazione archivio rimandata (file in uso)"
                    }
                    state.copy(
                        isCleaning = false,
                        dbSizeBytes = result.usage.dbSizeBytes,
                        logsSizeBytes = result.usage.logsSizeBytes,
                        cleanupPreview = result.preview,
                        notice = successNotice(details),
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(
                        isCleaning = false,
                        notice = errorNotice("Pulizia dati non completata: ${error.toMessage()}"),
                    )
                },
                operation = {
                    val deleted = contaStorico(cutoffDate)
                    eliminaStorico(cutoffDate).map { eliminaResult ->
                        val updatedUsage = loadStorageUsage()
                        val updatedPreview = contaStorico(cutoffDate)
                        CleanupExecutionResult(deleted, updatedUsage, updatedPreview, eliminaResult.vacuumExecuted)
                    }
                },
            )
        }
    }

    fun openLogsFolder() = openPath(AppRuntime.paths().logsDir, "Apertura cartella log non riuscita")

    fun openDataFolder() = openPath(AppRuntime.paths().dbFile.parent, "Apertura cartella dati non riuscita")

    fun openExportsFolder() = openPath(AppRuntime.paths().exportsDir, "Apertura cartella export non riuscita")

    fun moveDataToNewFolder() {
        val current = _state.value
        if (current.isApplyingDataSourceChange || current.isCleaning || current.isExporting || current.isImportingSeed) return
        scope.launch {
            _state.update { it.copy(isApplyingDataSourceChange = true) }
            runCatching {
                val currentDbFile = AppRuntime.paths().dbFile
                val selectedDirectory = selectDirectoryForDatabaseMove(currentDbFile.parent.toFile())
                if (selectedDirectory == null) {
                    _state.update { it.copy(isApplyingDataSourceChange = false) }
                    return@runCatching false
                }
                withContext(Dispatchers.IO) {
                    moveDatabaseToFile(selectedDirectory.toPath().resolve(currentDbFile.fileName.toString()))
                }
                true
            }.onSuccess { moved ->
                if (!moved) return@onSuccess
                AppRelauncher.relaunch().onFailure { error ->
                    _state.update {
                        it.copy(
                            isApplyingDataSourceChange = false,
                            notice = errorNotice("Dati spostati ma riavvio automatico non riuscito: ${error.message}"),
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isApplyingDataSourceChange = false,
                        notice = errorNotice("Spostamento dati non completato: ${error.message}"),
                    )
                }
            }
        }
    }

    fun selectDataFile() {
        val current = _state.value
        if (current.isApplyingDataSourceChange || current.isCleaning || current.isExporting || current.isImportingSeed) return
        scope.launch {
            _state.update { it.copy(isApplyingDataSourceChange = true) }
            runCatching {
                val selectedFile = selectDatabaseFile(AppRuntime.paths().dbFile.parent.toFile())
                if (selectedFile == null) {
                    _state.update { it.copy(isApplyingDataSourceChange = false) }
                    return@runCatching false
                }
                withContext(Dispatchers.IO) {
                    configureDatabaseFile(selectedFile.toPath())
                }
                true
            }.onSuccess { selected ->
                if (!selected) return@onSuccess
                AppRelauncher.relaunch().onFailure { error ->
                    _state.update {
                        it.copy(
                            isApplyingDataSourceChange = false,
                            notice = errorNotice("Sorgente dati salvata ma riavvio automatico non riuscito: ${error.message}"),
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isApplyingDataSourceChange = false,
                        notice = errorNotice("Selezione sorgente dati non completata: ${error.message}"),
                    )
                }
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun copySupportInfo() {
        val snapshot = _state.value
        val content = buildSupportInfo(snapshot)
        runCatching {
            val selection = StringSelection(content)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }.onSuccess {
            _state.update { it.copy(notice = successNotice("Info supporto copiate negli appunti")) }
        }.onFailure { error ->
            _state.update {
                it.copy(notice = errorNotice("Copia info supporto non riuscita: ${error.message}"))
            }
        }
    }

    fun refreshSeedAvailability() {
        scope.launch {
            val canImport = runCatching { cercaProclamatori(null).isEmpty() }
                .getOrDefault(false)
            _state.update { it.copy(canImportSeed = canImport) }
        }
    }

    private suspend fun importSeedFileInternal(selectedFile: File) {
        val fileSizeMb = selectedFile.length() / (1024 * 1024)
        if (fileSizeMb > 10) {
            _state.update {
                it.copy(
                    isImportingSeed = false,
                    notice = errorNotice("File troppo grande (${fileSizeMb}MB). Limite: 10MB"),
                )
            }
            return
        }

        val jsonContent = withContext(Dispatchers.IO) {
            runCatching { selectedFile.readText(Charsets.UTF_8) }.getOrNull()
        }
        if (jsonContent == null) {
            _state.update {
                it.copy(
                    isImportingSeed = false,
                    notice = errorNotice("Impossibile leggere il file selezionato"),
                )
            }
            return
        }

        _state.executeEitherOperation(
            loadingUpdate = { it },
            successUpdate = { state, result ->
                state.copy(
                    isImportingSeed = false,
                    canImportSeed = false,
                    notice = successNotice(
                        "Seed completato: ${result.importedPartTypes} tipi parte, " +
                            "${result.importedStudents} studenti, " +
                            "${result.importedLeadEligibility} idoneita' conduzione, " +
                            "${result.importedHistoricalAssignments} ultime parti importate",
                    ),
                )
            },
            errorUpdate = { state, error ->
                state.copy(
                    isImportingSeed = false,
                    canImportSeed = if (error == DomainError.ImportArchivioNonVuoto) false else state.canImportSeed,
                    notice = errorNotice("Import seed non completato: ${error.toMessage()}"),
                )
            },
            operation = {
                withContext(Dispatchers.IO) {
                    importaSeedApplicazione(jsonContent, referenceDate = LocalDate.now())
                }
            },
        )
    }

    private fun openPath(path: Path, errorPrefix: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile())
            } else {
                ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start()
            }
        }.onFailure { error ->
            _state.update { it.copy(notice = errorNotice("$errorPrefix: ${error.message}")) }
        }
    }

    private fun initialState(): DiagnosticsUiState {
        val paths = AppRuntime.pathsOrNull()
        return DiagnosticsUiState(
            dbPath = paths?.dbFile?.toAbsolutePath()?.toString() ?: "n/d",
            logsPath = paths?.logsDir?.toAbsolutePath()?.toString() ?: "n/d",
            exportsPath = paths?.exportsDir?.toAbsolutePath()?.toString() ?: "n/d",
        )
    }

    private suspend fun createDiagnosticsBundle(): Path = withContext(Dispatchers.IO) {
        val paths = AppRuntime.paths()
        Files.createDirectories(paths.exportsDir)

        val timestamp = LocalDateTime.now().format(BUNDLE_NAME_FORMATTER)
        val versionToken = AppVersion.current.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val bundlePath = paths.exportsDir.resolve("diagnostica-$timestamp-v$versionToken.zip")

        val databaseFiles = listOf(
            paths.dbFile,
            Path.of("${paths.dbFile}-wal"),
            Path.of("${paths.dbFile}-shm"),
        ).filter { Files.exists(it) && Files.isRegularFile(it) }

        val logs = collectRecentLogs(paths.logsDir, LOG_EXPORT_WINDOW_DAYS)

        ZipOutputStream(
            BufferedOutputStream(
                Files.newOutputStream(
                    bundlePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                ),
            ),
        ).use { zip ->
            databaseFiles.forEach { file ->
                addFileToZip(zip, file, "database/${file.fileName}")
            }
            logs.forEach { file ->
                addFileToZip(zip, file, "logs/${file.fileName}")
            }
            val metadata = buildMetadataJson(bundlePath, databaseFiles, logs)
            addStringToZip(zip, "metadata.json", metadata)
        }

        bundlePath
    }

    private fun buildMetadataJson(
        bundlePath: Path,
        databaseFiles: List<Path>,
        logs: List<Path>,
    ): String {
        val databaseBytes = databaseFiles.sumOf(::fileSize)
        val logsBytes = logs.sumOf(::fileSize)

        return buildJsonObject {
            put("generatedAt", OffsetDateTime.now().toString())
            put("appVersion", AppVersion.current)
            put("bundleFileName", bundlePath.fileName.toString())
            putJsonObject("os") {
                put("name", System.getProperty("os.name") ?: "n/d")
                put("version", System.getProperty("os.version") ?: "n/d")
                put("arch", System.getProperty("os.arch") ?: "n/d")
            }
            putJsonObject("java") {
                put("version", System.getProperty("java.version") ?: "n/d")
                put("vendor", System.getProperty("java.vendor") ?: "n/d")
            }
            putJsonObject("paths") {
                put("db", _state.value.dbPath)
                put("logs", _state.value.logsPath)
                put("exports", _state.value.exportsPath)
            }
            putJsonObject("retention") {
                put("logsDaysIncluded", LOG_EXPORT_WINDOW_DAYS)
            }
            putJsonObject("counts") {
                put("databaseFiles", databaseFiles.size)
                put("logFiles", logs.size)
            }
            putJsonObject("sizesBytes") {
                put("database", databaseBytes)
                put("logs", logsBytes)
            }
        }.toString()
    }

    private suspend fun loadStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val paths = AppRuntime.paths()
        StorageUsage(
            dbSizeBytes = fileSize(paths.dbFile),
            logsSizeBytes = directorySize(paths.logsDir),
        )
    }

    private suspend fun loadCleanupPreview(cutoffDate: LocalDate): HistoricalCleanupPreview = contaStorico(cutoffDate)

    private fun buildSupportInfo(state: DiagnosticsUiState): String = buildString {
        appendLine("Versione app: ${state.appVersion}")
        appendLine("Generato il: ${OffsetDateTime.now()}")
        appendLine("DB: ${state.dbPath}")
        appendLine("Log: ${state.logsPath}")
        appendLine("Export: ${state.exportsPath}")
        appendLine("Spazio DB: ${state.dbSizeBytes} bytes")
        appendLine("Spazio Log: ${state.logsSizeBytes} bytes")
        appendLine("Spazio Totale: ${state.dbSizeBytes + state.logsSizeBytes} bytes")
        appendLine("Periodo selezionato: ${state.selectedRetention.label}")
        appendLine("Data limite selezionata: ${state.selectedRetention.cutoffDate()}")
        appendLine(
            "Anteprima pulizia: settimane ${state.cleanupPreview.weekPlans}, " +
                "parti ${state.cleanupPreview.weeklyParts}, " +
                "assegnazioni ${state.cleanupPreview.assignments}",
        )
        appendLine("Stato import seed: ${if (state.isImportingSeed) "in corso" else "idle"}")
        appendLine("Stato export: ${if (state.isExporting) "in corso" else "idle"}")
        appendLine("Stato pulizia: ${if (state.isCleaning) "in corso" else "idle"}")
        appendLine("Cambio sorgente dati: ${if (state.isApplyingDataSourceChange) "in corso" else "idle"}")
    }

    private fun moveDatabaseToFile(targetDbFile: Path) {
        val currentDbFile = AppRuntime.paths().dbFile.toAbsolutePath().normalize()
        val normalizedTargetDbFile = targetDbFile.toAbsolutePath().normalize()
        if (normalizedTargetDbFile == currentDbFile) {
            throw IOException("Il database usa gia questa cartella")
        }
        if (Files.exists(normalizedTargetDbFile)) {
            throw IOException("Nel percorso selezionato esiste gia ${normalizedTargetDbFile.fileName}")
        }

        Files.createDirectories(normalizedTargetDbFile.parent)
        copyDatabaseFileSet(currentDbFile, normalizedTargetDbFile)
        saveConfiguredDatabase(normalizedTargetDbFile, previousDbFileToCleanup = currentDbFile)
    }

    private fun configureDatabaseFile(selectedFile: Path) {
        val normalizedSelectedFile = selectedFile.toAbsolutePath().normalize()
        if (!Files.exists(normalizedSelectedFile) || !Files.isRegularFile(normalizedSelectedFile)) {
            throw IOException("File selezionato non valido")
        }
        val currentDbFile = AppRuntime.paths().dbFile.toAbsolutePath().normalize()
        if (normalizedSelectedFile == currentDbFile) {
            throw IOException("Il file selezionato e gia la sorgente dati corrente")
        }
        saveConfiguredDatabase(normalizedSelectedFile, previousDbFileToCleanup = currentDbFile)
    }

    private fun saveConfiguredDatabase(
        dbFile: Path,
        previousDbFileToCleanup: Path? = null,
    ) {
        userConfigStore.saveDatabaseFile(dbFile.takeUnless {
            it == PathsResolver.defaultDatabaseFile().toAbsolutePath().normalize()
        }, pendingCleanupPath = previousDbFileToCleanup)
    }
}

private data class StorageUsage(
    val dbSizeBytes: Long,
    val logsSizeBytes: Long,
)

private data class CleanupExecutionResult(
    val deleted: HistoricalCleanupPreview,
    val usage: StorageUsage,
    val preview: HistoricalCleanupPreview,
    val vacuumExecuted: Boolean,
)

private fun collectRecentLogs(logsDir: Path, retentionDays: Long): List<Path> {
    if (!Files.exists(logsDir) || !Files.isDirectory(logsDir)) return emptyList()
    val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
    return Files.list(logsDir).use { stream ->
        stream
            .filter { Files.isRegularFile(it) }
            .filter { path ->
                runCatching { Files.getLastModifiedTime(path).toInstant() }
                    .getOrDefault(Instant.EPOCH)
                    .isAfter(cutoff)
            }
            .sorted(compareBy<Path> { it.fileName.toString() })
            .toList()
    }
}

private fun addFileToZip(zip: ZipOutputStream, source: Path, entryName: String) {
    if (!Files.exists(source) || !Files.isRegularFile(source)) return
    val normalizedName = entryName.replace('\\', '/')
    val entry = ZipEntry(normalizedName)
    entry.time = runCatching { Files.getLastModifiedTime(source).toMillis() }.getOrDefault(System.currentTimeMillis())
    zip.putNextEntry(entry)
    BufferedInputStream(Files.newInputStream(source)).use { input ->
        input.copyTo(zip)
    }
    zip.closeEntry()
}

private fun addStringToZip(zip: ZipOutputStream, entryName: String, content: String) {
    val normalizedName = entryName.replace('\\', '/')
    val entry = ZipEntry(normalizedName)
    zip.putNextEntry(entry)
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}

private fun fileSize(path: Path): Long {
    if (!Files.exists(path) || !Files.isRegularFile(path)) return 0L
    return runCatching { Files.size(path) }.getOrDefault(0L)
}

private fun directorySize(path: Path): Long {
    if (!Files.exists(path) || !Files.isDirectory(path)) return 0L
    return runCatching {
        Files.walk(path).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .mapToLong { file -> runCatching { Files.size(file) }.getOrDefault(0L) }
                .sum()
        }
    }.getOrDefault(0L)
}

private fun copyDatabaseFileSet(sourceDbFile: Path, targetDbFile: Path) {
    if (!Files.exists(sourceDbFile) || !Files.isRegularFile(sourceDbFile)) {
        throw IOException("Database corrente non disponibile")
    }
    copyFile(sourceDbFile, targetDbFile)
    listOf("-wal", "-shm", "-journal").forEach { suffix ->
        val source = Path.of("$sourceDbFile$suffix")
        if (Files.exists(source) && Files.isRegularFile(source)) {
            copyFile(source, Path.of("$targetDbFile$suffix"))
        }
    }
}

private fun copyFile(source: Path, target: Path) {
    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
}
