package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwPubContentDecryptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `deriveKeyIv produces pinned vectors for 4_mwb26_2026_20260100`() {
        val pubCard = PubCard(
            mepsLanguageIndex = 4,
            symbol = "mwb26",
            year = 2026,
            issueTag = "20260100",
        )
        val keyIv = JwPubContentDecryptor().deriveKeyIv(pubCard)
        assertEquals("ae2d85ab379f450f6fcfec6480a85f41", keyIv.key.toHex())
        assertEquals("f6dce80a87be02f5e24183a2378b5e3b", keyIv.iv.toHex())
    }

    @Test
    fun `pubCard string format is lang_symbol_year_issueTag`() {
        val pubCard = PubCard(4, "mwb26", 2026, "20260100")
        assertEquals("4_mwb26_2026_20260100", pubCard.toPubCardString())
    }

    @Test
    fun `decryptAndInflate produces HTML for first week from fixture`() {
        val fixture = java.nio.file.Paths.get(
            "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub"
        ).toAbsolutePath()
        val dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
        val pubCard = JwPubSqliteReader().readPubCard(dbFile)
        val weeks = JwPubSqliteReader().readWeeks(dbFile)
        val decryptor = JwPubContentDecryptor()
        val keyIv = decryptor.deriveKeyIv(pubCard)

        val html = decryptor.decryptAndInflate(weeks.first().content, keyIv)

        assertTrue(html.contains("5-11 GENNAIO"), "Expected week title in html")
        assertTrue(html.contains("ISAIA 17-20"), "Expected scripture ref in html")
        assertTrue(html.contains("Lettura biblica"), "Expected a known part label")
        Unit
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
