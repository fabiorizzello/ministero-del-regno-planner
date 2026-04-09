package org.example.project.core.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainErrorToMessageTest {

    @Test
    fun `toMessage maps specific domain errors`() {
        assertEquals("Settimana non trovato", DomainError.NotFound("Settimana").toMessage())
        assertEquals("Il proclamatore e' sospeso", DomainError.PersonaSospesa.toMessage())
        assertEquals("Proclamatore gia' assegnato in questa settimana", DomainError.PersonaGiaAssegnata.toMessage())
        assertEquals("Slot 3 non valido (max: 2)", DomainError.SlotNonValido(slot = 3, max = 2).toMessage())
        assertEquals("La settimana non e' modificabile per questa operazione", DomainError.SettimanaImmutabile.toMessage())
        assertEquals("La parte 'Lettura' non puo' essere rimossa", DomainError.ParteFissa("Lettura").toMessage())
        assertEquals("Il nome e' obbligatorio", DomainError.NomeObbligatorio.toMessage())
        assertEquals("Il cognome non puo' superare 100 caratteri", DomainError.CognomeTroppoLungo(100).toMessage())
        assertEquals("Import disponibile solo con archivio proclamatori vuoto", DomainError.ImportArchivioNonVuoto.toMessage())
        assertEquals("Versione schema non supportata: 2", DomainError.ImportVersioneSchemaNonSupportata(2).toMessage())
        assertEquals("Mese target non valido", DomainError.MeseTargetNonValido.toMessage())
        assertEquals("Il programma per 4/2026 esiste già", DomainError.ProgrammaGiaEsistenteNelMese(4, 2026).toMessage())
        assertEquals("La data della settimana deve essere un lunedi'", DomainError.DataSettimanaNonLunedi.toMessage())
        assertEquals("Errore nel salvataggio della settimana", DomainError.SalvataggioSettimanaFallito.toMessage())
        assertEquals("Errore nella rimozione delle assegnazioni: db", DomainError.RimozioneAssegnazioniFallita("db").toMessage())
        assertEquals(
            "Nessun template e nessuna parte fissa per 2026-03-02",
            DomainError.SettimanaSenzaTemplateENessunaParteFissa(LocalDate.of(2026, 3, 2)).toMessage(),
        )
    }
}
