package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreAggregate
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import java.time.LocalDate

class AggiornaProclamatoreUseCase(
    private val query: ProclamatoriQuery,
    private val store: ProclamatoriAggregateStore,
    private val eligibilityStore: EligibilityStore,
    private val transactionRunner: TransactionRunner,
) {
    data class Command(
        val id: ProclamatoreId,
        val nome: String,
        val cognome: String,
        val sesso: Sesso,
        val sospeso: Boolean = false,
        val puoAssistere: Boolean = false,
    )

    data class AggiornamentoOutcome(
        val proclamatore: Proclamatore,
        val futureWeeksWhereAssigned: List<LocalDate>,
    )

    suspend operator fun invoke(command: Command): Either<DomainError, AggiornamentoOutcome> = either {
        val nome = command.nome.trim()
        val cognome = command.cognome.trim()

        val corrente = store.load(command.id)
            ?: raise(DomainError.NotFound("Proclamatore"))

        val duplicato = query.esisteConNomeCognome(nome, cognome, esclusoId = command.id)
        if (duplicato) raise(DomainError.ProclamatoreDuplicato)

        val aggiornato = ProclamatoreAggregate(corrente).updateProfile(
            nome = nome,
            cognome = cognome,
            sesso = command.sesso,
            sospeso = command.sospeso,
            puoAssistere = command.puoAssistere,
        ).bind().person
        Either.catch {
            transactionRunner.runInTransaction { store.persist(aggiornato) }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore aggiornamento proclamatore") }.bind()

        val futureWeeks = if (!corrente.sospeso && command.sospeso) {
            eligibilityStore.listFutureAssignmentWeeks(command.id, LocalDate.now())
        } else {
            emptyList()
        }

        AggiornamentoOutcome(aggiornato, futureWeeks)
    }
}
