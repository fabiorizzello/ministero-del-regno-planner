package org.example.project.core.config

object RemoteConfig {
    const val SCHEMAS_CATALOG_URL =
        "https://raw.githubusercontent.com/fabiorizzello/efficaci-nel-ministero-data/main/schemas-catalog.json"
    const val UPDATE_REPO = "fabiorizzello/ministero-del-regno-planner"
    const val UPDATE_USE_LOCAL_BUILD_PROPERTY = "ministero.update.useLocalBuild"
    const val UPDATE_USE_LOCAL_BUILD_ENV = "MINISTERO_UPDATE_USE_LOCAL_BUILD"
    const val UPDATE_LOCAL_MSI_PATH_PROPERTY = "ministero.update.localMsiPath"
    const val UPDATE_LOCAL_MSI_PATH_ENV = "MINISTERO_UPDATE_LOCAL_MSI_PATH"
    const val UPDATE_LOCAL_VERSION_PROPERTY = "ministero.update.localVersion"
    const val UPDATE_LOCAL_VERSION_ENV = "MINISTERO_UPDATE_LOCAL_VERSION"
    const val UPDATE_DEV_CHUNK_DELAY_MS_PROPERTY = "ministero.update.devChunkDelayMs"
    const val UPDATE_DEV_CHUNK_DELAY_MS_ENV = "MINISTERO_UPDATE_DEV_CHUNK_DELAY_MS"
    const val UPDATE_DEV_CHUNK_SIZE_BYTES_PROPERTY = "ministero.update.devChunkSizeBytes"
    const val UPDATE_DEV_CHUNK_SIZE_BYTES_ENV = "MINISTERO_UPDATE_DEV_CHUNK_SIZE_BYTES"
    const val UPDATE_DEV_DISABLE_INSTALLER_CACHE_PROPERTY = "ministero.update.devDisableInstallerCache"
    const val UPDATE_DEV_DISABLE_INSTALLER_CACHE_ENV = "MINISTERO_UPDATE_DEV_DISABLE_INSTALLER_CACHE"
}
