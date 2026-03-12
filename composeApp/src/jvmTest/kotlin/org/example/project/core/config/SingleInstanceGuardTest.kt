package org.example.project.core.config

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceGuardTest {

    @AfterTest
    fun tearDown() {
        SingleInstanceGuard.release()
    }

    @Test
    fun `guard can reacquire same lock after release`() {
        val lockFile = Files.createTempDirectory("single-instance-guard-test").resolve("app.lock")

        assertTrue(SingleInstanceGuard.acquire(lockFile))
        SingleInstanceGuard.release()
        assertTrue(SingleInstanceGuard.acquire(lockFile))
    }

    @Test
    fun `independent lock attempt on same file is rejected while guard is active`() {
        val lockFile = Files.createTempDirectory("single-instance-guard-overlap").resolve("app.lock")

        assertTrue(SingleInstanceGuard.acquire(lockFile))
        assertFalse(CompetingLockAttempt.tryAcquire(lockFile))
        CompetingLockAttempt.release()
    }

    private object CompetingLockAttempt {
        private var channel: FileChannel? = null
        private var lock: FileLock? = null

        fun tryAcquire(lockFile: Path): Boolean {
            return try {
                val openedChannel = FileChannel.open(lockFile, CREATE, WRITE)
                val acquiredLock = openedChannel.tryLock()
                if (acquiredLock == null) {
                    openedChannel.close()
                    false
                } else {
                    channel = openedChannel
                    lock = acquiredLock
                    true
                }
            } catch (_: OverlappingFileLockException) {
                false
            }
        }

        fun release() {
            runCatching { lock?.close() }
            runCatching { channel?.close() }
            lock = null
            channel = null
        }
    }
}
