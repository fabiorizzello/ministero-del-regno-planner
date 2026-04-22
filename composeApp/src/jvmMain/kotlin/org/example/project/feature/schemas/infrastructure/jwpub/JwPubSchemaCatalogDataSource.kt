package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import org.example.project.core.domain.DomainError
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.RemoteWeekSchemaTemplate
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SkippedPart
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset

class JwPubSchemaCatalogDataSource(
    httpClient: HttpClient,
    private val cacheDir: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val language: String = "I",
) : SchemaCatalogRemoteSource {

    private val logger = KotlinLogging.logger {}
    private val mediaClient = JwPubMediaClient(httpClient)
    private val downloader = JwPubDownloader(httpClient)
    private val cache = JwPubCache(cacheDir)
    private val archiveReader = JwPubArchiveReader()
    private val sqliteReader = JwPubSqliteReader()
    private val decryptor = JwPubContentDecryptor()
    private val htmlParser = JwPubHtmlPartsParser()

    /**
     * Pipeline: discover candidate issues for the current year → for each, resolve
     * media links via JW CDN → download (or reuse cached) jwpub → parse weeks →
     * collect into a single [RemoteSchemaCatalog].
     *
     * 404 semantics: while no fascicolo has been obtained yet, a 404 skips to the next
     * candidate issue (the year may just not have started publishing yet). After one or
     * more fascicoli have been obtained, the first 404 terminates discovery — JW
     * publishes bimesters sequentially, so a gap implies we've reached the end of
     * currently-published content. If the CDN has a transient 404 gap, a later manual
     * refresh will pick up the missed issue once it's re-available.
     */
    override suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog> = either {
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val issues = MeetingWorkbookIssueDiscovery.candidatesForYear(
            year = today.year,
            startingFromMonth = today.monthValue,
        )

        val downloadedIssues = mutableListOf<String>()
        val allWeeks = mutableListOf<RemoteWeekSchemaTemplate>()
        val skippedUnknownParts = mutableListOf<SkippedPart>()
        var latestVersion: String? = null
        var hadAnyFascicolo = false

        for (issue in issues) {
            val mediaInfo = mediaClient.fetchMediaLinks("mwb", issue, language).bind()
            if (mediaInfo == null) {
                if (!hadAnyFascicolo) {
                    logger.info { "Issue $issue not yet published (404), probing next" }
                    continue
                }
                logger.info { "Issue $issue returned 404 after ≥1 successful fascicolo; stopping discovery" }
                break
            }
            hadAnyFascicolo = true

            val cached = cache.find(issue, language)
            val cachedJwPub = if (cache.isUpToDate(cached, mediaInfo)) {
                cached!!
            } else {
                val bytes = downloader.download(mediaInfo.url).bind()
                downloadedIssues += issue
                cache.store(issue, language, bytes, mediaInfo)
            }

            val parsed = Either.catch { parseFascicolo(cachedJwPub.file) }
                .mapLeft { DomainError.CatalogoJwPubCorrotto(it.message ?: "parse failed for $issue") }
                .bind()
            allWeeks += parsed.weeks
            skippedUnknownParts += parsed.skippedUnknown
            latestVersion = parsed.version ?: latestVersion
        }

        RemoteSchemaCatalog(
            version = latestVersion,
            weeks = allWeeks.sortedBy { it.weekStartDate },
            skippedUnknownParts = skippedUnknownParts,
            downloadedIssues = downloadedIssues,
        )
    }

    private fun parseFascicolo(jwpubFile: Path): FascicoloParse {
        val manifest = archiveReader.readManifest(jwpubFile)
        Files.createDirectories(cacheDir)
        val tmpDir = Files.createTempDirectory(cacheDir, "jwpub-")
        try {
            val dbFile = archiveReader.extractInnerDb(jwpubFile, tmpDir)
            val pubCard = sqliteReader.readPubCard(dbFile)
            val keyIv = decryptor.deriveKeyIv(pubCard)
            val weekRows = sqliteReader.readWeeks(dbFile)

            val weeks = mutableListOf<RemoteWeekSchemaTemplate>()
            val skipped = mutableListOf<SkippedPart>()

            for (row in weekRows) {
                val html = decryptor.decryptAndInflate(row.content, keyIv)
                val parts = htmlParser.parseParts(html)
                val efficaciParts = parts.filter { it.section == JwPubSection.EFFICACI }
                val weekStartDate = JwPubWeekDateResolver.resolve(row.title, pubCard.year)

                val partCodes = mutableListOf<String>()
                for (p in efficaciParts) {
                    when (val outcome = PartTypeLabelResolver.resolve(p.title, p.detailLine)) {
                        is PartTypeLabelResolver.ResolveOutcome.Mapped -> partCodes += outcome.code
                        is PartTypeLabelResolver.ResolveOutcome.NotEfficaci -> Unit
                        is PartTypeLabelResolver.ResolveOutcome.Unknown -> {
                            skipped += SkippedPart(
                                weekStartDate = weekStartDate.toString(),
                                mepsDocumentId = row.mepsDocumentId,
                                label = p.title,
                                detailLine = p.detailLine,
                            )
                            logger.warn { "Skipped unknown part: '${p.title}' week ${row.title}" }
                        }
                    }
                }
                weeks += RemoteWeekSchemaTemplate(
                    weekStartDate = weekStartDate.toString(),
                    partTypeCodes = partCodes,
                )
            }

            return FascicoloParse(
                version = manifest.publication.issueId?.toString(),
                weeks = weeks,
                skippedUnknown = skipped,
            )
        } finally {
            runCatching { tmpDir.toFile().deleteRecursively() }
        }
    }

    private data class FascicoloParse(
        val version: String?,
        val weeks: List<RemoteWeekSchemaTemplate>,
        val skippedUnknown: List<SkippedPart>,
    )
}
