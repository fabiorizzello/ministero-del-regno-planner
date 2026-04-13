package org.example.project.feature.schemas.application

import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus

data class CatalogoSchemiSettimanali(
    val templates: List<StoredSchemaWeekTemplate>,
    val partTypes: List<PartTypeWithStatus>,
)

class CaricaCatalogoSchemiSettimanaliUseCase(
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(): CatalogoSchemiSettimanali =
        CatalogoSchemiSettimanali(
            templates = schemaTemplateStore.listAll(),
            partTypes = partTypeStore.allWithStatus(),
        )
}
