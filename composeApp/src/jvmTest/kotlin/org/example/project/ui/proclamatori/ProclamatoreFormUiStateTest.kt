package org.example.project.ui.proclamatori

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProclamatoreFormUiStateTest {

    @Test
    fun `canSubmitForm Nuovo false quando nome e' blank`() {
        val state = ProclamatoreFormUiState(nome = "", cognome = "Rossi")
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Nuovo false quando cognome e' blank`() {
        val state = ProclamatoreFormUiState(nome = "Mario", cognome = "  ")
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Nuovo true con nome e cognome validi`() {
        val state = ProclamatoreFormUiState(nome = "Mario", cognome = "Rossi")
        assertTrue(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Nuovo false durante controllo duplicato in corso`() {
        val state = ProclamatoreFormUiState(
            nome = "Mario",
            cognome = "Rossi",
            isCheckingDuplicate = true,
        )
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Nuovo false quando c'e' errore duplicato`() {
        val state = ProclamatoreFormUiState(
            nome = "Mario",
            cognome = "Rossi",
            duplicateError = "Esiste già uno studente con questo nome e cognome",
        )
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Nuovo false durante caricamento`() {
        val state = ProclamatoreFormUiState(nome = "Mario", cognome = "Rossi", isLoading = true)
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Nuovo))
    }

    @Test
    fun `canSubmitForm Modifica false se nessun campo cambiato`() {
        val state = ProclamatoreFormUiState(
            initialNome = "Mario",
            initialCognome = "Rossi",
            initialSesso = Sesso.M,
            nome = "Mario",
            cognome = "Rossi",
            sesso = Sesso.M,
        )
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Modifica(ProclamatoreId("p1"))))
    }

    @Test
    fun `canSubmitForm Modifica true quando nome cambia`() {
        val state = ProclamatoreFormUiState(
            initialNome = "Mario",
            initialCognome = "Rossi",
            nome = "Luigi",
            cognome = "Rossi",
        )
        assertTrue(state.canSubmitForm(ProclamatoriRoute.Modifica(ProclamatoreId("p1"))))
    }

    @Test
    fun `canSubmitForm Modifica true quando idoneita' cambia`() {
        val partTypeId = PartTypeId("pt-1")
        val state = ProclamatoreFormUiState(
            initialNome = "Mario",
            initialCognome = "Rossi",
            nome = "Mario",
            cognome = "Rossi",
            initialLeadEligibilityByPartType = mapOf(partTypeId to false),
            leadEligibilityOptions = listOf(
                LeadEligibilityOptionUi(
                    partTypeId = partTypeId,
                    label = "Parte 1",
                    sexRule = SexRule.STESSO_SESSO,
                    active = true,
                    checked = true,
                    canSelect = true,
                ),
            ),
        )
        assertTrue(state.canSubmitForm(ProclamatoriRoute.Modifica(ProclamatoreId("p1"))))
    }

    @Test
    fun `canSubmitForm Elenco sempre false`() {
        val state = ProclamatoreFormUiState(nome = "Mario", cognome = "Rossi")
        assertFalse(state.canSubmitForm(ProclamatoriRoute.Elenco))
    }
}
