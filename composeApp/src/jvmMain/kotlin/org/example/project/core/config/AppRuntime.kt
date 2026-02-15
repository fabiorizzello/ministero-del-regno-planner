package org.example.project.core.config

object AppRuntime {
    @Volatile
    private var runtimePaths: AppPaths? = null

    fun initialize(paths: AppPaths) {
        runtimePaths = paths
    }

    fun pathsOrNull(): AppPaths? = runtimePaths

    fun paths(): AppPaths {
        return requireNotNull(runtimePaths) {
            "AppRuntime non inizializzato. Eseguire AppBootstrap.initialize() prima dell'uso."
        }
    }
}
