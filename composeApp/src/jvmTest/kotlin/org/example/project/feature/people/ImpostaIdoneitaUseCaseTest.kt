package org.example.project.feature.people

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.ImpostaIdoneitaAssistenzaUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaConduzioneUseCase
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImpostaIdoneitaUseCaseTest {

    private val personId = ProclamatoreId("p1")
    private val partTypeId = PartTypeId("pt-1")

    @Test
    fun `ImpostaIdoneitaConduzioneUseCase chiama setCanLead con i valori corretti`() = runBlocking {
        val store = RecordingEligibilityStore()
        val useCase = ImpostaIdoneitaConduzioneUseCase(store, ImmediateTransactionRunner)

        val result = useCase(personId, partTypeId, canLead = true)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(Triple(personId, partTypeId, true), store.lastCanLead)
    }

    @Test
    fun `ImpostaIdoneitaConduzioneUseCase imposta canLead false`() = runBlocking {
        val store = RecordingEligibilityStore()
        val useCase = ImpostaIdoneitaConduzioneUseCase(store, ImmediateTransactionRunner)

        val result = useCase(personId, partTypeId, canLead = false)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(Triple(personId, partTypeId, false), store.lastCanLead)
    }

    @Test
    fun `ImpostaIdoneitaAssistenzaUseCase chiama setCanAssist con i valori corretti`() = runBlocking {
        val store = RecordingEligibilityStore()
        val useCase = ImpostaIdoneitaAssistenzaUseCase(store, ImmediateTransactionRunner)

        val result = useCase(personId, canAssist = true)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(Pair(personId, true), store.lastCanAssist)
    }

    @Test
    fun `ImpostaIdoneitaAssistenzaUseCase imposta canAssist false`() = runBlocking {
        val store = RecordingEligibilityStore()
        val useCase = ImpostaIdoneitaAssistenzaUseCase(store, ImmediateTransactionRunner)

        val result = useCase(personId, canAssist = false)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(Pair(personId, false), store.lastCanAssist)
    }
}

// ---- fakes ----

private class RecordingEligibilityStore : EligibilityStore {
    var lastCanLead: Triple<ProclamatoreId, PartTypeId, Boolean>? = null
    var lastCanAssist: Pair<ProclamatoreId, Boolean>? = null

    override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {}
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {
        lastCanAssist = Pair(personId, canAssist)
    }
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {
        lastCanLead = Triple(personId, partTypeId, canLead)
    }
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = emptyList()
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = emptyMap()
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}
