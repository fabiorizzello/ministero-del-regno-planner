package org.example.project.feature.schemas.di

import org.example.project.core.config.RemoteConfig
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.schemas.infrastructure.GitHubSchemaCatalogDataSource
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaTemplateStore
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaUpdateAnomalyStore
import org.koin.dsl.module

val schemasModule = module {
    // Local schema templates
    single<SchemaTemplateStore> { SqlDelightSchemaTemplateStore(get()) }
    single<SchemaUpdateAnomalyStore> { SqlDelightSchemaUpdateAnomalyStore(get()) }
    single<SchemaCatalogRemoteSource> {
        GitHubSchemaCatalogDataSource(
            schemasCatalogUrl = RemoteConfig.SCHEMAS_CATALOG_URL,
        )
    }
    single { AggiornaSchemiUseCase(get(), get(), get(), get(), get(), get(), get()) }
}
