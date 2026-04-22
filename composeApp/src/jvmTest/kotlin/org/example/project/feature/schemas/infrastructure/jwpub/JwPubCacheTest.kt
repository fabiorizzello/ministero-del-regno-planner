package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwPubCacheTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val info = JwPubMediaInfo(
        url = "https://cfp2.jw-cdn.org/sample.jwpub",
        checksum = "8b35423df852a905ac2feea6d758ac7b",
        modifiedDatetime = "2025-07-09 10:20:40",
        filesize = 3_569_429,
    )

    @Test fun `find returns null when cache empty`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        assertNull(cache.find("202601", "I"))
    }

    @Test fun `store writes jwpub and metadata sidecar, find returns both`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "fake".toByteArray(), info)
        assertTrue(java.nio.file.Files.exists(cached.file))

        val found = assertNotNull(cache.find("202601", "I"))
        assertEquals(info, found.meta)
    }

    @Test fun `isUpToDate true when checksum matches`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "x".toByteArray(), info)
        assertTrue(cache.isUpToDate(cached, info))
    }

    @Test fun `isUpToDate false when checksum differs`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "x".toByteArray(), info)
        val changed = info.copy(checksum = "other")
        assertFalse(cache.isUpToDate(cached, changed))
    }

    @Test fun `find tolerates missing metadata sidecar by returning null`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        cache.store("202601", "I", "x".toByteArray(), info)
        java.nio.file.Files.delete(tempFolder.root.toPath().resolve("mwb_I_202601.meta.json"))
        assertNull(cache.find("202601", "I"))
    }
}
