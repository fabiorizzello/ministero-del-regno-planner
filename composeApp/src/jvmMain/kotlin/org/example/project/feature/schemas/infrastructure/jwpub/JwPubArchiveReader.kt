package org.example.project.feature.schemas.infrastructure.jwpub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

@Serializable
data class JwPubManifest(val name: String, val publication: JwPubManifestPublication)

@Serializable
data class JwPubManifestPublication(
    val fileName: String,
    val symbol: String,
    val uniqueEnglishSymbol: String? = null,
    val year: Int,
    val issueTagNumber: String? = null,
    val issueId: Long? = null,
)

class JwPubArchiveReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun extractInnerDb(jwpubFile: Path, destinationDir: Path): Path {
        val (contentsBytes, _) = readOuterEntries(jwpubFile)
        val contents = contentsBytes
            ?: throw IllegalStateException("'contents' entry missing in $jwpubFile")
        ZipInputStream(ByteArrayInputStream(contents)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".db")) {
                    Files.createDirectories(destinationDir)
                    val target = destinationDir.resolve(entry.name)
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING)
                    return target
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("No .db file inside contents of $jwpubFile")
    }

    fun readManifest(jwpubFile: Path): JwPubManifest {
        val (_, manifestBytes) = readOuterEntries(jwpubFile)
        val bytes = manifestBytes
            ?: throw IllegalStateException("manifest.json missing in $jwpubFile")
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    private fun readOuterEntries(jwpubFile: Path): Pair<ByteArray?, ByteArray?> {
        var contents: ByteArray? = null
        var manifest: ByteArray? = null
        ZipInputStream(Files.newInputStream(jwpubFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "contents" -> contents = zis.readAllBytes()
                    "manifest.json" -> manifest = zis.readAllBytes()
                }
                entry = zis.nextEntry
            }
        }
        return contents to manifest
    }
}
