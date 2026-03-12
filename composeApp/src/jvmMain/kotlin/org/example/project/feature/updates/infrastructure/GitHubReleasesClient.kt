package org.example.project.feature.updates.infrastructure

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.core.config.RemoteConfig
import org.example.project.core.domain.DomainError
import org.example.project.feature.updates.application.UpdateAsset
import org.example.project.core.config.UpdateChannel
import org.example.project.feature.updates.application.UpdateRelease
import org.example.project.feature.updates.application.UpdateReleaseSource
import org.example.project.feature.updates.application.UpdateSource

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)

class GitHubReleasesClient(
    private val httpClient: HttpClient,
    private val systemPropertyReader: (String) -> String? = System::getProperty,
    private val environmentReader: (String) -> String? = System::getenv,
) : UpdateReleaseSource {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchLatestRelease(channel: UpdateChannel): Either<DomainError, UpdateRelease?> = either {
        localOverrideReleaseOrNull()?.let { return@either it }

        val repo = RemoteConfig.UPDATE_REPO
        val endpoint = if (channel == UpdateChannel.STABLE) {
            "https://api.github.com/repos/$repo/releases/latest"
        } else {
            "https://api.github.com/repos/$repo/releases"
        }

        val body = executeGet(endpoint).bind()
        if (channel == UpdateChannel.STABLE) {
            parseRelease(body).bind()
        } else {
            parseReleaseList(body).bind().firstOrNull()
        }
    }

    private suspend fun executeGet(url: String): Either<DomainError, String> = either {
        val response = Either.catch {
            val response = httpClient.get(url) {
                header("Accept", "application/vnd.github+json")
            }
            response
        }.mapLeft { error ->
            DomainError.Network("Errore verifica aggiornamenti: ${error.message ?: "Connessione fallita"}")
        }.bind()
        if (!response.status.isSuccess()) {
            logger.warn { "GitHub API risposta non valida ${response.status.value} per $url" }
            raise(DomainError.Network("Errore verifica aggiornamenti: HTTP ${response.status.value}"))
        }
        Either.catch { response.bodyAsText() }
            .mapLeft { error ->
                DomainError.Network("Errore verifica aggiornamenti: ${error.message ?: "Connessione fallita"}")
            }
            .bind()
    }

    private fun parseRelease(payload: String): Either<DomainError, UpdateRelease?> =
        Either.catch {
            json.decodeFromString<GitHubReleaseDto>(payload).toUpdateRelease()
        }.mapLeft { error ->
            logger.warn(error) { "Parsing release GitHub fallito: ${error.message}" }
            DomainError.Network("Risposta GitHub non valida: ${error.message ?: "payload malformato"}")
        }

    private fun parseReleaseList(payload: String): Either<DomainError, List<UpdateRelease>> =
        Either.catch {
            json.decodeFromString<List<GitHubReleaseDto>>(payload)
                .mapNotNull { it.toUpdateRelease() }
        }.mapLeft { error ->
            logger.warn(error) { "Parsing lista release GitHub fallito: ${error.message}" }
            DomainError.Network("Risposta GitHub non valida: ${error.message ?: "payload malformato"}")
        }

    private fun GitHubReleaseDto.toUpdateRelease(): UpdateRelease? {
        if (tagName.isNullOrBlank()) return null
        val asset = assets
            .firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
            ?: assets.firstOrNull()
        return UpdateRelease(
            version = tagName,
            title = name,
            notes = body,
            asset = asset?.let { UpdateAsset(it.name, it.browserDownloadUrl, it.size) },
            source = UpdateSource.GITHUB,
        )
    }

    private fun localOverrideReleaseOrNull(): UpdateRelease? {
        explicitLocalOverrideOrNull()?.let { return it }
        if (!readBooleanOverride(
                propertyName = RemoteConfig.UPDATE_USE_LOCAL_BUILD_PROPERTY,
                envName = RemoteConfig.UPDATE_USE_LOCAL_BUILD_ENV,
            )
        ) {
            return null
        }
        return autoDetectedLocalBuildOverrideOrNull()
    }

    private fun explicitLocalOverrideOrNull(): UpdateRelease? {
        val localMsiPath = readOverride(
            propertyName = RemoteConfig.UPDATE_LOCAL_MSI_PATH_PROPERTY,
            envName = RemoteConfig.UPDATE_LOCAL_MSI_PATH_ENV,
        ) ?: return null
        val localVersion = readOverride(
            propertyName = RemoteConfig.UPDATE_LOCAL_VERSION_PROPERTY,
            envName = RemoteConfig.UPDATE_LOCAL_VERSION_ENV,
        ) ?: return null

        val resolvedPath = Paths.get(localMsiPath).toAbsolutePath().normalize()
        if (!Files.isRegularFile(resolvedPath)) {
            logger.warn { "Override update locale ignorato: file non trovato $resolvedPath" }
            return null
        }

        logger.info { "Uso override update locale: $resolvedPath ($localVersion)" }
        return UpdateRelease(
            version = localVersion,
            title = "Release locale $localVersion",
            notes = "Override locale attivo: installazione da file",
            asset = UpdateAsset(
                name = resolvedPath.fileName.toString(),
                downloadUrl = resolvedPath.toUri().toString(),
                sizeBytes = Files.size(resolvedPath),
            ),
            source = UpdateSource.LOCAL,
        )
    }

    private fun autoDetectedLocalBuildOverrideOrNull(): UpdateRelease? {
        val repoRoot = findProjectRoot() ?: run {
            logger.warn { "Override update locale automatico ignorato: root progetto non trovata" }
            return null
        }
        val msiDir = repoRoot.resolve("composeApp").resolve("build").resolve("compose").resolve("binaries").resolve("main").resolve("msi")
        if (!Files.isDirectory(msiDir)) {
            logger.warn { "Override update locale automatico ignorato: cartella MSI non trovata $msiDir" }
            return null
        }
        val latestMsi = Files.list(msiDir).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".msi", ignoreCase = true) }
                .max(Comparator.comparingLong { path -> Files.getLastModifiedTime(path).toMillis() })
                .orElse(null)
        } ?: run {
            logger.warn { "Override update locale automatico ignorato: nessun MSI trovato in $msiDir" }
            return null
        }
        val localVersion = deriveVersionFromInstaller(latestMsi)
        logger.info { "Uso override update locale automatico: $latestMsi ($localVersion)" }
        return UpdateRelease(
            version = localVersion,
            title = "Build locale ${displayVersion(localVersion)}",
            notes = "Override locale automatico attivo: installazione da build locale del progetto",
            asset = UpdateAsset(
                name = latestMsi.fileName.toString(),
                downloadUrl = latestMsi.toUri().toString(),
                sizeBytes = Files.size(latestMsi),
            ),
            source = UpdateSource.LOCAL,
        )
    }

    private fun findProjectRoot(): Path? {
        val candidates = buildList {
            readOverride("user.dir", "PWD")?.let { add(Paths.get(it).toAbsolutePath().normalize()) }
            classLocationPath()?.let { add(it) }
        }
        return candidates
            .asSequence()
            .distinct()
            .mapNotNull(::findProjectRootFrom)
            .firstOrNull()
    }

    private fun classLocationPath(): Path? =
        runCatching {
            Paths.get(GitHubReleasesClient::class.java.protectionDomain.codeSource.location.toURI())
                .toAbsolutePath()
                .normalize()
        }.getOrNull()

    private fun findProjectRootFrom(start: Path): Path? {
        var current: Path? = if (Files.isDirectory(start)) start else start.parent
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts")) && Files.exists(current.resolve("composeApp"))) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun deriveVersionFromInstaller(installerPath: Path): String {
        val fileName = installerPath.fileName.toString()
        val match = LOCAL_INSTALLER_VERSION_REGEX.matchEntire(fileName)
        val version = match?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
            ?: fileName.removeSuffix(".msi")
        return bumpLocalBuildVersion(normalizeVersion(version))
    }

    private fun bumpLocalBuildVersion(version: String): String {
        val components = version
            .split('.')
            .mapNotNull { part -> part.toIntOrNull() }
            .toMutableList()
        while (components.size < 3) {
            components += 0
        }
        val patchIndex = 2
        components[patchIndex] = components[patchIndex] + 1
        return components.take(3).joinToString(".")
    }

    private fun readBooleanOverride(propertyName: String, envName: String): Boolean =
        readOverride(propertyName, envName)
            ?.trim()
            ?.lowercase()
            ?.let { value -> value == "1" || value == "true" || value == "yes" || value == "on" }
            ?: false

    private fun readOverride(propertyName: String, envName: String): String? =
        systemPropertyReader(propertyName)
            ?.takeIf(String::isNotBlank)
            ?: environmentReader(envName)?.takeIf(String::isNotBlank)

    private fun normalizeVersion(version: String): String = version.removePrefix("v")

    private fun displayVersion(version: String): String = "v${normalizeVersion(version)}"

    private companion object {
        private val LOCAL_INSTALLER_VERSION_REGEX = Regex("""scuola-di-ministero-(.+)\.msi""", RegexOption.IGNORE_CASE)
    }
}
