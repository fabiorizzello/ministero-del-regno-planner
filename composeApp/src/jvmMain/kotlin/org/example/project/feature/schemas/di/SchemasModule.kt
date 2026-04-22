package org.example.project.feature.schemas.di

import org.example.project.core.config.AppPaths
import org.example.project.feature.schemas.application.AggiornaSchemiOperation
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.ArchivaAnomalieSchemaUseCase
import org.example.project.feature.schemas.application.CaricaCatalogoSchemiSettimanaliUseCase
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaTemplateStore
import org.example.project.feature.schemas.infrastructure.SqlDelightSchemaUpdateAnomalyStore
import org.example.project.feature.schemas.infrastructure.jwpub.JwPubSchemaCatalogDataSource
import org.koin.dsl.bind
import org.koin.dsl.module

val schemasModule = module {
    // Local schema templates
    single<SchemaTemplateStore> { SqlDelightSchemaTemplateStore(get()) }
    single<SchemaUpdateAnomalyStore> { SqlDelightSchemaUpdateAnomalyStore(get()) }
    single<SchemaCatalogRemoteSource> {
        JwPubSchemaCatalogDataSource(
            httpClient = get(),
            cacheDir = get<AppPaths>().jwpubCacheDir,
        )
    }
    factory { AggiornaSchemiUseCase(get(), get(), get(), get(), get()) } bind AggiornaSchemiOperation::class
    factory { ArchivaAnomalieSchemaUseCase(get(), get()) }
    factory { CaricaCatalogoSchemiSettimanaliUseCase(get(), get()) }
}
