package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.infrastructure.GitHubDataSource
import org.example.project.feature.weeklyparts.infrastructure.RemoteWeekSchema
import java.time.LocalDate
import java.util.UUID

data class ImportResult(
    val partTypesImported: Int,
    val weeksImported: Int,
    val weeksNeedingConfirmation: List<RemoteWeekSchema>,
)

class AggiornaDatiRemotiUseCase(
    private val gitHubDataSource: GitHubDataSource,
    private val partTypeStore: PartTypeStore,
    private val weekPlanStore: WeekPlanStore,
) {
    suspend fun fetchAndImport(
        overwriteDates: Set<LocalDate> = emptySet(),
    ): Either<DomainError, ImportResult> = either {
        val remoteTypes = try {
            gitHubDataSource.fetchPartTypes()
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel download del catalogo: ${e.message}"))
        }
        partTypeStore.upsertAll(remoteTypes)

        val remoteSchemas = try {
            gitHubDataSource.fetchWeeklySchemas()
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel download degli schemi: ${e.message}"))
        }

        var imported = 0
        val needConfirmation = mutableListOf<RemoteWeekSchema>()

        for (schema in remoteSchemas) {
            val date = LocalDate.parse(schema.weekStartDate)
            val existing = weekPlanStore.findByDate(date)

            if (existing != null && date !in overwriteDates) {
                needConfirmation.add(schema)
                continue
            }

            if (existing != null) {
                weekPlanStore.delete(existing.id)
            }

            val newPlanId = WeekPlanId(UUID.randomUUID().toString())
            weekPlanStore.save(
                WeekPlan(
                    id = newPlanId,
                    weekStartDate = date,
                    parts = emptyList(),
                ),
            )
            weekPlanStore.replaceAllParts(newPlanId, schema.partTypeCodes, partTypeStore)
            imported++
        }

        ImportResult(
            partTypesImported = remoteTypes.size,
            weeksImported = imported,
            weeksNeedingConfirmation = needConfirmation,
        )
    }
}
