package org.example.project.feature.people.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class ImportaProclamatoriDaJsonUseCase(
    private val query: ProclamatoriQuery,
    private val store: ProclamatoriAggregateStore,
) {
    data class Result(
        val importati: Int,
        val errori: Int,
    )

    suspend operator fun invoke(jsonContent: String): Either<DomainError, Result> = either {
        val presenti = query.cerca(termine = null)
        if (presenti.isNotEmpty()) {
            raise(
                DomainError.Validation(
                    "Import disponibile solo con archivio proclamatori vuoto",
                ),
            )
        }

        val proclamatori = parseAndValidate(jsonContent).bind()
        try {
            store.persistAll(proclamatori)
        } catch (_: Exception) {
            raise(DomainError.Validation("Import non completato. Errore durante il salvataggio"))
        }
        Result(importati = proclamatori.size, errori = 0)
    }

    private fun parseAndValidate(jsonContent: String): Either<DomainError, List<Proclamatore>> = either {
        val root = try {
            Json.parseToJsonElement(jsonContent)
        } catch (_: Exception) {
            raise(DomainError.Validation("File JSON non valido"))
        }

        val rootObject = root as? JsonObject
            ?: raise(DomainError.Validation("Radice JSON non valida: atteso oggetto"))

        val versionElement = rootObject["version"]
            ?: raise(DomainError.Validation("Campo obbligatorio mancante: version"))
        val version = versionElement.jsonPrimitive.intOrNull
            ?: versionElement.jsonPrimitive.contentOrNull?.toIntOrNull()
            ?: raise(DomainError.Validation("Campo version non valido"))
        if (version != 1) {
            raise(DomainError.Validation("Versione schema non supportata: $version"))
        }

        val proclamatoriElement = rootObject["proclamatori"]
            ?: raise(DomainError.Validation("Campo obbligatorio mancante: proclamatori"))
        val proclamatoriArray = proclamatoriElement as? JsonArray
            ?: raise(DomainError.Validation("Campo proclamatori non valido: atteso array"))
        if (proclamatoriArray.isEmpty()) {
            raise(DomainError.Validation("Il file non contiene proclamatori da importare"))
        }

        val errors = mutableListOf<String>()
        val uniqueNames = mutableSetOf<String>()
        val results = mutableListOf<Proclamatore>()

        proclamatoriArray.forEachIndexed { index, element ->
            val item = parseItem(index, element, uniqueNames)
            item.fold(
                ifLeft = { errors += it },
                ifRight = { results += it },
            )
        }

        if (errors.isNotEmpty()) {
            val preview = errors.take(5).joinToString(" | ")
            val suffix = if (errors.size > 5) " | ..." else ""
            raise(
                DomainError.Validation(
                    "Import non completato. Errori (${errors.size}): $preview$suffix",
                ),
            )
        }

        results
    }

    private fun parseItem(
        index: Int,
        element: JsonElement,
        uniqueNames: MutableSet<String>,
    ): Either<String, Proclamatore> = either {
        val position = index + 1
        val item = element as? JsonObject
            ?: raise("Elemento #$position non valido: atteso oggetto")

        val nome = extractRequiredText(item, "nome", position).bind()
        val cognome = extractRequiredText(item, "cognome", position).bind()
        val sesso = extractSesso(item, position).bind()
        val attivo = extractAttivo(item, position).bind()

        val duplicateKey = "${nome.lowercase()}|${cognome.lowercase()}"
        if (!uniqueNames.add(duplicateKey)) {
            raise("Elemento #$position duplicato nel file: $nome $cognome")
        }

        Proclamatore(
            id = ProclamatoreId(UUID.randomUUID().toString()),
            nome = nome,
            cognome = cognome,
            sesso = sesso,
            attivo = attivo,
        )
    }

    private fun extractRequiredText(
        item: JsonObject,
        fieldName: String,
        position: Int,
    ): Either<String, String> = either {
        val raw = item[fieldName]?.asStringOrNull()
            ?: raise("Elemento #$position: campo $fieldName mancante o non valido")
        val value = raw.trim()
        if (value.isBlank()) {
            raise("Elemento #$position: campo $fieldName obbligatorio")
        }
        value
    }

    private fun extractSesso(item: JsonObject, position: Int): Either<String, Sesso> = either {
        val raw = item["sesso"]?.asStringOrNull()
            ?: raise("Elemento #$position: campo sesso mancante o non valido")
        val normalized = raw.trim().uppercase()
        when (normalized) {
            "M" -> Sesso.M
            "F" -> Sesso.F
            else -> raise("Elemento #$position: sesso non valido ($raw), valori ammessi: M, F")
        }
    }

    private fun extractAttivo(item: JsonObject, position: Int): Either<String, Boolean> = either {
        val element = item["attivo"] ?: return@either true
        val primitive = element as? JsonPrimitive
            ?: raise("Elemento #$position: campo attivo non valido")

        primitive.booleanOrNull?.let { return@either it }
        val fromText = primitive.contentOrNull?.trim()?.lowercase()
        when (fromText) {
            "true" -> true
            "false" -> false
            else -> raise("Elemento #$position: campo attivo non valido")
        }
    }

    private fun JsonElement.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }
}
