package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import java.util.UUID

@Serializable
private data class ImportDto(
    val version: Int = 0,
    val proclamatori: List<ProclamatoreDto> = emptyList(),
)

@Serializable
private data class ProclamatoreDto(
    val nome: String = "",
    val cognome: String = "",
    val sesso: String = "",
)

class ImportaProclamatoriDaJsonUseCase(
    private val query: ProclamatoriQuery,
    private val store: ProclamatoriAggregateStore,
    private val transactionRunner: TransactionRunner,
) {
    data class Result(
        val importati: Int,
        val errori: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(jsonContent: String): Either<DomainError, Result> = either {
        val presenti = query.cerca(termine = null)
        if (presenti.isNotEmpty()) {
            raise(DomainError.ImportArchivioNonVuoto)
        }

        val proclamatori = parseAndValidate(jsonContent).bind()
        Either.catch {
            transactionRunner.runInTransaction {
                store.persistAll(proclamatori)
            }
        }.mapLeft { DomainError.ImportSalvataggioFallito(it.message) }.bind()
        Result(importati = proclamatori.size, errori = 0)
    }

    private fun parseAndValidate(jsonContent: String): Either<DomainError, List<Proclamatore>> = either {
        val dto = try {
            json.decodeFromString<ImportDto>(jsonContent)
        } catch (_: Exception) {
            raise(DomainError.ImportJsonNonValido)
        }

        if (dto.version != 1) {
            raise(DomainError.ImportVersioneSchemaNonSupportata(dto.version))
        }
        if (dto.proclamatori.isEmpty()) {
            raise(DomainError.ImportSenzaProclamatori)
        }

        val errors = mutableListOf<String>()
        val uniqueNames = mutableSetOf<String>()
        val results = mutableListOf<Proclamatore>()

        dto.proclamatori.forEachIndexed { index, item ->
            val position = index + 1
            validateItem(position, item, uniqueNames).fold(
                ifLeft = { errors += it },
                ifRight = { results += it },
            )
        }

        if (errors.isNotEmpty()) {
            val preview = errors.take(5).joinToString(" | ")
            val suffix = if (errors.size > 5) " | ..." else ""
            raise(DomainError.ImportContenutoNonValido("Import non completato. Errori (${errors.size}): $preview$suffix"))
        }

        results
    }

    private fun validateItem(
        position: Int,
        item: ProclamatoreDto,
        uniqueNames: MutableSet<String>,
    ): Either<String, Proclamatore> = either {
        val nome = item.nome.trim()
            .takeIf { it.isNotBlank() }
            ?: raise("Elemento #$position: campo nome obbligatorio")

        val cognome = item.cognome.trim()
            .takeIf { it.isNotBlank() }
            ?: raise("Elemento #$position: campo cognome obbligatorio")

        val sesso = when (item.sesso.trim().uppercase()) {
            "M" -> Sesso.M
            "F" -> Sesso.F
            else -> raise("Elemento #$position: sesso non valido (${item.sesso}), valori ammessi: M, F")
        }

        val duplicateKey = "${nome.lowercase()}|${cognome.lowercase()}"
        if (!uniqueNames.add(duplicateKey)) {
            raise("Elemento #$position duplicato nel file: $nome $cognome")
        }

        Proclamatore.of(
            id = ProclamatoreId(UUID.randomUUID().toString()),
            nome = nome,
            cognome = cognome,
            sesso = sesso,
        ).fold(
            ifLeft = { err -> raise("Elemento #$position non valido: ${(err as? org.example.project.core.domain.DomainError.Validation)?.message ?: err.toString()}") },
            ifRight = { it },
        )
    }
}
