package org.example.project.ui.proclamatori

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.CaricaStoricoAssegnazioniPersonaUseCase
import org.example.project.feature.assignments.domain.PersonAssignmentHistory
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaIdoneitaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.ImpostaIdoneitaConduzioneUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperation
import org.example.project.ui.components.successNotice

internal data class LeadEligibilityOptionUi(
    val partTypeId: PartTypeId,
    val label: String,
    val sexRule: SexRule,
    val active: Boolean,
    val checked: Boolean,
    val canSelect: Boolean,
)

private data class LoadedProclamatoreData(
    val proclamatore: Proclamatore,
    val options: List<LeadEligibilityOptionUi>,
    val history: PersonAssignmentHistory,
)

private class ProclamatoreNotFoundException : Exception("Proclamatore non trovato")

private class SubmitFormDomainError(val domainError: DomainError) : Exception(domainError.toString())

internal data class ProclamatoreFormUiState(
    val isLoading: Boolean = false,
    val formError: String? = null,
    val duplicateError: String? = null,
    val isCheckingDuplicate: Boolean = false,
    val initialNome: String = "",
    val initialCognome: String = "",
    val initialSesso: Sesso = Sesso.M,
    val initialSospeso: Boolean = false,
    val initialPuoAssistere: Boolean = false,
    val initialLeadEligibilityByPartType: Map<PartTypeId, Boolean> = emptyMap(),
    val nome: String = "",
    val cognome: String = "",
    val sesso: Sesso = Sesso.M,
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
    val leadEligibilityOptions: List<LeadEligibilityOptionUi> = emptyList(),
    val showFieldErrors: Boolean = false,
    val assignmentHistory: PersonAssignmentHistory? = null,
    val isHistoryExpanded: Boolean = false,
) {
    private fun currentLeadEligibilityByPartType(): Map<PartTypeId, Boolean> {
        return leadEligibilityOptions.associate { option ->
            option.partTypeId to (option.checked && option.canSelect)
        }
    }

    fun canSubmitForm(route: ProclamatoriRoute): Boolean {
        val nomeTrim = nome.trim()
        val cognomeTrim = cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        val hasEligibilityChanges = currentLeadEligibilityByPartType() != initialLeadEligibilityByPartType
        val hasFormChanges = when (route) {
            ProclamatoriRoute.Nuovo -> requiredFieldsValid || sesso != Sesso.M
            is ProclamatoriRoute.Modifica -> {
                nomeTrim != initialNome.trim() ||
                    cognomeTrim != initialCognome.trim() ||
                    sesso != initialSesso ||
                    sospeso != initialSospeso ||
                    puoAssistere != initialPuoAssistere ||
                    hasEligibilityChanges
            }
            ProclamatoriRoute.Elenco -> false
        }
        return route != ProclamatoriRoute.Elenco &&
            requiredFieldsValid &&
            hasFormChanges &&
            duplicateError == null &&
            !isCheckingDuplicate &&
            !isLoading
    }
}

