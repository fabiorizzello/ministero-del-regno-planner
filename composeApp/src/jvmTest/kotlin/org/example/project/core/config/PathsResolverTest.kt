package org.example.project.core.config

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathsResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var originalUserHome: String? = null

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        // Override user.home so PathsResolver.resolveRootDir() creates directories
        // under a TemporaryFolder-managed path instead of the real ~/.ScuolaDiMinisterData.
        // Note: LOCALAPPDATA is an environment variable (not a system property) and the
        // JVM cannot unset env vars. On Linux (including CI) LOCALAPPDATA is typically
        // unset, so the user.home branch is taken. This test is not isolated on Windows
        // hosts where LOCALAPPDATA is set and points inside the user's home.
        System.setProperty("user.home", tempFolder.root.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    @Test
    fun `resolve creates and exposes the jwpub cache directory`() {
        val paths = PathsResolver.resolve()
        assertEquals(
            paths.rootDir.resolve("cache").resolve("GuidaAdunanza").normalize(),
            paths.jwpubCacheDir.normalize(),
        )
        assertTrue(
            Files.isDirectory(paths.jwpubCacheDir),
            "jwpubCacheDir must exist: ${paths.jwpubCacheDir}",
        )
        Unit
    }
}
