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
}