internal class ProclamatoreFormViewModel(
    private val scope: CoroutineScope,
    private val carica: CaricaProclamatoreUseCase,
    private val caricaIdoneita: CaricaIdoneitaProclamatoreUseCase,
    private val crea: CreaProclamatoreUseCase,
    private val aggiorna: AggiornaProclamatoreUseCase,
    private val impostaIdoneitaConduzione: ImpostaIdoneitaConduzioneUseCase,
    private val partTypeStore: PartTypeStore,
    private val verificaDuplicato: VerificaDuplicatoProclamatoreUseCase,
    private val caricaStoricoAssegnazioni: CaricaStoricoAssegnazioniPersonaUseCase,
) {
    private val _uiState = MutableStateFlow(ProclamatoreFormUiState())
    val uiState: StateFlow<ProclamatoreFormUiState> = _uiState.asStateFlow()

    private var duplicateCheckJob: Job? = null

    fun prepareForNew() {
        scope.launch {
            _uiState.update {
                it.copy(
                    initialNome = "",
                    initialCognome = "",
                    initialSesso = Sesso.M,
                    initialSospeso = false,
                    initialPuoAssistere = false,
                    initialLeadEligibilityByPartType = emptyMap(),
                    nome = "",
                    cognome = "",
                    sesso = Sesso.M,
                    sospeso = false,
                    puoAssistere = false,
                    formError = null,
                    duplicateError = null,
                    isCheckingDuplicate = false,
                    showFieldErrors = false,
                    leadEligibilityOptions = emptyList(),
                )
            }
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { state, options: List<LeadEligibilityOptionUi> ->
                    state.copy(
                        isLoading = false,
                        leadEligibilityOptions = options,
                        initialLeadEligibilityByPartType = options.associate { option ->
                            option.partTypeId to (option.checked && option.canSelect)
                        },
                    )
                },
                errorUpdate = { state, error ->
                    state.copy(isLoading = false, formError = "Errore: ${error.message}")
                },
                operation = {
                    buildLeadEligibilityOptions(
                        sesso = Sesso.M,
                        selected = emptyMap(),
                    )
                },
            )
        }
    }

    fun setNome(value: String) {
        _uiState.update { it.copy(nome = value) }
    }

    fun setCognome(value: String) {
        _uiState.update { it.copy(cognome = value) }
    }

    fun setSesso(value: Sesso) {
        _uiState.update { state ->
            state.copy(
                sesso = value,
                leadEligibilityOptions = state.leadEligibilityOptions.map { option ->
                    val canSelect = canLeadForSex(value, option.sexRule)
                    option.copy(
                        canSelect = canSelect,
                        checked = if (canSelect) option.checked else false,
                    )
                },
            )
        }
    }

    fun setSospeso(value: Boolean) {
        _uiState.update { it.copy(sospeso = value) }
    }

    fun setPuoAssistere(value: Boolean) {
        _uiState.update { it.copy(puoAssistere = value) }
    }

    fun setLeadEligibility(partTypeId: PartTypeId, value: Boolean) {
        _uiState.update { state ->
            state.copy(
                leadEligibilityOptions = state.leadEligibilityOptions.map { option ->
                    if (option.partTypeId == partTypeId && option.canSelect) {
                        option.copy(checked = value)
                    } else {
                        option
                    }
                },
            )
        }
    }

    fun toggleHistoryExpanded() {
        _uiState.update { it.copy(isHistoryExpanded = !it.isHistoryExpanded) }
    }

    fun clearForm() {
        _uiState.update {
            it.copy(
                isLoading = false,
                initialNome = "",
                initialCognome = "",
                initialSesso = Sesso.M,
                initialSospeso = false,
                initialPuoAssistere = false,
                initialLeadEligibilityByPartType = emptyMap(),
                nome = "",
                cognome = "",
                sesso = Sesso.M,
                sospeso = false,
                puoAssistere = false,
                formError = null,
                duplicateError = null,
                isCheckingDuplicate = false,
                showFieldErrors = false,
                leadEligibilityOptions = emptyList(),
            )
        }
    }

    fun loadForEdit(id: ProclamatoreId, onNotFound: () -> Unit, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { state, result: LoadedProclamatoreData ->
                    onSuccess()
                    state.copy(
                        isLoading = false,
                        initialNome = result.proclamatore.nome,
                        initialCognome = result.proclamatore.cognome,
                        initialSesso = result.proclamatore.sesso,
                        initialSospeso = result.proclamatore.sospeso,
                        initialPuoAssistere = result.proclamatore.puoAssistere,
                        initialLeadEligibilityByPartType = result.options.associate { option ->
                            option.partTypeId to (option.checked && option.canSelect)
                        },
                        nome = result.proclamatore.nome,
                        cognome = result.proclamatore.cognome,
                        sesso = result.proclamatore.sesso,
                        sospeso = result.proclamatore.sospeso,
                        puoAssistere = result.proclamatore.puoAssistere,
                        leadEligibilityOptions = result.options,
                        assignmentHistory = result.history,
                        formError = null,
                        duplicateError = null,
                        isCheckingDuplicate = false,
                        showFieldErrors = false,
                    )
                },
                errorUpdate = { state, error ->
                    if (error is ProclamatoreNotFoundException) {
                        onNotFound()
                    }
                    state.copy(isLoading = false, formError = if (error is ProclamatoreNotFoundException) null else "Errore: ${error.message}")
                },
                operation = {
                    val loaded = carica(id)
                        ?: throw ProclamatoreNotFoundException()
                    val eligibilityByPartType = caricaIdoneita(id)
                        .associate { eligibility ->
                            eligibility.partTypeId to eligibility.canLead
                        }
                    val options = buildLeadEligibilityOptions(
                        sesso = loaded.sesso,
                        selected = eligibilityByPartType,
                    )
                    val history = caricaStoricoAssegnazioni(id)
                    LoadedProclamatoreData(
                        proclamatore = loaded,
                        options = options,
                        history = history,
                    )
                },
            )
        }
    }

    fun submitForm(
        route: ProclamatoriRoute,
        currentEditId: ProclamatoreId?,
        onSuccess: (FeedbackBannerModel) -> Unit,
    ) {
        if (!_uiState.value.canSubmitForm(route)) {
            _uiState.update { it.copy(showFieldErrors = true) }
            return
        }

        scope.launch {
            _uiState.update { it.copy(showFieldErrors = true, formError = null) }
            val state = _uiState.value
            val capturedRoute = route
            val capturedOptions = state.leadEligibilityOptions
            val capturedNome = state.nome
            val capturedCognome = state.cognome

            _uiState.executeAsyncOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { currentState, _: Unit ->
                    val operation = if (capturedRoute == ProclamatoriRoute.Nuovo) {
                        "Proclamatore aggiunto"
                    } else {
                        "Proclamatore aggiornato"
                    }
                    val details = personDetails(capturedNome, capturedCognome)
                    clearForm()
                    onSuccess(successNotice("$operation: $details"))
                    currentState.copy(isLoading = false)
                },
                errorUpdate = { currentState, error ->
                    val errorMessage = when (error) {
                        is SubmitFormDomainError -> (error.domainError as? DomainError.Validation)?.message
                            ?: "Operazione non completata"
                        else -> "Errore: ${error.message}"
                    }
                    currentState.copy(
                        isLoading = false,
                        formError = errorMessage,
                    )
                },
                operation = {
                    val saveResult = if (capturedRoute == ProclamatoriRoute.Nuovo) {
                        crea(
                            CreaProclamatoreUseCase.Command(
                                nome = capturedNome,
                                cognome = capturedCognome,
                                sesso = state.sesso,
                                sospeso = state.sospeso,
                                puoAssistere = state.puoAssistere,
                            ),
                        )
                    } else {
                        aggiorna(
                            AggiornaProclamatoreUseCase.Command(
                                id = requireNotNull(currentEditId),
                                nome = capturedNome,
                                cognome = capturedCognome,
                                sesso = state.sesso,
                                sospeso = state.sospeso,
                                puoAssistere = state.puoAssistere,
                            ),
                        )
                    }

                    val person = saveResult.fold(
                        ifLeft = { err: DomainError -> throw SubmitFormDomainError(err) },
                        ifRight = { it },
                    )

                    persistLeadEligibilityAsEither(person, capturedOptions).fold(
                        ifLeft = { err: DomainError -> throw SubmitFormDomainError(err) },
                        ifRight = { },
                    )
                },
            )
        }
    }

    fun scheduleDuplicateCheck(isFormRoute: Boolean, currentEditId: ProclamatoreId?) {
        duplicateCheckJob?.cancel()
        val state = _uiState.value
        val nomeTrim = state.nome.trim()
        val cognomeTrim = state.cognome.trim()

        if (!isFormRoute || nomeTrim.isBlank() || cognomeTrim.isBlank()) {
            _uiState.update { it.copy(duplicateError = null, isCheckingDuplicate = false) }
            return
        }

        duplicateCheckJob = scope.launch {
            _uiState.update { it.copy(isCheckingDuplicate = true) }
            delay(250)
            val exists = verificaDuplicato(nomeTrim, cognomeTrim, currentEditId)
            _uiState.update {
                it.copy(
                    duplicateError = if (exists) {
                        "Esiste gia' un proclamatore con questo nome e cognome"
                    } else {
                        null
                    },
                    isCheckingDuplicate = false,
                )
            }
        }
    }

    private suspend fun buildLeadEligibilityOptions(
        sesso: Sesso,
        selected: Map<PartTypeId, Boolean>,
    ): List<LeadEligibilityOptionUi> {
        val partTypes = partTypeStore.allWithStatus()
        return partTypes.map { typeWithStatus ->
            toLeadEligibilityOption(
                typeWithStatus = typeWithStatus,
                sesso = sesso,
                selected = selected,
            )
        }
    }

    private fun toLeadEligibilityOption(
        typeWithStatus: PartTypeWithStatus,
        sesso: Sesso,
        selected: Map<PartTypeId, Boolean>,
    ): LeadEligibilityOptionUi {
        val partType = typeWithStatus.partType
        val canSelect = canLeadForSex(sesso, partType.sexRule)
        return LeadEligibilityOptionUi(
            partTypeId = partType.id,
            label = partType.label,
            sexRule = partType.sexRule,
            active = typeWithStatus.active,
            checked = if (canSelect) selected[partType.id] == true else false,
            canSelect = canSelect,
        )
    }

    private suspend fun persistLeadEligibilityAsEither(
        person: Proclamatore,
        options: List<LeadEligibilityOptionUi>,
    ): Either<DomainError, Unit> {
        options.forEach { option ->
            val canLead = option.checked && option.canSelect
            val saveResult = impostaIdoneitaConduzione(
                personId = person.id,
                partTypeId = option.partTypeId,
                canLead = canLead,
            )
            saveResult.fold(
                ifLeft = { error -> return Either.Left(error) },
                ifRight = {},
            )
        }
        return Either.Right(Unit)
    }

    private fun canLeadForSex(sesso: Sesso, sexRule: SexRule): Boolean {
        return when (sexRule) {
            SexRule.UOMO -> sesso == Sesso.M
            SexRule.LIBERO -> true
        }
    }
}
