package org.example.project.ui.proclamatori

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.feature.assignments.application.CaricaUltimeAssegnazioniPerParteProclamatoreUseCase
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
import org.example.project.ui.components.executeAsyncOperation
import org.example.project.ui.components.executeEitherOperation
import org.example.project.ui.components.successNotice
import org.example.project.ui.components.warningNotice
import java.time.LocalDate

internal data class LeadEligibilityOptionUi(
    val partTypeId: PartTypeId,
    val label: String,
    val sexRule: SexRule,
    val active: Boolean,
    val checked: Boolean,
    val canSelect: Boolean,
    val lastAssignedOn: LocalDate? = null,
)

private data class LoadedProclamatoreData(
    val proclamatore: Proclamatore,
    val options: List<LeadEligibilityOptionUi>,
    val lastAssistantAssignmentDate: LocalDate?,
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
    val lastAssistantAssignmentDate: LocalDate? = null,
    val showFieldErrors: Boolean = false,
) {
    private fun currentLeadEligibilityByPartType(): Map<PartTypeId, Boolean> {
        return leadEligibilityOptions.associate { option ->
            option.partTypeId to (option.checked && option.canSelect)
        }
    }

    fun hasUnsavedChanges(route: ProclamatoriRoute): Boolean {
        val nomeTrim = nome.trim()
        val cognomeTrim = cognome.trim()
        val hasEligibilityChanges = currentLeadEligibilityByPartType() != initialLeadEligibilityByPartType
        return when (route) {
            ProclamatoriRoute.Nuovo -> {
                nomeTrim.isNotBlank() ||
                    cognomeTrim.isNotBlank() ||
                    sesso != Sesso.M ||
                    sospeso ||
                    puoAssistere ||
                    hasEligibilityChanges
            }
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
    }

    fun canSubmitForm(route: ProclamatoriRoute): Boolean {
        val nomeTrim = nome.trim()
        val cognomeTrim = cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        return route != ProclamatoriRoute.Elenco &&
            requiredFieldsValid &&
            hasUnsavedChanges(route) &&
            duplicateError == null &&
            !isCheckingDuplicate &&
            !isLoading
    }
}

internal class ProclamatoreFormViewModel(
    private val scope: CoroutineScope,
    private val carica: CaricaProclamatoreUseCase,
    private val caricaIdoneita: CaricaIdoneitaProclamatoreUseCase,
    private val caricaUltimeAssegnazioniPerParte: CaricaUltimeAssegnazioniPerParteProclamatoreUseCase,
    private val crea: CreaProclamatoreUseCase,
    private val aggiorna: AggiornaProclamatoreUseCase,
    private val impostaIdoneitaConduzione: ImpostaIdoneitaConduzioneUseCase,
    private val partTypeStore: PartTypeStore,
    private val verificaDuplicato: VerificaDuplicatoProclamatoreUseCase,
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
                    lastAssistantAssignmentDate = null,
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
                        lastAssignmentDatesByPartType = emptyMap(),
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

    fun setAllEligibility(value: Boolean) {
        _uiState.update { state ->
            state.copy(
                puoAssistere = value,
                leadEligibilityOptions = state.leadEligibilityOptions.map { option ->
                    if (option.canSelect) option.copy(checked = value) else option.copy(checked = false)
                },
            )
        }
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
                lastAssistantAssignmentDate = null,
            )
        }
    }

    fun loadForEdit(id: ProclamatoreId, onNotFound: () -> Unit, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.executeEitherOperation(
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
                        lastAssistantAssignmentDate = result.lastAssistantAssignmentDate,
                        formError = null,
                        duplicateError = null,
                        isCheckingDuplicate = false,
                        showFieldErrors = false,
                    )
                },
                errorUpdate = { state, error ->
                    if (error is DomainError.NotFound) {
                        onNotFound()
                    }
                    state.copy(isLoading = false, formError = if (error is DomainError.NotFound) null else error.toMessage())
                },
                operation = {
                    val loaded = carica(id)
                        ?: return@executeEitherOperation Either.Left(DomainError.NotFound("Studente"))
                    val eligibilityByPartType = caricaIdoneita(id)
                        .associate { eligibility ->
                            eligibility.partTypeId to eligibility.canLead
                        }
                    val allPartTypeIds = partTypeStore.allWithStatus()
                        .map { it.partType.id }
                        .toSet()
                    val options = buildLeadEligibilityOptions(
                        sesso = loaded.sesso,
                        selected = eligibilityByPartType,
                        lastAssignmentDatesByPartType = caricaUltimeAssegnazioniPerParte(
                            personId = loaded.id,
                            partTypeIds = allPartTypeIds,
                        ),
                    )
                    val lastAssistantDate = caricaUltimeAssegnazioniPerParte
                        .lastAssistantDate(loaded.id)
                    Either.Right(
                        LoadedProclamatoreData(
                            proclamatore = loaded,
                            options = options,
                            lastAssistantAssignmentDate = lastAssistantDate,
                        ),
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

            _uiState.executeEitherOperation(
                loadingUpdate = { it.copy(isLoading = true) },
                successUpdate = { currentState, futureWeeks: List<LocalDate> ->
                    val operation = if (capturedRoute == ProclamatoriRoute.Nuovo) {
                        "Studente aggiunto"
                    } else {
                        "Studente aggiornato"
                    }
                    val details = personDetails(capturedNome, capturedCognome)
                    clearForm()
                    val banner = if (futureWeeks.isNotEmpty()) {
                        warningNotice("$operation: $details — sospeso con ${futureWeeks.size} assegnazioni future da verificare")
                    } else {
                        successNotice("$operation: $details")
                    }
                    onSuccess(banner)
                    currentState.copy(isLoading = false)
                },
                errorUpdate = { currentState, error ->
                    currentState.copy(
                        isLoading = false,
                        formError = error.toMessage(),
                    )
                },
                operation = {
                    either {
                        val futureWeeks: List<LocalDate>
                        val person: Proclamatore

                        if (capturedRoute == ProclamatoriRoute.Nuovo) {
                            person = crea(
                                CreaProclamatoreUseCase.Command(
                                    nome = capturedNome,
                                    cognome = capturedCognome,
                                    sesso = state.sesso,
                                    sospeso = state.sospeso,
                                    puoAssistere = state.puoAssistere,
                                ),
                            ).bind()
                            futureWeeks = emptyList()
                        } else {
                            val editId = currentEditId
                                ?: raise(DomainError.Validation("ID proclamatore mancante per modifica"))
                            val outcome = aggiorna(
                                AggiornaProclamatoreUseCase.Command(
                                    id = editId,
                                    nome = capturedNome,
                                    cognome = capturedCognome,
                                    sesso = state.sesso,
                                    sospeso = state.sospeso,
                                    puoAssistere = state.puoAssistere,
                                ),
                            ).bind()
                            person = outcome.proclamatore
                            futureWeeks = outcome.futureWeeksWhereAssigned
                        }

                        persistLeadEligibilityAsEither(person, capturedOptions).bind()
                        futureWeeks
                    }
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
                        "Esiste già uno studente con questo nome e cognome"
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
        lastAssignmentDatesByPartType: Map<PartTypeId, LocalDate>,
    ): List<LeadEligibilityOptionUi> {
        val partTypes = partTypeStore.allWithStatus()
        return partTypes.map { typeWithStatus ->
            toLeadEligibilityOption(
                typeWithStatus = typeWithStatus,
                sesso = sesso,
                selected = selected,
                lastAssignmentDate = lastAssignmentDatesByPartType[typeWithStatus.partType.id],
            )
        }
    }

    private fun toLeadEligibilityOption(
        typeWithStatus: PartTypeWithStatus,
        sesso: Sesso,
        selected: Map<PartTypeId, Boolean>,
        lastAssignmentDate: LocalDate?,
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
            lastAssignedOn = lastAssignmentDate,
        )
    }

    private suspend fun persistLeadEligibilityAsEither(
        person: Proclamatore,
        options: List<LeadEligibilityOptionUi>,
    ): Either<DomainError, Unit> {
        val changes = options.map { option ->
            ImpostaIdoneitaConduzioneUseCase.EligibilityChange(
                partTypeId = option.partTypeId,
                canLead = option.checked && option.canSelect,
            )
        }
        return impostaIdoneitaConduzione.batch(person.id, changes)
    }

    private fun canLeadForSex(sesso: Sesso, sexRule: SexRule): Boolean {
        return when (sexRule) {
            SexRule.UOMO -> sesso == Sesso.M
            SexRule.STESSO_SESSO -> true
        }
    }
}
