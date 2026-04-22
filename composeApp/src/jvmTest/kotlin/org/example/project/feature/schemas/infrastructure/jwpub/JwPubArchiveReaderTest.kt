package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class JwPubArchiveReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fixture(): Path = Paths.get(
        "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub"
    ).toAbsolutePath()

    @Test fun `extracts inner db from real jwpub`() {
        val reader = JwPubArchiveReader()
        val dbFile = reader.extractInnerDb(fixture(), tempFolder.root.toPath())
        assertTrue(Files.exists(dbFile))
        assertTrue(dbFile.fileName.toString().endsWith(".db"))
        assertTrue(Files.size(dbFile) > 100_000L, "DB should be reasonably sized")
    }

    @Test fun `reads manifest from real jwpub`() {
        val reader = JwPubArchiveReader()
        val manifest = reader.readManifest(fixture())
        assertTrue(manifest.publication.symbol == "mwb26")
    }
}
