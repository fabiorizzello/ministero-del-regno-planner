package org.example.project.core.config

import java.io.IOException

object AppRelauncher {
    fun relaunch(): Result<Nothing> = runCatching {
        val processInfo = ProcessHandle.current().info()
        val command = processInfo.command().orElseThrow {
            IOException("Comando processo corrente non disponibile")
        }
        val arguments = processInfo.arguments().orElse(emptyArray())
        ProcessBuilder(listOf(command) + arguments)
            .directory(java.io.File(System.getProperty("user.dir") ?: "."))
            .start()
        SingleInstanceGuard.release()
        kotlin.system.exitProcess(0)
    }
}
