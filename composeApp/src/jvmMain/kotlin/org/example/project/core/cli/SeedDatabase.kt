package org.example.project.core.cli

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.core.bootstrap.AppBootstrap
import org.example.project.core.persistence.DatabaseProvider
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightPartTypeStore
import java.io.File
import java.util.UUID

fun main() {
    AppBootstrap.initialize()
    val db = DatabaseProvider.database()
    val store = SqlDelightPartTypeStore(db)

    val jsonFile = File("data/part-types.json")
    require(jsonFile.exists()) { "File non trovato: ${jsonFile.absolutePath}" }

    val root = Json.parseToJsonElement(jsonFile.readText()).jsonObject
    val arr = root["partTypes"]?.jsonArray ?: error("Campo 'partTypes' mancante nel JSON")

    val partTypes = arr.mapIndexed { index, element ->
        val obj = element.jsonObject
        PartType(
            id = PartTypeId(UUID.randomUUID().toString()),
            code = obj["code"]!!.jsonPrimitive.content,
            label = obj["label"]!!.jsonPrimitive.content,
            peopleCount = obj["peopleCount"]!!.jsonPrimitive.int,
            sexRule = SexRule.valueOf(obj["sexRule"]!!.jsonPrimitive.content),
            fixed = obj["fixed"]?.jsonPrimitive?.boolean ?: false,
            sortOrder = index,
        )
    }

    runBlocking {
        store.upsertAll(partTypes)
    }

    println("Seed completato: ${partTypes.size} tipi di parte inseriti.")
}
