package org.example.project.core.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathsResolverTest {
    @Test
    fun `resolve creates and exposes the jwpub cache directory`() {
        val paths = PathsResolver.resolve()
        assertTrue(
            Files.isDirectory(paths.jwpubCacheDir),
            "jwpubCacheDir must exist: ${paths.jwpubCacheDir}",
        )
        assertEquals(
            paths.rootDir.resolve("cache").resolve("GuidaAdunanza").normalize(),
            paths.jwpubCacheDir.normalize(),
        )
        Unit
    }
}
