package org.example.project.feature.schemas.infrastructure.jwpub

import kotlin.test.Test
import kotlin.test.assertEquals

class JwPubContentDecryptorTest {

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

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
