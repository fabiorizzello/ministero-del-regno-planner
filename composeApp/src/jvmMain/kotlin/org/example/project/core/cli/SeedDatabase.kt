package org.example.project.core.cli

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import org.example.project.feature.weeklyparts.infrastructure.parsePartTypeFromJson
import java.io.File

fun main() {
    AppBootstrap.initialize()
    val db = DatabaseProvider.database()
    val store = SqlDelightPartTypeStore(db)

    val jsonFile = File("data/part-types.json")
    require(jsonFile.exists()) { "File non trovato: ${jsonFile.absolutePath}" }

    val root = Json.parseToJsonElement(jsonFile.readText()).jsonObject
    val arr = root["partTypes"]?.jsonArray ?: error("Campo 'partTypes' mancante nel JSON")

    val partTypes = arr.mapIndexed { index, element ->
        parsePartTypeFromJson(element.jsonObject, index)
    }

    runBlocking {
        store.upsertAll(partTypes)
    }

    println("Seed completato: ${partTypes.size} tipi di parte inseriti.")
}
