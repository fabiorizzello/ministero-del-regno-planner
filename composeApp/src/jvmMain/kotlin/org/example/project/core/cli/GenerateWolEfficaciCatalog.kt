package org.example.project.core.cli

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.project.core.config.RemoteConfig
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import kotlin.system.exitProcess

private const val DEFAULT_MEETINGS_ROOT_URL = "https://wol.jw.org/it/wol/meetings/r6/lp-i"
private const val DEFAULT_OUTPUT_PATH = ".worktrees/efficaci-nel-ministero-data/schemas-catalog.json"
private const val DEFAULT_MAX_WEEKS = 120
private const val DEFAULT_COOLDOWN_MIN_MS = 1600L
private const val DEFAULT_COOLDOWN_MAX_MS = 3200L
private const val CONSECUTIVE_NO_PROGRAM_STOP_WEEKS = 2
private const val LETTURA_BIBBIA_POINT_NUMBER = 3
private const val LETTURA_BIBBIA_LABEL = "Lettura della Bibbia"
private const val LETTURA_BIBBIA_CODE = "LETTURA_DELLA_BIBBIA"
private const val LETTURA_BIBBIA_SEX_RULE = "UOMO"
private const val DISCORSO_LABEL = "Discorso"
private const val DISCORSO_CODE = "DISCORSO"
private const val SEX_RULE_STESSO_SESSO = "STESSO_SESSO"

fun main(args: Array<String>) {
    val config = CliConfig.parse(args)
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    val cooldown = WolRequestCooldown(config.cooldownMinMs, config.cooldownMaxMs)

    val currentWeekUrl = resolveCurrentWeekUrl(config, client, cooldown)
    println("Settimana corrente rilevata: $currentWeekUrl")

    val existingCatalog = loadExistingOutputCatalog(config.outputPath)
    val existingWeekDates = existingCatalog
        ?.weeks
        ?.mapNotNull { week -> runCatching { LocalDate.parse(week.weekStartDate) }.getOrNull() }
        ?.toSet()
        ?: emptySet()
    if (existingWeekDates.isNotEmpty()) {
        println("Modalita incrementale: ${existingWeekDates.size} settimane gia presenti nel file output.")
    }

    val crawlReport = crawlCurrentAndFutureWeeks(
        startWeekUrl = currentWeekUrl,
        config = config,
        client = client,
        cooldown = cooldown,
        existingWeekDates = existingWeekDates,
    )
    val scrapedWeeks = crawlReport.scrapedWeeks
    println("Settimane elaborate: ${scrapedWeeks.size}")
    println("Settimane gia presenti saltate: ${crawlReport.alreadyPresentWeeks}")
    printSkippedWeeksReport(crawlReport.skippedWeeks)

    val existingPartTypes = existingCatalog?.partTypes.orEmpty()
    val basePartTypes = loadBasePartTypes(
        baseCatalogUrl = config.baseCatalogUrl,
        client = client,
        cooldown = cooldown,
    )
    val output = buildCatalog(
        scrapedWeeks = scrapedWeeks,
        basePartTypes = mergePartTypesByCode(basePartTypes, existingPartTypes),
    )
    val finalOutput = mergeCatalogs(existingCatalog, output)

    val outputFile = File(config.outputPath).absoluteFile
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(
        Json {
            prettyPrint = true
            encodeDefaults = true
        }.encodeToString(finalOutput.toJsonObject()),
    )

    println("JSON generato: ${outputFile.path}")
    println("Part types: ${finalOutput.partTypes.size}, settimane: ${finalOutput.weeks.size}")
}

