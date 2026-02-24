package org.example.project.ui.proclamatori

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
import org.example.project.ui.components.successNotice

internal data class LeadEligibilityOptionUi(
    val partTypeId: PartTypeId,
    val label: String,
    val sexRule: SexRule,
    val active: Boolean,
    val checked: Boolean,
    val canSelect: Boolean,
)

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
                    isLoading = true,
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
            val options = buildLeadEligibilityOptions(
                sesso = Sesso.M,
                selected = emptyMap(),
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    leadEligibilityOptions = options,
                    initialLeadEligibilityByPartType = options.associate { option ->
                        option.partTypeId to (option.checked && option.canSelect)
                    },
                )
            }
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
            _uiState.update { it.copy(isLoading = true) }
            val loaded = carica(id)
            if (loaded == null) {
                _uiState.update { it.copy(isLoading = false) }
                onNotFound()
                return@launch
            }
            val eligibilityByPartType = caricaIdoneita(id)
                .associate { eligibility ->
                    eligibility.partTypeId to eligibility.canLead
                }
            val options = buildLeadEligibilityOptions(
                sesso = loaded.sesso,
                selected = eligibilityByPartType,
            )
            val history = caricaStoricoAssegnazioni(id)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    initialNome = loaded.nome,
                    initialCognome = loaded.cognome,
                    initialSesso = loaded.sesso,
                    initialSospeso = loaded.sospeso,
                    initialPuoAssistere = loaded.puoAssistere,
                    initialLeadEligibilityByPartType = options.associate { option ->
                        option.partTypeId to (option.checked && option.canSelect)
                    },
                    nome = loaded.nome,
                    cognome = loaded.cognome,
                    sesso = loaded.sesso,
                    sospeso = loaded.sospeso,
                    puoAssistere = loaded.puoAssistere,
                    leadEligibilityOptions = options,
                    assignmentHistory = history,
                    formError = null,
                    duplicateError = null,
                    isCheckingDuplicate = false,
                    showFieldErrors = false,
                )
            }
            onSuccess()
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
            _uiState.update { it.copy(showFieldErrors = true, formError = null, isLoading = true) }
            val state = _uiState.value
            val result = if (route == ProclamatoriRoute.Nuovo) {
                crea(
                    CreaProclamatoreUseCase.Command(
                        nome = state.nome,
                        cognome = state.cognome,
                        sesso = state.sesso,
                        sospeso = state.sospeso,
                        puoAssistere = state.puoAssistere,
                    ),
                )
            } else {
                aggiorna(
                    AggiornaProclamatoreUseCase.Command(
                        id = requireNotNull(currentEditId),
                        nome = state.nome,
                        cognome = state.cognome,
                        sesso = state.sesso,
                        sospeso = state.sospeso,
                        puoAssistere = state.puoAssistere,
                    ),
                )
            }

            result.fold(
                ifLeft = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            formError = (err as? DomainError.Validation)?.message ?: "Operazione non completata",
                        )
                    }
                },
                ifRight = { person ->
                    val eligibilityError = persistLeadEligibility(person, state.leadEligibilityOptions)
                    if (eligibilityError != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                formError = eligibilityError,
                            )
                        }
                        return@fold
                    }

                    val operation = if (route == ProclamatoriRoute.Nuovo) {
                        "Proclamatore aggiunto"
                    } else {
                        "Proclamatore aggiornato"
                    }
                    val details = personDetails(state.nome, state.cognome)
                    clearForm()
                    onSuccess(successNotice("$operation: $details"))
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

    private suspend fun persistLeadEligibility(
        person: Proclamatore,
        options: List<LeadEligibilityOptionUi>,
    ): String? {
        options.forEach { option ->
            val canLead = option.checked && option.canSelect
            val saveResult = impostaIdoneitaConduzione(
                personId = person.id,
                partTypeId = option.partTypeId,
                canLead = canLead,
            )
            var maybeError: String? = null
            saveResult.fold(
                ifLeft = { error ->
                    maybeError = (error as? DomainError.Validation)?.message
                        ?: "Salvataggio idoneita non completato"
                },
                ifRight = {},
            )
            if (maybeError != null) return maybeError
        }
        return null
    }

    private fun canLeadForSex(sesso: Sesso, sexRule: SexRule): Boolean {
        return when (sexRule) {
            SexRule.UOMO -> sesso == Sesso.M
            SexRule.LIBERO -> true
        }
    }
}
