package org.example.project.ui.diagnostics

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.DriverManager
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
import org.example.project.db.MinisteroDatabase
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
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

internal data class HistoricalCleanupPreview(
    val weekPlans: Int = 0,
    val weeklyParts: Int = 0,
    val assignments: Int = 0,
) {
    val hasData: Boolean get() = weekPlans > 0 || weeklyParts > 0 || assignments > 0
}

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
    val showCleanupConfirmDialog: Boolean = false,
    val notice: FeedbackBannerModel? = null,
)

internal class DiagnosticsViewModel(
    private val scope: CoroutineScope,
    private val database: MinisteroDatabase,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun onScreenEntered() {
        refreshStorageUsage()
        refreshCleanupPreview()
    }

    fun refreshStorageUsage() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                loadStorageUsage()
            }.onSuccess { usage ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        dbSizeBytes = usage.dbSizeBytes,
                        logsSizeBytes = usage.logsSizeBytes,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        notice = errorNotice("Errore nel calcolo spazio disco: ${error.message}"),
                    )
                }
            }
        }
    }

    fun exportDiagnosticsBundle() {
        if (_state.value.isExporting) return
        scope.launch {
            _state.update { it.copy(isExporting = true) }
            runCatching {
                createDiagnosticsBundle()
            }.onSuccess { bundlePath ->
                _state.update {
                    it.copy(
                        isExporting = false,
                        notice = successNotice("Bundle creato: ${bundlePath.fileName}"),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isExporting = false,
                        notice = errorNotice("Export diagnostica non completato: ${error.message}"),
                    )
                }
            }
        }
    }

    fun selectRetention(option: DiagnosticsRetentionOption) {
        _state.update { it.copy(selectedRetention = option) }
        refreshCleanupPreview()
    }

    fun refreshCleanupPreview() {
        scope.launch {
            runCatching {
                loadCleanupPreview(_state.value.selectedRetention.cutoffDate())
            }.onSuccess { preview ->
                _state.update { it.copy(cleanupPreview = preview) }
            }.onFailure { error ->
                _state.update {
                    it.copy(notice = errorNotice("Errore nel calcolo anteprima pulizia: ${error.message}"))
                }
            }
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
            _state.update { it.copy(isCleaning = true, showCleanupConfirmDialog = false) }
            runCatching {
                val deleted = loadCleanupPreview(cutoffDate)
                database.ministeroDatabaseQueries.deleteWeekPlansBeforeDate(cutoffDate.toString())
                val vacuumOk = runVacuum()
                val updatedUsage = loadStorageUsage()
                val updatedPreview = loadCleanupPreview(cutoffDate)
                CleanupExecutionResult(deleted, updatedUsage, updatedPreview, vacuumOk)
            }.onSuccess { result ->
                val baseDetails = buildString {
                    append("Eliminate settimane: ${result.deleted.weekPlans}")
                    append(" | Parti: ${result.deleted.weeklyParts}")
                    append(" | Assegnazioni: ${result.deleted.assignments}")
                }
                val details = if (result.vacuumExecuted) {
                    "$baseDetails | VACUUM completato"
                } else {
                    "$baseDetails | VACUUM non eseguito (DB in uso)"
                }
                _state.update {
                    it.copy(
                        isCleaning = false,
                        dbSizeBytes = result.usage.dbSizeBytes,
                        logsSizeBytes = result.usage.logsSizeBytes,
                        cleanupPreview = result.preview,
                        notice = successNotice(details),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isCleaning = false,
                        notice = errorNotice("Pulizia dati non completata: ${error.message}"),
                    )
                }
            }
        }
    }

    fun openLogsFolder() = openPath(AppRuntime.paths().logsDir, "Apertura cartella log non riuscita")

    fun openDataFolder() = openPath(AppRuntime.paths().dbFile.parent, "Apertura cartella dati non riuscita")

    fun openExportsFolder() = openPath(AppRuntime.paths().exportsDir, "Apertura cartella export non riuscita")

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

    private suspend fun loadCleanupPreview(cutoffDate: LocalDate): HistoricalCleanupPreview = withContext(Dispatchers.IO) {
        val cutoff = cutoffDate.toString()
        val weekPlans = database.ministeroDatabaseQueries.countWeekPlansBeforeDate(cutoff).executeAsOne().toInt()
        val weeklyParts = database.ministeroDatabaseQueries.countWeeklyPartsBeforeDate(cutoff).executeAsOne().toInt()
        val assignments = database.ministeroDatabaseQueries.countAssignmentsBeforeDate(cutoff).executeAsOne().toInt()
        HistoricalCleanupPreview(
            weekPlans = weekPlans,
            weeklyParts = weeklyParts,
            assignments = assignments,
        )
    }

    private suspend fun runVacuum(): Boolean = withContext(Dispatchers.IO) {
        val dbPath = AppRuntime.paths().dbFile.toAbsolutePath().toString()
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("VACUUM;")
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun buildSupportInfo(state: DiagnosticsUiState): String = buildString {
        appendLine("Versione app: ${state.appVersion}")
        appendLine("Generato il: ${OffsetDateTime.now()}")
        appendLine("DB: ${state.dbPath}")
        appendLine("Log: ${state.logsPath}")
        appendLine("Export: ${state.exportsPath}")
        appendLine("Spazio DB: ${state.dbSizeBytes} bytes")
        appendLine("Spazio Log: ${state.logsSizeBytes} bytes")
        appendLine("Spazio Totale: ${state.dbSizeBytes + state.logsSizeBytes} bytes")
        appendLine("Retention selezionata: ${state.selectedRetention.label}")
        appendLine("Cutoff retention: ${state.selectedRetention.cutoffDate()}")
        appendLine(
            "Anteprima pulizia: settimane ${state.cleanupPreview.weekPlans}, " +
                "parti ${state.cleanupPreview.weeklyParts}, " +
                "assegnazioni ${state.cleanupPreview.assignments}",
        )
        appendLine("Stato export: ${if (state.isExporting) "in corso" else "idle"}")
        appendLine("Stato pulizia: ${if (state.isCleaning) "in corso" else "idle"}")
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
