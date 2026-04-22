package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwPubHtmlPartsParserTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var htmlByDocId: Map<Int, String>

    @BeforeTest fun setup() {
        val fixture = Paths.get(
            "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub",
        ).toAbsolutePath()
        val dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
        val pubCard = JwPubSqliteReader().readPubCard(dbFile)
        val keyIv = JwPubContentDecryptor().deriveKeyIv(pubCard)
        val weeks = JwPubSqliteReader().readWeeks(dbFile)
        htmlByDocId = weeks.associate {
            it.documentId to JwPubContentDecryptor().decryptAndInflate(it.content, keyIv)
        }
    }

    @Test fun `parses week 1 parts with expected efficaci section`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(1))
        val efficaci = parts.filter { it.section == JwPubSection.EFFICACI }
        val titles = efficaci.map { it.title }
        assertEquals(
            listOf(
                "3. Lettura biblica",
                "4. Iniziare una conversazione",
                "5. Coltivare l'interesse",
                "6. Discorso",
            ),
            titles,
        )
    }

    @Test fun `spiegare part carries detailLine for week 3`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(3))
        val spiegare = parts.single { it.title.contains("Spiegare") }
        assertTrue(
            spiegare.detailLine?.contains("Dimostrazione") == true,
            "Expected detailLine with 'Dimostrazione', got: ${spiegare.detailLine}",
        )
    }

    @Test fun `tesori section heading parts are classified outside EFFICACI`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(1))
        val tesori = parts.filter { it.section == JwPubSection.TESORI }
        assertTrue(tesori.isNotEmpty(), "Expected at least one TESORI part")
        assertTrue(tesori.all { it.section == JwPubSection.TESORI })
    }
}
