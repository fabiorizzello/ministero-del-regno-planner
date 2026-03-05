package org.example.project.feature.people.domain

data class ProclamatoreAggregate(
    val person: Proclamatore,
) {
    companion object {
        fun create(
            id: ProclamatoreId,
            nome: String,
            cognome: String,
            sesso: Sesso,
            sospeso: Boolean = false,
            puoAssistere: Boolean = false,
        ): ProclamatoreAggregate = ProclamatoreAggregate(
            person = Proclamatore(
                id = id,
                nome = nome,
                cognome = cognome,
                sesso = sesso,
                sospeso = sospeso,
                puoAssistere = puoAssistere,
            ),
        )
    }

    fun updateProfile(
        nome: String,
        cognome: String,
        sesso: Sesso,
        sospeso: Boolean,
        puoAssistere: Boolean,
    ): ProclamatoreAggregate {
        return copy(
            person = person.copy(
                nome = nome,
                cognome = cognome,
                sesso = sesso,
                sospeso = sospeso,
                puoAssistere = puoAssistere,
            ),
        )
    }

    fun suspendPerson(): ProclamatoreAggregate =
        if (person.sospeso) this else copy(person = person.copy(sospeso = true))

    fun reactivate(): ProclamatoreAggregate =
        if (!person.sospeso) this else copy(person = person.copy(sospeso = false))
}
