package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwPubSqliteReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dbFile: java.nio.file.Path

    @BeforeTest fun setup() {
        val fixture = Paths.get(
            "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub"
        ).toAbsolutePath()
        dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
    }

    @Test fun `readPubCard returns expected meta`() {
        val pubCard = JwPubSqliteReader().readPubCard(dbFile)
        assertEquals(PubCard(4, "mwb26", 2026, "20260100"), pubCard)
    }

    @Test fun `readWeeks returns 8 rows for Class 106`() {
        val weeks = JwPubSqliteReader().readWeeks(dbFile)
        assertEquals(8, weeks.size)
        val first = weeks.first()
        assertEquals(202026001L, first.mepsDocumentId)
        assertEquals("5-11 gennaio", first.title)
        assertEquals("ISAIA 17-20", first.subtitle)
        assertTrue(first.content.isNotEmpty(), "Encrypted content must be present")
    }
}
