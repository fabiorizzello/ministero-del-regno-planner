package org.example.project.core.domain

import java.time.LocalDate

sealed interface DomainError {
    data class Validation(val message: String) : DomainError
    data class Network(val message: String) : DomainError
    data class NotFound(val entity: String) : DomainError
    data object PersonaSospesa : DomainError
    data object PersonaGiaAssegnata : DomainError
    data class SlotNonValido(val slot: Int, val max: Int) : DomainError
    data object SettimanaImmutabile : DomainError
    data class ParteFissa(val label: String) : DomainError
    data object OrdinePartiNonValido : DomainError
    data object NomeObbligatorio : DomainError
    data class NomeTroppoLungo(val max: Int) : DomainError
    data object CognomeObbligatorio : DomainError
    data class CognomeTroppoLungo(val max: Int) : DomainError
    data object ProclamatoreDuplicato : DomainError
    data object ImportArchivioNonVuoto : DomainError
    data object ImportJsonNonValido : DomainError
    data class ImportVersioneSchemaNonSupportata(val version: Int) : DomainError
    data object ImportSenzaProclamatori : DomainError
    data object ProgrammaPassatoNonEliminabile : DomainError
    data object MeseTargetNonValido : DomainError
    data object MeseFuoriFinestraCreazione : DomainError
    data class ProgrammaGiaEsistenteNelMese(val month: Int, val year: Int) : DomainError
    data object MeseNonCreabile : DomainError
    data object SettimanaGiaEsistente : DomainError
    data object DataSettimanaNonLunedi : DomainError
    data object CatalogoTipiNonDisponibile : DomainError
    data object SalvataggioSettimanaFallito : DomainError
    data class RiordinoPartiFallito(val reason: String?) : DomainError
    data class RimozioneAssegnazioniFallita(val reason: String?) : DomainError
    data class EliminazioneProclamatoreFallita(val reason: String?) : DomainError
    data class ImportSalvataggioFallito(val reason: String?) : DomainError
    data class ImportContenutoNonValido(val details: String) : DomainError
    data class SettimanaSenzaTemplateENessunaParteFissa(val weekStartDate: LocalDate) : DomainError
    data class CatalogoSchemiIncoerente(val weekStartDate: String) : DomainError
    data class DataSchemaNonValida(val rawValue: String) : DomainError
}

fun DomainError.toMessage(): String = when (this) {
    is DomainError.Validation -> message
    is DomainError.Network -> message
    is DomainError.NotFound -> "$entity non trovato"
    DomainError.PersonaSospesa -> "Il proclamatore e' sospeso"
    DomainError.PersonaGiaAssegnata -> "Proclamatore gia' assegnato in questa settimana"
    is DomainError.SlotNonValido -> "Slot $slot non valido (max: $max)"
    DomainError.SettimanaImmutabile -> "La settimana non e' modificabile per questa operazione"
    is DomainError.ParteFissa -> "La parte '$label' non puo' essere rimossa"
    DomainError.OrdinePartiNonValido -> "Ordine parti non valido"
    DomainError.NomeObbligatorio -> "Il nome e' obbligatorio"
    is DomainError.NomeTroppoLungo -> "Il nome non puo' superare $max caratteri"
    DomainError.CognomeObbligatorio -> "Il cognome e' obbligatorio"
    is DomainError.CognomeTroppoLungo -> "Il cognome non puo' superare $max caratteri"
    DomainError.ProclamatoreDuplicato -> "Esiste gia' un proclamatore con questo nome e cognome"
    DomainError.ImportArchivioNonVuoto -> "Import disponibile solo con archivio proclamatori vuoto"
    DomainError.ImportJsonNonValido -> "File JSON non valido"
    is DomainError.ImportVersioneSchemaNonSupportata -> "Versione schema non supportata: $version"
    DomainError.ImportSenzaProclamatori -> "Il file non contiene proclamatori da importare"
    DomainError.ProgrammaPassatoNonEliminabile -> "Puoi eliminare solo il programma corrente o futuri"
    DomainError.MeseTargetNonValido -> "Mese target non valido"
    DomainError.MeseFuoriFinestraCreazione -> "Puoi creare solo mesi nella finestra corrente..+2"
    is DomainError.ProgrammaGiaEsistenteNelMese -> "Il programma per $month/$year esiste già"
    DomainError.MeseNonCreabile -> "Mese non creabile con le regole correnti (finestra consentita o limite futuri)"
    DomainError.SettimanaGiaEsistente -> "La settimana esiste gia'"
    DomainError.DataSettimanaNonLunedi -> "La data della settimana deve essere un lunedi'"
    DomainError.CatalogoTipiNonDisponibile -> "Catalogo tipi non disponibile. Aggiorna i dati prima."
    DomainError.SalvataggioSettimanaFallito -> "Errore nel salvataggio della settimana"
    is DomainError.RiordinoPartiFallito -> "Errore nel riordinamento: ${reason ?: "sconosciuto"}"
    is DomainError.RimozioneAssegnazioniFallita -> "Errore nella rimozione delle assegnazioni: ${reason ?: "sconosciuto"}"
    is DomainError.EliminazioneProclamatoreFallita -> "Errore nell'eliminazione: ${reason ?: "sconosciuto"}"
    is DomainError.ImportSalvataggioFallito -> "Import non completato. Errore durante il salvataggio: ${reason ?: "sconosciuto"}"
    is DomainError.ImportContenutoNonValido -> details
    is DomainError.SettimanaSenzaTemplateENessunaParteFissa -> "Nessun template e nessuna parte fissa per $weekStartDate"
    is DomainError.CatalogoSchemiIncoerente -> "Schema settimana $weekStartDate contiene partTypeCode non presenti nel catalogo"
    is DomainError.DataSchemaNonValida -> "Data schema non valida: $rawValue"
}