private data class CliConfig(
    val meetingsRootUrl: String = DEFAULT_MEETINGS_ROOT_URL,
    val outputPath: String = DEFAULT_OUTPUT_PATH,
    val baseCatalogUrl: String = RemoteConfig.SCHEMAS_CATALOG_URL,
    val maxWeeks: Int = DEFAULT_MAX_WEEKS,
    val cooldownMinMs: Long = DEFAULT_COOLDOWN_MIN_MS,
    val cooldownMaxMs: Long = DEFAULT_COOLDOWN_MAX_MS,
) {
    companion object {
        fun parse(args: Array<String>): CliConfig {
            if (args.any { it == "--help" || it == "-h" }) {
                printUsage()
                exitProcess(0)
            }

            var config = CliConfig()
            var i = 0
            while (i < args.size) {
                when {
                    args[i].startsWith("--meetings-root-url=") -> {
                        config = config.copy(meetingsRootUrl = args[i].substringAfter("="))
                    }
                    args[i] == "--meetings-root-url" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --meetings-root-url")
                        config = config.copy(meetingsRootUrl = value)
                        i++
                    }
                    // Backward compatibility: alias mantenuto, ma il crawler parte comunque dalla settimana attuale.
                    args[i].startsWith("--start-url=") -> {
                        config = config.copy(meetingsRootUrl = args[i].substringAfter("="))
                    }
                    args[i] == "--start-url" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --start-url")
                        config = config.copy(meetingsRootUrl = value)
                        i++
                    }
                    args[i].startsWith("--output=") -> {
                        config = config.copy(outputPath = args[i].substringAfter("="))
                    }
                    args[i] == "--output" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --output")
                        config = config.copy(outputPath = value)
                        i++
                    }
                    args[i].startsWith("--base-catalog-url=") -> {
                        config = config.copy(baseCatalogUrl = args[i].substringAfter("="))
                    }
                    args[i] == "--base-catalog-url" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --base-catalog-url")
                        config = config.copy(baseCatalogUrl = value)
                        i++
                    }
                    args[i].startsWith("--max-weeks=") -> {
                        config = config.copy(maxWeeks = args[i].substringAfter("=").toInt())
                    }
                    args[i] == "--max-weeks" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --max-weeks")
                        config = config.copy(maxWeeks = value.toInt())
                        i++
                    }
                    args[i].startsWith("--cooldown-ms=") -> {
                        val value = args[i].substringAfter("=").toLong()
                        config = config.copy(cooldownMinMs = value, cooldownMaxMs = value)
                    }
                    args[i] == "--cooldown-ms" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --cooldown-ms")
                        val parsed = value.toLong()
                        config = config.copy(cooldownMinMs = parsed, cooldownMaxMs = parsed)
                        i++
                    }
                    args[i].startsWith("--cooldown-min-ms=") -> {
                        config = config.copy(cooldownMinMs = args[i].substringAfter("=").toLong())
                    }
                    args[i] == "--cooldown-min-ms" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --cooldown-min-ms")
                        config = config.copy(cooldownMinMs = value.toLong())
                        i++
                    }
                    args[i].startsWith("--cooldown-max-ms=") -> {
                        config = config.copy(cooldownMaxMs = args[i].substringAfter("=").toLong())
                    }
                    args[i] == "--cooldown-max-ms" -> {
                        val value = args.getOrNull(i + 1)
                            ?: error("Valore mancante per --cooldown-max-ms")
                        config = config.copy(cooldownMaxMs = value.toLong())
                        i++
                    }
                    args[i].startsWith("--") -> {
                        error("Argomento non riconosciuto: ${args[i]}")
                    }
                }
                i++
            }

            require(config.maxWeeks >= 1) { "--max-weeks deve essere >= 1" }
            require(config.cooldownMinMs >= 0) { "--cooldown-min-ms deve essere >= 0" }
            require(config.cooldownMaxMs >= config.cooldownMinMs) {
                "--cooldown-max-ms deve essere >= --cooldown-min-ms"
            }
            return config
        }

        private fun printUsage() {
            println(
                """
                Uso:
                  ./gradlew :composeApp:generateWolEfficaciCatalog --args="--output data/schemas-catalog.wol.json"

                Opzioni:
                  --meetings-root-url <url> URL base meetings WOL (la settimana iniziale viene sempre risolta da 'todayWeek')
                  --output <path>           Path file JSON output
                  --base-catalog-url <url>  Catalogo base per riuso metadati partTypes
                  --max-weeks <n>           Limite sicurezza settimane da scansionare
                  --cooldown-ms <n>         Cooldown fisso (ms) tra richieste WOL
                  --cooldown-min-ms <n>     Cooldown minimo (ms) tra richieste WOL
                  --cooldown-max-ms <n>     Cooldown massimo (ms) tra richieste WOL
                """.trimIndent(),
            )
        }
    }
}

