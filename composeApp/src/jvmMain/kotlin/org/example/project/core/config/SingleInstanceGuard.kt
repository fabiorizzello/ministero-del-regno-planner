package org.example.project.core.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

object SingleInstanceGuard {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var channel: FileChannel? = null

    @Volatile
    private var lock: FileLock? = null

    fun acquire(lockFile: Path = defaultLockFile()): Boolean = synchronized(this) {
        if (lock?.isValid == true) return true

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
        } catch (error: Exception) {
            logger.warn(error) { "Impossibile acquisire il lock di single-instance: $lockFile" }
            false
        }
    }

    fun release() = synchronized(this) {
        runCatching { lock?.close() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    private fun defaultLockFile(): Path =
        PathsResolver.resolve().rootDir.resolve("app.lock")
}
