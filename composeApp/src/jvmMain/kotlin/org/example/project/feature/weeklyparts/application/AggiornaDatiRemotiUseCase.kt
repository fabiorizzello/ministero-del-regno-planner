package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

data class ImportResult(
    val partTypesImported: Int,
    val weeksImported: Int,
    val weeksNeedingConfirmation: List<RemoteWeekSchema>,
    val unresolvedPartTypeCodes: Set<String> = emptySet(),
)

class AggiornaDatiRemotiUseCase(
    private val remoteDataSource: RemoteDataSource,
    private val partTypeStore: PartTypeStore,
    private val weekPlanStore: WeekPlanStore,
) {
    suspend fun fetchAndImport(): Either<DomainError, ImportResult> = either {
        val remoteTypes = try {
            remoteDataSource.fetchPartTypes()
        } catch (e: Exception) {
            raise(DomainError.Network("Errore nel download del catalogo: ${e.message}"))
        }
        partTypeStore.upsertAll(remoteTypes)

        val remoteSchemas = try {
            remoteDataSource.fetchWeeklySchemas()
        } catch (e: Exception) {
            raise(DomainError.Network("Errore nel download degli schemi: ${e.message}"))
        }

        var imported = 0
        val needConfirmation = mutableListOf<RemoteWeekSchema>()
        val allUnresolvedCodes = mutableSetOf<String>()

        for (schema in remoteSchemas) {
            val date = try {
                LocalDate.parse(schema.weekStartDate)
            } catch (_: DateTimeParseException) {
                raise(DomainError.Validation("Data non valida nello schema remoto: '${schema.weekStartDate}'"))
            }
            val existing = weekPlanStore.findByDate(date)

            if (existing != null) {
                needConfirmation.add(schema)
                continue
            }

            allUnresolvedCodes += importSchema(schema)
            imported++
        }

        ImportResult(
            partTypesImported = remoteTypes.size,
            weeksImported = imported,
            weeksNeedingConfirmation = needConfirmation,
            unresolvedPartTypeCodes = allUnresolvedCodes,
        )
    }

    suspend fun importSchemas(schemas: List<RemoteWeekSchema>): Either<DomainError, Int> = either {
        var count = 0
        for (schema in schemas) {
            val date = try {
                LocalDate.parse(schema.weekStartDate)
            } catch (_: DateTimeParseException) {
                raise(DomainError.Validation("Data non valida nello schema remoto: '${schema.weekStartDate}'"))
            }
            val existing = weekPlanStore.findByDate(date)
            if (existing != null) {
                weekPlanStore.delete(existing.id)
            }
            importSchema(schema)
            count++
        }
        count
    }

    /**
     * Returns the list of part-type codes that could not be resolved to a known [PartType].
     */
    private suspend fun importSchema(schema: RemoteWeekSchema): List<String> {
        val date = LocalDate.parse(schema.weekStartDate)
        val newPlanId = WeekPlanId(UUID.randomUUID().toString())
        weekPlanStore.save(
            WeekPlan(
                id = newPlanId,
                weekStartDate = date,
                parts = emptyList(),
            ),
        )
        val unresolvedCodes = mutableListOf<String>()
        val resolvedIds = schema.partTypeCodes.mapNotNull { code ->
            partTypeStore.findByCode(code)?.id ?: run {
                unresolvedCodes += code
                null
            }
        }
        weekPlanStore.replaceAllParts(newPlanId, resolvedIds)
        return unresolvedCodes
    }
}
