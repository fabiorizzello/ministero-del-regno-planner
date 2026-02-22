package org.example.project.ui.proclamatori

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.application.AggiornaProclamatoreUseCase
import org.example.project.feature.people.application.CaricaProclamatoreUseCase
import org.example.project.feature.people.application.CreaProclamatoreUseCase
import org.example.project.feature.people.application.VerificaDuplicatoProclamatoreUseCase
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.ui.components.FeedbackBannerModel
import org.example.project.ui.components.successNotice

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
    val nome: String = "",
    val cognome: String = "",
    val sesso: Sesso = Sesso.M,
    val sospeso: Boolean = false,
    val puoAssistere: Boolean = false,
    val showFieldErrors: Boolean = false,
) {
    fun canSubmitForm(route: ProclamatoriRoute): Boolean {
        val nomeTrim = nome.trim()
        val cognomeTrim = cognome.trim()
        val requiredFieldsValid = nomeTrim.isNotBlank() && cognomeTrim.isNotBlank()
        val hasFormChanges = when (route) {
            ProclamatoriRoute.Nuovo -> requiredFieldsValid || sesso != Sesso.M
            is ProclamatoriRoute.Modifica -> {
                nomeTrim != initialNome.trim() ||
                    cognomeTrim != initialCognome.trim() ||
                    sesso != initialSesso ||
                    sospeso != initialSospeso ||
                    puoAssistere != initialPuoAssistere
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
    private val crea: CreaProclamatoreUseCase,
    private val aggiorna: AggiornaProclamatoreUseCase,
    private val verificaDuplicato: VerificaDuplicatoProclamatoreUseCase,
) {
    private val _uiState = MutableStateFlow(ProclamatoreFormUiState())
    val uiState: StateFlow<ProclamatoreFormUiState> = _uiState.asStateFlow()

    private var duplicateCheckJob: Job? = null

    fun setNome(value: String) {
        _uiState.update { it.copy(nome = value) }
    }

    fun setCognome(value: String) {
        _uiState.update { it.copy(cognome = value) }
    }

    fun setSesso(value: Sesso) {
        _uiState.update { it.copy(sesso = value) }
    }

    fun setSospeso(value: Boolean) {
        _uiState.update { it.copy(sospeso = value) }
    }

    fun setPuoAssistere(value: Boolean) {
        _uiState.update { it.copy(puoAssistere = value) }
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
                nome = "",
                cognome = "",
                sesso = Sesso.M,
                sospeso = false,
                puoAssistere = false,
                formError = null,
                duplicateError = null,
                isCheckingDuplicate = false,
                showFieldErrors = false,
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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    initialNome = loaded.nome,
                    initialCognome = loaded.cognome,
                    initialSesso = loaded.sesso,
                    initialSospeso = loaded.sospeso,
                    initialPuoAssistere = loaded.puoAssistere,
                    nome = loaded.nome,
                    cognome = loaded.cognome,
                    sesso = loaded.sesso,
                    sospeso = loaded.sospeso,
                    puoAssistere = loaded.puoAssistere,
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
                ifRight = {
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
}
