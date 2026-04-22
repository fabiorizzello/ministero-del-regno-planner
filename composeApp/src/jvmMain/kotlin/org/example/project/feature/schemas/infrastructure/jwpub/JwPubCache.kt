package org.example.project.feature.schemas.infrastructure.jwpub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Serializable
data class JwPubMediaInfo(
    val url: String,
    val checksum: String,
    val modifiedDatetime: String,
    val filesize: Long,
)

data class CachedJwPub(val file: Path, val meta: JwPubMediaInfo)

@Serializable
private data class CacheSidecar(
    val info: JwPubMediaInfo,
    val fetchedAtEpoch: Long,
)

class JwPubCache(
    private val cacheDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false },
) {

    fun find(issue: String, lang: String): CachedJwPub? {
        val jwpubFile = jwpubPath(issue, lang)
        val metaFile = metaPath(issue, lang)
        if (!Files.exists(jwpubFile) || !Files.exists(metaFile)) return null
        val sidecar = runCatching {
            json.decodeFromString<CacheSidecar>(Files.readString(metaFile))
        }.getOrElse { return null }
        return CachedJwPub(file = jwpubFile, meta = sidecar.info)
    }

    fun store(
        issue: String,
        lang: String,
        bytes: ByteArray,
        info: JwPubMediaInfo,
    ): CachedJwPub {
        Files.createDirectories(cacheDir)
        val jwpubFile = jwpubPath(issue, lang)
        Files.write(jwpubFile, bytes)
        val sidecar = CacheSidecar(info = info, fetchedAtEpoch = Instant.now().epochSecond)
        Files.writeString(
            metaPath(issue, lang),
            json.encodeToString(CacheSidecar.serializer(), sidecar),
        )
        return CachedJwPub(file = jwpubFile, meta = info)
    }

    fun isUpToDate(cached: CachedJwPub?, info: JwPubMediaInfo): Boolean =
        cached != null && cached.meta.checksum == info.checksum

    private fun jwpubPath(issue: String, lang: String): Path =
        cacheDir.resolve("mwb_${lang}_${issue}.jwpub")

    private fun metaPath(issue: String, lang: String): Path =
        cacheDir.resolve("mwb_${lang}_${issue}.meta.json")
}