internal data class ScrapedWeek(
    val weekStartDate: LocalDate,
    val meetingsUrl: String,
    val vitaMinisteroUrl: String,
    val efficaciParts: List<EfficaciPart>,
)

private data class SkippedWeek(
    val meetingsUrl: String,
    val reason: String,
)

private data class CrawlReport(
    val scrapedWeeks: List<ScrapedWeek>,
    val skippedWeeks: List<SkippedWeek>,
    val alreadyPresentWeeks: Int,
)

internal data class OutputCatalog(
    val version: String,
    val updatedAt: String,
    val partTypes: List<OutputPartType>,
    val weeks: List<OutputWeek>,
)

internal data class OutputPartType(
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: String,
    val fixed: Boolean,
)

internal data class OutputWeek(
    val weekStartDate: String,
    val parts: List<OutputWeekPart>,
)

internal data class OutputWeekPart(
    val partTypeCode: String,
)

private fun OutputCatalog.toJsonObject() = buildJsonObject {
    put("version", version)
    put("updated_at", updatedAt)
    put(
        "partTypes",
        buildJsonArray {
            partTypes.forEach { part ->
                add(
                    buildJsonObject {
                        put("code", part.code)
                        put("label", part.label)
                        put("peopleCount", part.peopleCount)
                        put("sexRule", part.sexRule)
                        put("fixed", part.fixed)
                    },
                )
            }
        },
    )
    put(
        "weeks",
        buildJsonArray {
            weeks.forEach { week ->
                add(
                    buildJsonObject {
                        put("weekStartDate", week.weekStartDate)
                        put(
                            "parts",
                            buildJsonArray {
                                week.parts.forEach { part ->
                                    add(
                                        buildJsonObject {
                                            put("partTypeCode", part.partTypeCode)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
    )
}

private fun resolveCurrentWeekUrl(
    config: CliConfig,
    client: HttpClient,
    cooldown: WolRequestCooldown,
): String {
    val rootUrl = config.meetingsRootUrl.trimEnd('/')
    val html = httpGet(rootUrl, client, cooldown)
    return WolHtmlParser.parseCurrentWeekMeetingsUrl(html, rootUrl)
        ?: error("Impossibile determinare la settimana attuale da: $rootUrl")
}

private fun crawlCurrentAndFutureWeeks(
    startWeekUrl: String,
    config: CliConfig,
    client: HttpClient,
    cooldown: WolRequestCooldown,
    existingWeekDates: Set<LocalDate>,
): CrawlReport {
    val visitedUrls = mutableSetOf<String>()
    val weeks = mutableListOf<ScrapedWeek>()
    val skippedWeeks = mutableListOf<SkippedWeek>()
    var alreadyPresentWeeks = 0
    var currentUrl: String? = absolutize(startWeekUrl, startWeekUrl)
    var index = 1
    var consecutiveNoProgramWeeks = 0

    while (currentUrl != null && index <= config.maxWeeks) {
        val normalizedUrl = absolutize(currentUrl, currentUrl)
        if (!visitedUrls.add(normalizedUrl)) {
            println("URL già visitato, stop su: $normalizedUrl")
            break
        }

        println("[$index/${config.maxWeeks}] Lettura settimana: $normalizedUrl")
        val outcome = try {
            processSingleWeek(
                meetingsUrl = normalizedUrl,
                client = client,
                cooldown = cooldown,
                existingWeekDates = existingWeekDates,
            )
        } catch (error: Exception) {
            val reason = "${error::class.simpleName ?: "Errore"}: ${error.message ?: "dettaglio non disponibile"}"
            println("  Errore settimana, salto: $reason")
            skippedWeeks += SkippedWeek(
                meetingsUrl = normalizedUrl,
                reason = reason,
            )
            val fallbackNext = nextWeekUrlFromMeetingsUrl(normalizedUrl)
            if (fallbackNext == null) {
                println("  Impossibile calcolare la settimana successiva da $normalizedUrl, stop.")
                break
            }
            currentUrl = fallbackNext
            index++
            continue
        }

        when (outcome) {
            is WeekProcessOutcome.EndOfPublishedContent -> {
                consecutiveNoProgramWeeks++
                val reason = "Programma non disponibile (sezione Vita e ministero assente)"
                println("  $reason per ${outcome.weekStartDate}, settimana saltata.")
                skippedWeeks += SkippedWeek(
                    meetingsUrl = normalizedUrl,
                    reason = reason,
                )

                if (consecutiveNoProgramWeeks >= CONSECUTIVE_NO_PROGRAM_STOP_WEEKS) {
                    println(
                        "  Trovate $consecutiveNoProgramWeeks settimane consecutive senza programma, fine scansione.",
                    )
                    break
                }

                val nextUrl = outcome.nextWeekUrl ?: nextWeekUrlFromMeetingsUrl(normalizedUrl)
                if (nextUrl == null) {
                    println("  Impossibile determinare la settimana successiva dopo ${outcome.weekStartDate}, stop.")
                    break
                }
                currentUrl = nextUrl
            }
            is WeekProcessOutcome.AlreadyPresent -> {
                consecutiveNoProgramWeeks = 0
                alreadyPresentWeeks++
                println("  Settimana ${outcome.weekStartDate} gia presente, salto (incrementale).")
                val nextUrl = outcome.nextWeekUrl ?: nextWeekUrlFromMeetingsUrl(normalizedUrl)
                if (nextUrl == null) {
                    println("  Impossibile determinare la settimana successiva dopo ${outcome.weekStartDate}, stop.")
                    break
                }
                currentUrl = nextUrl
            }
            is WeekProcessOutcome.Success -> {
                consecutiveNoProgramWeeks = 0
                val week = outcome.week
                if (week.efficaciParts.isEmpty()) {
                    println("  Nessuna parte EFFICACI trovata per ${week.weekStartDate}")
                } else {
                    println("  Parti EFFICACI trovate: ${week.efficaciParts.size}")
                }
                weeks += week
                currentUrl = outcome.nextWeekUrl
            }
        }

        index++
    }

    return CrawlReport(
        scrapedWeeks = weeks.sortedBy { it.weekStartDate },
        skippedWeeks = skippedWeeks,
        alreadyPresentWeeks = alreadyPresentWeeks,
    )
}

private sealed interface WeekProcessOutcome {
    data class Success(
        val week: ScrapedWeek,
        val nextWeekUrl: String?,
    ) : WeekProcessOutcome

    data class EndOfPublishedContent(
        val weekStartDate: LocalDate,
        val nextWeekUrl: String?,
    ) : WeekProcessOutcome

    data class AlreadyPresent(
        val weekStartDate: LocalDate,
        val nextWeekUrl: String?,
    ) : WeekProcessOutcome
}

private fun processSingleWeek(
    meetingsUrl: String,
    client: HttpClient,
    cooldown: WolRequestCooldown,
    existingWeekDates: Set<LocalDate>,
): WeekProcessOutcome {
    val meetingsHtml = httpGet(meetingsUrl, client, cooldown)
    val page = WolHtmlParser.parseMeetingsWeekPage(meetingsHtml, meetingsUrl)
        ?: error("Impossibile leggere la data settimana da: $meetingsUrl")
    if (page.weekStartDate in existingWeekDates) {
        return WeekProcessOutcome.AlreadyPresent(
            weekStartDate = page.weekStartDate,
            nextWeekUrl = page.nextWeekUrl,
        )
    }

    val vitaMinisteroUrl = page.vitaMinisteroUrl
        ?: return WeekProcessOutcome.EndOfPublishedContent(
            weekStartDate = page.weekStartDate,
            nextWeekUrl = page.nextWeekUrl,
        )

    val articleHtml = httpGet(vitaMinisteroUrl, client, cooldown)
    val efficaci = WolHtmlParser.parseEfficaciSectionParts(articleHtml)

    return WeekProcessOutcome.Success(
        week = ScrapedWeek(
            weekStartDate = page.weekStartDate,
            meetingsUrl = meetingsUrl,
            vitaMinisteroUrl = vitaMinisteroUrl,
            efficaciParts = efficaci,
        ),
        nextWeekUrl = page.nextWeekUrl,
    )
}

private fun printSkippedWeeksReport(skippedWeeks: List<SkippedWeek>) {
    println("Settimane saltate: ${skippedWeeks.size}")
    if (skippedWeeks.isEmpty()) return
    skippedWeeks.forEachIndexed { idx, skipped ->
        println("  [${idx + 1}] ${skipped.meetingsUrl} -> ${skipped.reason}")
    }
}

private fun loadBasePartTypes(
    baseCatalogUrl: String,
    client: HttpClient,
    cooldown: WolRequestCooldown,
): List<OutputPartType> {
    return runCatching {
        val body = httpGet(baseCatalogUrl, client, cooldown)
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["partTypes"]?.jsonArray ?: return emptyList()
        arr.mapNotNull { partElement ->
            val obj = partElement.jsonObject
            val code = obj["code"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val peopleCount = obj["peopleCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2
            val sexRule = normalizeSexRule(
                obj["sexRule"]?.jsonPrimitive?.content ?: SEX_RULE_STESSO_SESSO,
            )
            val fixed = obj["fixed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            OutputPartType(
                code = code,
                label = label,
                peopleCount = peopleCount,
                sexRule = sexRule,
                fixed = fixed,
            )
        }
    }.getOrElse { error ->
        println("Catalogo base non disponibile ($baseCatalogUrl): ${error.message}")
        emptyList()
    }
}

internal fun buildCatalog(
    scrapedWeeks: List<ScrapedWeek>,
    basePartTypes: List<OutputPartType>,
): OutputCatalog {
    val baseByLabelKey = basePartTypes.associateBy { labelKey(it.label) }
    val usedPartTypes = linkedMapOf<String, OutputPartType>()
    val allKnownCodes = basePartTypes.map { it.code }.toMutableSet()
    val generatedByLabelKey = mutableMapOf<String, OutputPartType>()
    val letturaBibbiaPartType = OutputPartType(
        code = LETTURA_BIBBIA_CODE,
        label = LETTURA_BIBBIA_LABEL,
        peopleCount = 1,
        sexRule = LETTURA_BIBBIA_SEX_RULE,
        fixed = true,
    )
    val discorsoPartType = OutputPartType(
        code = DISCORSO_CODE,
        label = DISCORSO_LABEL,
        peopleCount = 1,
        sexRule = LETTURA_BIBBIA_SEX_RULE,
        fixed = false,
    )

    fun resolvePartType(title: String): OutputPartType {
        if (isLetturaBibbiaTitle(title)) {
            allKnownCodes += LETTURA_BIBBIA_CODE
            usedPartTypes[LETTURA_BIBBIA_CODE] = letturaBibbiaPartType
            return letturaBibbiaPartType
        }
        if (isDiscorsoTitle(title)) {
            allKnownCodes += DISCORSO_CODE
            usedPartTypes[DISCORSO_CODE] = discorsoPartType
            return discorsoPartType
        }

        val key = labelKey(title)

        baseByLabelKey[key]?.let { base ->
            usedPartTypes.putIfAbsent(base.code, base)
            return base
        }

        generatedByLabelKey[key]?.let { generated ->
            usedPartTypes.putIfAbsent(generated.code, generated)
            return generated
        }

        val baseCode = codeFromLabel(title)
        var code = baseCode
        var suffix = 2
        while (code in allKnownCodes) {
            code = "${baseCode}_$suffix"
            suffix++
        }

        val generated = OutputPartType(
            code = code,
            label = title,
            peopleCount = 2,
            sexRule = SEX_RULE_STESSO_SESSO,
            fixed = false,
        )

        allKnownCodes += code
        generatedByLabelKey[key] = generated
        usedPartTypes[code] = generated
        return generated
    }

    val outputWeeks = scrapedWeeks.map { week ->
        val partCodes = ensureLetturaBibbiaPointThree(week.efficaciParts).map { part ->
            resolvePartType(part.title).code
        }
        OutputWeek(
            weekStartDate = week.weekStartDate.toString(),
            parts = partCodes.map { OutputWeekPart(partTypeCode = it) },
        )
    }

    return OutputCatalog(
        version = LocalDate.now().toString(),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
        partTypes = reorderPartTypes(usedPartTypes.values.toList()),
        weeks = outputWeeks,
    )
}

private fun reorderPartTypes(partTypes: List<OutputPartType>): List<OutputPartType> {
    val (lettura, others) = partTypes.partition { it.code == LETTURA_BIBBIA_CODE }
    return lettura + others
}

private fun mergePartTypesByCode(
    first: List<OutputPartType>,
    second: List<OutputPartType>,
): List<OutputPartType> {
    val byCode = linkedMapOf<String, OutputPartType>()
    first.forEach { byCode[it.code] = it }
    second.forEach { byCode[it.code] = it }
    return byCode.values.toList()
}

private fun mergeCatalogs(
    existing: OutputCatalog?,
    generated: OutputCatalog,
): OutputCatalog {
    if (existing == null) return generated

    val mergedPartTypes = mergePartTypesByCode(existing.partTypes, generated.partTypes)
    val mergedWeeksByDate = linkedMapOf<String, OutputWeek>()
    existing.weeks.forEach { week -> mergedWeeksByDate[week.weekStartDate] = week }
    generated.weeks.forEach { week -> mergedWeeksByDate[week.weekStartDate] = week }
    val mergedWeeks = mergedWeeksByDate.values.sortedBy { it.weekStartDate }

    return generated.copy(
        partTypes = reorderPartTypes(mergedPartTypes),
        weeks = mergedWeeks,
    )
}

private fun loadExistingOutputCatalog(outputPath: String): OutputCatalog? {
    val file = File(outputPath)
    if (!file.exists()) return null

    return runCatching {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val version = root["version"]?.jsonPrimitive?.content ?: LocalDate.now().toString()
        val updatedAt = root["updated_at"]?.jsonPrimitive?.content ?: OffsetDateTime.now(ZoneOffset.UTC).toString()
        val partTypes = root["partTypes"]?.jsonArray.orEmpty().mapNotNull { partEl ->
            val obj = partEl.jsonObject
            val code = obj["code"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val peopleCount = obj["peopleCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2
            val sexRule = normalizeSexRule(
                obj["sexRule"]?.jsonPrimitive?.content ?: SEX_RULE_STESSO_SESSO,
            )
            val fixed = obj["fixed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            OutputPartType(
                code = code,
                label = label,
                peopleCount = peopleCount,
                sexRule = sexRule,
                fixed = fixed,
            )
        }
        val weeks = root["weeks"]?.jsonArray.orEmpty().mapNotNull { weekEl ->
            val obj = weekEl.jsonObject
            val weekStartDate = obj["weekStartDate"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val parts = obj["parts"]?.jsonArray.orEmpty().mapNotNull { partEl ->
                val partTypeCode = partEl.jsonObject["partTypeCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
                OutputWeekPart(partTypeCode = partTypeCode)
            }
            OutputWeek(
                weekStartDate = weekStartDate,
                parts = parts,
            )
        }

        OutputCatalog(
            version = version,
            updatedAt = updatedAt,
            partTypes = partTypes,
            weeks = weeks,
        )
    }.onFailure {
        println("Output esistente non leggibile, verra rigenerato da zero: ${it.message}")
    }.getOrNull()
}

private fun httpGet(
    url: String,
    client: HttpClient,
    cooldown: WolRequestCooldown,
): String {
    cooldown.waitIfNeeded(url)
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(45))
        .header(
            "User-Agent",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
        )
        .GET()
        .build()
    val response = try {
        client.send(request, HttpResponse.BodyHandlers.ofString())
    } finally {
        cooldown.markCompleted(url)
    }
    if (response.statusCode() != 200) {
        error("HTTP ${response.statusCode()} su $url")
    }
    return response.body()
}

private fun absolutize(url: String, baseUrl: String): String = URI(baseUrl).resolve(url).toString()

private fun labelKey(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replace(Regex("[^A-Z0-9]+"), " ")
        .trim()
    return normalized.replace(Regex("\\s+"), " ")
}

private fun isLetturaBibbiaTitle(value: String): Boolean {
    val key = labelKey(value)
    return key.contains("LETTURA") && (key.contains("BIBBIA") || key.contains("BIBLICA"))
}

private fun isDiscorsoTitle(value: String): Boolean = labelKey(value).contains("DISCORSO")

private fun ensureLetturaBibbiaPointThree(parts: List<EfficaciPart>): List<EfficaciPart> {
    val normalizedParts = parts.filterNot { part ->
        part.number == LETTURA_BIBBIA_POINT_NUMBER || isLetturaBibbiaTitle(part.title)
    }
    return listOf(
        EfficaciPart(
            number = LETTURA_BIBBIA_POINT_NUMBER,
            title = LETTURA_BIBBIA_LABEL,
        ),
    ) + normalizedParts
}

private fun normalizeSexRule(value: String): String = when (val normalized = value.uppercase(Locale.ROOT)) {
    "UOMO", SEX_RULE_STESSO_SESSO -> normalized
    else -> SEX_RULE_STESSO_SESSO
}

private fun codeFromLabel(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .replace(Regex("_+"), "_")
    return if (normalized.isBlank()) "PARTE_EFFICACI" else normalized
}

internal fun nextWeekUrlFromMeetingsUrl(currentUrl: String): String? {
    val uri = runCatching { URI.create(currentUrl) }.getOrNull() ?: return null
    val segments = uri.path.trim('/').split('/').filter { it.isNotBlank() }
    if (segments.size < 2) return null

    val year = segments[segments.lastIndex - 1].toIntOrNull() ?: return null
    val week = segments.last().toIntOrNull() ?: return null
    if (year <= 0 || week <= 0) return null

    // WOL usa la numerazione settimana del selettore meetings (1..52 per il nostro flusso).
    val (nextYear, nextWeek) = if (week >= 52) {
        year + 1 to 1
    } else {
        year to (week + 1)
    }

    val nextPath = buildList {
        addAll(segments.dropLast(2))
        add(nextYear.toString())
        add(nextWeek.toString().padStart(2, '0'))
    }.joinToString(separator = "/", prefix = "/")

    return URI(
        uri.scheme,
        uri.authority,
        nextPath,
        null,
        null,
    ).toString()
}

private class WolRequestCooldown(
    private val minMs: Long,
    private val maxMs: Long,
) {
    private var nextAllowedAtMillis: Long = 0

    fun waitIfNeeded(url: String) {
        if (!isWolUrl(url)) return
        val now = System.currentTimeMillis()
        val waitMs = nextAllowedAtMillis - now
        if (waitMs > 0) {
            Thread.sleep(waitMs)
        }
    }

    fun markCompleted(url: String) {
        if (!isWolUrl(url)) return
        val delay = if (minMs == maxMs) {
            minMs
        } else {
            ThreadLocalRandom.current().nextLong(minMs, maxMs + 1)
        }
        nextAllowedAtMillis = System.currentTimeMillis() + delay
    }

    private fun isWolUrl(url: String): Boolean =
        runCatching { URI.create(url).host?.endsWith("wol.jw.org") == true }
            .getOrDefault(false)
}

internal data class MeetingsWeekPage(
    val weekStartDate: LocalDate,
    val vitaMinisteroUrl: String?,
    val nextWeekUrl: String?,
)

internal data class EfficaciPart(
    val number: Int,
    val title: String,
)

internal object WolHtmlParser {
    private val partHeadingRegex = Regex("""^(\d+)[\.\)]\s*(.+)$""")
    private val dateRegex = Regex("""(?:\?|&)date=(\d{4}-\d{2}-\d{2})(?:&|$)""")
    private val todayWeekRegex = Regex("""^(\d{4})/(\d{1,2})$""")
    private val vitaMinisteroTitleRegex = Regex("""\bVITA\s+E\s+MINISTERO\b""", RegexOption.IGNORE_CASE)

    fun parseCurrentWeekMeetingsUrl(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val rootMeetingsUrl = doc.selectFirst("#navigationDailyTextToday a[href], a.todayNav[href*=/wol/meetings/]")
            ?.absUrl("href")
            ?.trimEnd('/')
            ?: baseUrl.trimEnd('/')
        val rawTodayWeek = doc.getElementById("todayWeek")
            ?.attr("value")
            ?.trim()
            ?: return null
        val match = todayWeekRegex.matchEntire(rawTodayWeek) ?: return null
        val year = match.groupValues[1]
        val week = match.groupValues[2].toInt().toString().padStart(2, '0')
        return "$rootMeetingsUrl/$year/$week"
    }

    fun parseMeetingsWeekPage(html: String, baseUrl: String): MeetingsWeekPage? {
        val doc = Jsoup.parse(html, baseUrl)
        val shareBaseUrl = doc.getElementById("shareBaseUrl")?.attr("value") ?: return null
        val dateString = dateRegex.find(shareBaseUrl)?.groupValues?.get(1) ?: return null
        val weekStartDate = LocalDate.parse(dateString)

        val materialNav = doc.getElementById("materialNav")
        val hasVitaMinisteroSection = materialNav
            ?.select("h2")
            ?.any { vitaMinisteroTitleRegex.containsMatchIn(it.text()) }
            ?: false
        val vitaLink = if (hasVitaMinisteroSection) {
            materialNav
                .select("li[class*=pub-mwb] a[href*=/wol/d/]")
                .firstOrNull()
                ?.absUrl("href")
        } else {
            null
        }

        val nextWeekUrl = doc.selectFirst("#footerNextWeek a[href]")?.absUrl("href")

        return MeetingsWeekPage(
            weekStartDate = weekStartDate,
            vitaMinisteroUrl = vitaLink,
            nextWeekUrl = nextWeekUrl,
        )
    }

    fun parseEfficaciSectionParts(html: String): List<EfficaciPart> {
        val doc = Jsoup.parse(html)
        val headings = doc.select("h2, h3")
        val startIndex = headings.indexOfFirst {
            it.tagName() == "h2" && labelKey(it.text()) == labelKey("EFFICACI NEL MINISTERO")
        }
        if (startIndex == -1) return ensureLetturaBibbiaPointThree(emptyList())

        val parts = mutableListOf<EfficaciPart>()
        for (i in startIndex + 1 until headings.size) {
            val heading = headings[i]
            if (heading.tagName() == "h2") break
            if (heading.tagName() != "h3") continue

            val normalizedText = heading.text().replace('\u00A0', ' ').trim()
            val match = partHeadingRegex.find(normalizedText) ?: continue
            val number = match.groupValues[1].toInt()
            val title = match.groupValues[2].trim()

            parts += EfficaciPart(number = number, title = title)
        }

        return ensureLetturaBibbiaPointThree(parts)
    }
}
