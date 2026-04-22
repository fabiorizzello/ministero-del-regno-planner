# JWPUB Catalog Update Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the `Aggiorna catalogo` pipeline (GitHub JSON) with a pipeline
that downloads official `.jwpub` meeting workbook files, decrypts the internal
SQLite content, extracts weeks and parts, caches by checksum, and shows skipped
unknown part labels in a forced result dialog.

**Architecture:** Vertical slice refactor inside `feature/schemas`. New
`JwPubSchemaCatalogDataSource` implementing the existing
`SchemaCatalogRemoteSource` interface. All jwpub-specific code (ktor client,
zip-in-zip reader, AES+zlib decryptor, sqlite-jdbc reader, jsoup HTML parser,
mapping table) lives in `infrastructure/jwpub/`. Application layer stays almost
identical; the only change is extending `RemoteSchemaCatalog` /
`AggiornaSchemiResult` with two new fields (`skippedUnknownParts`,
`downloadedIssues`) and adjusting the ViewModel to open the result dialog even
when there are no effective program changes, so long as there is something to
report.

**Tech Stack:** Kotlin 2.3.10, Compose Multiplatform, Arrow Either, ktor 3.4.0,
SQLDelight 2.2.1, `org.xerial:sqlite-jdbc:3.53.0.0` (new explicit dep),
`org.jsoup:jsoup:1.22.2` (upgrade from 1.18.3), JDK standard for AES / SHA256 /
zlib / zip.

**Design doc:** `docs/plans/2026-04-22-jwpub-catalog-update-design.md`.

**Required reading before starting:**

- `docs/plans/2026-04-22-jwpub-catalog-update-design.md` (full design).
- `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCase.kt`.
- `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/SchemaCatalogRemoteSource.kt`.
- `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/GitHubSchemaCatalogDataSource.kt`.
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt`.
- `composeApp/src/jvmMain/kotlin/org/example/project/core/config/PathsResolver.kt`.
- `CLAUDE.md` (if present) for project conventions.
- JUnit4 + `kotlin.test` footgun: `assertIs<T>()` returns `T`, JUnit4 rejects
  non-void test methods. Always end with `Unit` explicit if last expression is
  `assertIs`.

---

## Task 1: Gradle — explicit sqlite-jdbc + jsoup upgrade

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

**Step 1: Update version catalog**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
jsoup = "1.22.2"
sqliteJdbc = "3.53.0.0"
```

(Replace existing `jsoup = "1.18.3"`.)

Under `[libraries]`:

```toml
sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqliteJdbc" }
```

**Step 2: Wire dependency into composeApp**

Open `composeApp/build.gradle.kts`. Find the `jvmMain.dependencies { ... }`
block (look for `libs.jsoup` which is already there). Add:

```kotlin
implementation(libs.sqlite.jdbc)
```

**Step 3: Refresh Gradle dependency graph**

```bash
./gradlew :composeApp:dependencies --configuration jvmRuntimeClasspath | grep -iE "sqlite|jsoup" | head
```

Expected output includes:

```
+--- app.cash.sqldelight:sqlite-driver:2.2.1
|    +--- org.xerial:sqlite-jdbc:3.51.0.0 -> 3.53.0.0
+--- org.xerial:sqlite-jdbc:3.53.0.0 (*)
+--- org.jsoup:jsoup:1.22.2
```

**Step 4: Verify existing tests still compile and pass**

```bash
./gradlew :composeApp:jvmTest
```

Expected: BUILD SUCCESSFUL, no new failures.

**Step 5: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "build: add explicit sqlite-jdbc 3.53.0.0 and upgrade jsoup to 1.22.2"
```

---

## Task 2: AppPaths — expose GuidaAdunanza cache directory

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/config/PathsResolver.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/config/AppPaths.kt`
  (locate via `grep -rn "data class AppPaths" composeApp/src`).
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/core/config/PathsResolverTest.kt`
  (create if absent, otherwise extend).

**Step 1: Extend data class**

Add field to `AppPaths`:

```kotlin
data class AppPaths(
    val rootDir: Path,
    val dbFile: Path,
    val logsDir: Path,
    val exportsDir: Path,
    val jwpubCacheDir: Path,   // NEW
)
```

**Step 2: Write failing test**

Create or extend `PathsResolverTest.kt`:

```kotlin
@Test
fun `resolve creates and exposes the jwpub cache directory`() {
    val paths = PathsResolver.resolve()
    assertTrue(Files.isDirectory(paths.jwpubCacheDir),
        "jwpubCacheDir must exist: ${paths.jwpubCacheDir}")
    assertEquals(
        paths.rootDir.resolve("cache").resolve("GuidaAdunanza").normalize(),
        paths.jwpubCacheDir.normalize(),
    )
    Unit
}
```

**Step 3: Run test — expect FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.PathsResolverTest.resolve*jwpub*"
```

Expected: compilation error or missing property.

**Step 4: Implement**

In `PathsResolver.resolve()`, after `createDir(exportsDir)`:

```kotlin
val jwpubCacheDir = rootDir.resolve("cache").resolve("GuidaAdunanza")
createDir(jwpubCacheDir)
```

Pass `jwpubCacheDir = jwpubCacheDir` to the `AppPaths(...)` constructor call.

**Step 5: Run test — expect PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.PathsResolverTest.resolve*jwpub*"
```

Expected: OK.

**Step 6: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/core/config/PathsResolver.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/core/config/AppPaths.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/core/config/PathsResolverTest.kt
git commit -m "feat(paths): expose GuidaAdunanza jwpub cache directory"
```

---

## Task 3: DomainError — new variant CatalogoJwPubCorrotto

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/domain/DomainError.kt`

**Step 1: Add variant**

Near the existing schema-related variants (line ~39-40), add:

```kotlin
data class CatalogoJwPubCorrotto(val details: String) : DomainError
```

**Step 2: Add message mapping**

In the `toMessage` extension in the same file, add:

```kotlin
is DomainError.CatalogoJwPubCorrotto -> "Catalogo JW non leggibile: $details"
```

**Step 3: Verify compile**

```bash
./gradlew :composeApp:compileKotlinJvm
```

Expected: OK.

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/core/domain/DomainError.kt
git commit -m "feat(errors): add CatalogoJwPubCorrotto DomainError variant"
```

---

## Task 4: MeetingWorkbookIssueDiscovery (pure)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/MeetingWorkbookIssueDiscovery.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/MeetingWorkbookIssueDiscoveryTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingWorkbookIssueDiscoveryTest {

    @Test
    fun `january start returns six issues for the full year`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 1)
        assertEquals(
            listOf("202601", "202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }

    @Test
    fun `april start skips the january-february issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 4)
        assertEquals(
            listOf("202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }

    @Test
    fun `december start returns only the last issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 12)
        assertEquals(listOf("202611"), result)
    }

    @Test
    fun `mid-bimester month maps to the containing issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 2)
        assertEquals(
            listOf("202601", "202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.MeetingWorkbookIssueDiscoveryTest"
```

Expected: FAIL (class not found).

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

object MeetingWorkbookIssueDiscovery {
    private val BIMESTER_MONTHS = listOf(1, 3, 5, 7, 9, 11)

    fun candidatesForYear(year: Int, startingFromMonth: Int): List<String> {
        require(year in 1000..9999) { "year out of range: $year" }
        require(startingFromMonth in 1..12) { "month out of range: $startingFromMonth" }
        return BIMESTER_MONTHS
            .filter { issueMonth -> issueMonth + 1 >= startingFromMonth }
            .map { month -> "%04d%02d".format(year, month) }
    }
}
```

**Step 4: Run — expect PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.MeetingWorkbookIssueDiscoveryTest"
```

Expected: 4 tests passed.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/MeetingWorkbookIssueDiscovery.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/MeetingWorkbookIssueDiscoveryTest.kt
git commit -m "feat(schemas): add MeetingWorkbookIssueDiscovery pure helper"
```

---

## Task 5: PartTypeLabelResolver (pure mapping)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/PartTypeLabelResolver.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/PartTypeLabelResolverTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PartTypeLabelResolverTest {

    @Test fun `lettura biblica`() {
        val r = PartTypeLabelResolver.resolve("3. Lettura biblica", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.Mapped("LETTURA_DELLA_BIBBIA"), r)
    }

    @Test fun `iniziare una conversazione`() {
        val r = PartTypeLabelResolver.resolve("4. Iniziare una conversazione", "(3 min)")
        assertEquals(PartTypeLabelResolver.ResolveOutcome.Mapped("INIZIARE_CONVERSAZIONE"), r)
    }

    @Test fun `coltivare l interesse with apostrophe variant`() {
        val r = PartTypeLabelResolver.resolve("5. Coltivare l'interesse", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.Mapped("COLTIVARE_INTERESSE"), r)
    }

    @Test fun `fare discepoli`() {
        val r = PartTypeLabelResolver.resolve("6. Fare discepoli", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.Mapped("FARE_DISCEPOLI"), r)
    }

    @Test fun `discorso plain`() {
        val r = PartTypeLabelResolver.resolve("6. Discorso", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.Mapped("DISCORSO"), r)
    }

    @Test fun `spiegare with discorso detail maps to discorso variant`() {
        val r = PartTypeLabelResolver.resolve(
            "6. Spiegare quello in cui si crede",
            "(5 min) Discorso. Tema: ...",
        )
        assertEquals(
            PartTypeLabelResolver.ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO"),
            r,
        )
    }

    @Test fun `spiegare with dimostrazione detail maps to base code`() {
        val r = PartTypeLabelResolver.resolve(
            "6. Spiegare quello in cui si crede",
            "(4 min) Dimostrazione.",
        )
        assertEquals(
            PartTypeLabelResolver.ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE"),
            r,
        )
    }

    @Test fun `spiegare with missing detail is Unknown`() {
        val r = PartTypeLabelResolver.resolve("6. Spiegare quello in cui si crede", null)
        assertIs<PartTypeLabelResolver.ResolveOutcome.Unknown>(r)
        Unit
    }

    @Test fun `cantico is non-efficaci and silently skipped`() {
        val r = PartTypeLabelResolver.resolve("Cantico 153 e preghiera", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `commenti conclusivi is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("Commenti conclusivi (3 min) | Cantico 3", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `tesori heading is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("1. Impariamo dall'esempio di Sebna", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `gemme spirituali is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("2. Gemme spirituali", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `studio biblico congregazione is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("9. Studio biblico di congregazione", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `totally unknown efficaci label`() {
        val r = PartTypeLabelResolver.resolve(
            "4. Prepariamo il cuore",
            "(5 min) Discorso.",
        )
        val unknown = assertIs<PartTypeLabelResolver.ResolveOutcome.Unknown>(r)
        assertEquals("prepariamo il cuore", unknown.normalizedTitle)
        Unit
    }
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.PartTypeLabelResolverTest"
```

Expected: compilation error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import java.text.Normalizer
import java.util.Locale

object PartTypeLabelResolver {

    sealed interface ResolveOutcome {
        data class Mapped(val code: String) : ResolveOutcome
        data object NotEfficaci : ResolveOutcome
        data class Unknown(val normalizedTitle: String) : ResolveOutcome
    }

    private val EFFICACI_LABELS: Map<String, String> = mapOf(
        "lettura biblica" to "LETTURA_DELLA_BIBBIA",
        "iniziare una conversazione" to "INIZIARE_CONVERSAZIONE",
        "coltivare l interesse" to "COLTIVARE_INTERESSE",
        "fare discepoli" to "FARE_DISCEPOLI",
        "discorso" to "DISCORSO",
    )

    private val NON_EFFICACI_SUBSTRINGS = listOf(
        "cantico",
        "commenti introduttivi",
        "commenti conclusivi",
        "gemme spirituali",
        "studio biblico di congregazione",
        "bisogni locali",
        "tema stagionale",
        "rapporto di servizio",
    )

    // Heuristic: Tesori della Parola di Dio and Vita cristiana talk-style parts
    // do not have a standardized title across bimesters. They are recognized
    // instead upstream by section position. This resolver is invoked only on
    // parts already scoped to the EFFICACI section by the caller. Other sections
    // are NOT passed in.

    fun resolve(rawTitle: String, detailLine: String?): ResolveOutcome {
        val normalized = normalize(stripLeadingNumber(rawTitle))
        NON_EFFICACI_SUBSTRINGS.forEach { sub ->
            if (normalized.contains(sub)) return ResolveOutcome.NotEfficaci
        }

        if (normalized == "spiegare quello in cui si crede") {
            val detailNormalized = detailLine?.let(::normalize).orEmpty()
            return when {
                detailNormalized.contains("dimostrazione") ->
                    ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE")
                detailNormalized.contains("discorso") ->
                    ResolveOutcome.Mapped("SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO")
                else -> ResolveOutcome.Unknown(normalized)
            }
        }

        EFFICACI_LABELS[normalized]?.let { return ResolveOutcome.Mapped(it) }
        return ResolveOutcome.Unknown(normalized)
    }

    private fun stripLeadingNumber(value: String): String =
        value.replace(Regex("^\\s*\\d+[\\.\\)]\\s*"), "")

    private fun normalize(value: String): String {
        val noAccent = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return noAccent
            .replace('’', '\'')
            .replace('\'', ' ')
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
```

Note the caller contract: the caller MUST pass only parts belonging to the
`EFFICACI NEL MINISTERO` section. A follow-up task implements that scoping in
`JwPubHtmlPartsParser` (emits `section` on each `JwPubPart`, and the orchestrator
only invokes this resolver for parts with `section == EFFICACI`).

**Wait — re-read the test fixture for "1. Impariamo dall'esempio di Sebna"**:
The test currently expects this `Tesori` heading to come back `NotEfficaci`. But
the resolver above has no entry for it. The orchestrator must filter by section
BEFORE calling this resolver. Update the test to reflect the contract:

Replace the `tesori heading is non-efficaci` test with:

```kotlin
@Test fun `caller is expected to filter by section, unscoped tesori label is Unknown`() {
    val r = PartTypeLabelResolver.resolve("1. Impariamo dall'esempio di Sebna", null)
    assertIs<PartTypeLabelResolver.ResolveOutcome.Unknown>(r)
    Unit
}
```

(Keep the explicit documented contract in the test itself.)

**Step 4: Run — expect PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.PartTypeLabelResolverTest"
```

Expected: all pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/PartTypeLabelResolver.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/PartTypeLabelResolverTest.kt
git commit -m "feat(schemas): add PartTypeLabelResolver with deterministic mapping"
```

---

## Task 6: JwPubWeekDateResolver (pure, Italian dates)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubWeekDateResolver.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubWeekDateResolverTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class JwPubWeekDateResolverTest {

    @Test fun `same month range`() {
        assertEquals(
            LocalDate.of(2026, 1, 5),
            JwPubWeekDateResolver.resolve("5-11 gennaio", 2026),
        )
    }

    @Test fun `cross-month range en dash`() {
        assertEquals(
            LocalDate.of(2026, 1, 26),
            JwPubWeekDateResolver.resolve("26 gennaio – 1º febbraio", 2026),
        )
    }

    @Test fun `cross-month range em dash`() {
        assertEquals(
            LocalDate.of(2026, 2, 23),
            JwPubWeekDateResolver.resolve("23 febbraio — 1º marzo", 2026),
        )
    }

    @Test fun `same month with ordinal mark in start`() {
        assertEquals(
            LocalDate.of(2026, 3, 1),
            JwPubWeekDateResolver.resolve("1º-7 marzo", 2026),
        )
    }

    @Test fun `cross year range returns start date in previous year`() {
        assertEquals(
            LocalDate.of(2026, 12, 28),
            JwPubWeekDateResolver.resolve("28 dicembre – 3 gennaio", 2026),
        )
    }

    @Test fun `uppercase title is normalized`() {
        assertEquals(
            LocalDate.of(2026, 1, 5),
            JwPubWeekDateResolver.resolve("5-11 GENNAIO", 2026),
        )
    }

    @Test(expected = JwPubWeekDateResolver.UnparseableDateException::class)
    fun `garbled input throws`() {
        JwPubWeekDateResolver.resolve("nonsense", 2026)
    }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubWeekDateResolverTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import java.time.LocalDate
import java.util.Locale

object JwPubWeekDateResolver {

    class UnparseableDateException(message: String) : RuntimeException(message)

    private val MONTHS = mapOf(
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4,
        "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
    )

    // Accept `-`, en-dash, em-dash as range separator, with optional
    // surrounding whitespace. "º" ordinal is ignored.
    private val RANGE_REGEX = Regex(
        "^\\s*(\\d{1,2})º?\\s*(?:([a-zA-Zàèéìòù]+)\\s*)?[\\-–—]\\s*(\\d{1,2})º?\\s*([a-zA-Zàèéìòù]+)\\s*$",
    )

    fun resolve(title: String, publicationYear: Int): LocalDate {
        val normalized = title.lowercase(Locale.ROOT).trim()
        val match = RANGE_REGEX.matchEntire(normalized)
            ?: throw UnparseableDateException("Title not in expected range format: $title")
        val startDay = match.groupValues[1].toInt()
        val rawStartMonth = match.groupValues[2]
        val endMonth = MONTHS[match.groupValues[4]]
            ?: throw UnparseableDateException("Unknown end month: ${match.groupValues[4]}")
        val startMonth = if (rawStartMonth.isBlank()) {
            endMonth  // same month range
        } else {
            MONTHS[rawStartMonth]
                ?: throw UnparseableDateException("Unknown start month: $rawStartMonth")
        }
        val startYear = if (startMonth == 12 && endMonth == 1) {
            publicationYear
        } else {
            publicationYear
        }
        return LocalDate.of(startYear, startMonth, startDay)
    }
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubWeekDateResolverTest"
```

Expected: all pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubWeekDateResolver.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubWeekDateResolverTest.kt
git commit -m "feat(schemas): add JwPubWeekDateResolver with Italian month parsing"
```

---

## Task 7: JwPubContentDecryptor — deriveKeyIv (pure vectors)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptor.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptorTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import kotlin.test.Test
import kotlin.test.assertEquals

class JwPubContentDecryptorTest {

    @Test
    fun `deriveKeyIv produces pinned vectors for 4_mwb26_2026_20260100`() {
        val pubCard = PubCard(
            mepsLanguageIndex = 4,
            symbol = "mwb26",
            year = 2026,
            issueTag = "20260100",
        )
        val keyIv = JwPubContentDecryptor().deriveKeyIv(pubCard)
        // Pinned from empirical verification on mwb_I_202601.jwpub:
        assertEquals("ae2d85ab379f450f6fcfec6480a85f41", keyIv.key.toHex())
        assertEquals("f6dce80a87be02f5e24183a2378b5e3b", keyIv.iv.toHex())
    }

    @Test
    fun `pubCard string format is lang_symbol_year_issueTag`() {
        val pubCard = PubCard(4, "mwb26", 2026, "20260100")
        assertEquals("4_mwb26_2026_20260100", pubCard.toPubCardString())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubContentDecryptorTest"
```

Expected: compile error.

**Step 3: Implement (only deriveKeyIv, decrypt in next task)**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import java.security.MessageDigest

data class PubCard(
    val mepsLanguageIndex: Int,
    val symbol: String,
    val year: Int,
    val issueTag: String,
) {
    fun toPubCardString(): String = "${mepsLanguageIndex}_${symbol}_${year}_${issueTag}"
}

data class KeyIv(val key: ByteArray, val iv: ByteArray)

class JwPubContentDecryptor {

    fun deriveKeyIv(pubCard: PubCard): KeyIv {
        val cardBytes = pubCard.toPubCardString().toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(cardBytes)
        val combined = ByteArray(hash.size)
        for (i in hash.indices) combined[i] = (hash[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        return KeyIv(key = combined.copyOfRange(0, 16), iv = combined.copyOfRange(16, 32))
    }

    companion object {
        // Public constant from sws2apps/meeting-schedules-parser (MIT).
        private val XOR_KEY: ByteArray = hexToBytes(
            "11cbb5587e32846d4c26790c633da289f66fe5842a3a585ce1bc3a294af5ada7",
        )

        private fun hexToBytes(hex: String): ByteArray {
            val out = ByteArray(hex.length / 2)
            for (i in out.indices) {
                out[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
            return out
        }
    }
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubContentDecryptorTest"
```

Expected: both pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptor.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptorTest.kt
git commit -m "feat(schemas): add JwPubContentDecryptor key+iv derivation"
```

---

## Task 8: Fixture acquisition — real .jwpub in test resources

**Files:**

- Create: `scripts/fetch-jwpub-fixture.sh`
- Create: `composeApp/src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub` (binary)

**Step 1: Write fetch script**

```bash
#!/usr/bin/env bash
# Fetch the jwpub fixture used by tests. Run manually only, result
# is committed as a fixture.
set -euo pipefail
TARGET_DIR="composeApp/src/jvmTest/resources/fixtures/jwpub"
mkdir -p "$TARGET_DIR"
API_URL="https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS"
CDN_URL=$(curl -sS "${API_URL}?output=json&pub=mwb&issue=202601&fileformat=JWPUB&alllangs=0&langwritten=I" \
    | python3 -c "import json,sys;print(json.load(sys.stdin)['files']['I']['JWPUB'][0]['file']['url'])")
curl -sS -o "$TARGET_DIR/mwb_I_202601.jwpub" "$CDN_URL"
echo "Fixture saved: $TARGET_DIR/mwb_I_202601.jwpub ($(stat -c%s "$TARGET_DIR/mwb_I_202601.jwpub") bytes)"
```

Make executable: `chmod +x scripts/fetch-jwpub-fixture.sh`.

**Step 2: Run it**

```bash
./scripts/fetch-jwpub-fixture.sh
```

Expected: file ~3.4 MB saved.

**Step 3: Verify**

```bash
unzip -l composeApp/src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub | head -5
```

Expected: shows `manifest.json` and `contents`.

**Step 4: Commit fixture**

```bash
git add scripts/fetch-jwpub-fixture.sh composeApp/src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub
git commit -m "test: add mwb_I_202601.jwpub fixture and fetch script"
```

---

## Task 9: JwPubArchiveReader (zip-in-zip)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubArchiveReader.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubArchiveReaderTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class JwPubArchiveReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fixture(): Path = Paths.get(
        "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub"
    ).toAbsolutePath()

    @Test fun `extracts inner db from real jwpub`() {
        val reader = JwPubArchiveReader()
        val dbFile = reader.extractInnerDb(fixture(), tempFolder.root.toPath())
        assertTrue(Files.exists(dbFile))
        assertTrue(dbFile.fileName.toString().endsWith(".db"))
        assertTrue(Files.size(dbFile) > 100_000L, "DB should be reasonably sized")
    }

    @Test fun `reads manifest from real jwpub`() {
        val reader = JwPubArchiveReader()
        val manifest = reader.readManifest(fixture())
        assertTrue(manifest.publication.symbol == "mwb26")
    }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubArchiveReaderTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

@Serializable
data class JwPubManifest(val name: String, val publication: JwPubManifestPublication)

@Serializable
data class JwPubManifestPublication(
    val fileName: String,
    val symbol: String,
    val uniqueEnglishSymbol: String? = null,
    val year: Int,
    val issueTagNumber: String? = null,
    val issueId: Long? = null,
)

class JwPubArchiveReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun extractInnerDb(jwpubFile: Path, destinationDir: Path): Path {
        val (contentsBytes, _) = readOuterEntries(jwpubFile)
        val contents = contentsBytes
            ?: throw IllegalStateException("'contents' entry missing in $jwpubFile")
        ZipInputStream(ByteArrayInputStream(contents)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".db")) {
                    Files.createDirectories(destinationDir)
                    val target = destinationDir.resolve(entry.name)
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING)
                    return target
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("No .db file inside contents of $jwpubFile")
    }

    fun readManifest(jwpubFile: Path): JwPubManifest {
        val (_, manifestBytes) = readOuterEntries(jwpubFile)
        val bytes = manifestBytes
            ?: throw IllegalStateException("manifest.json missing in $jwpubFile")
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    private fun readOuterEntries(jwpubFile: Path): Pair<ByteArray?, ByteArray?> {
        var contents: ByteArray? = null
        var manifest: ByteArray? = null
        ZipInputStream(Files.newInputStream(jwpubFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "contents" -> contents = zis.readAllBytes()
                    "manifest.json" -> manifest = zis.readAllBytes()
                }
                entry = zis.nextEntry
            }
        }
        return contents to manifest
    }
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubArchiveReaderTest"
```

Expected: both pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubArchiveReader.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubArchiveReaderTest.kt
git commit -m "feat(schemas): add JwPubArchiveReader zip-in-zip extractor"
```

---

## Task 10: JwPubSqliteReader (sqlite-jdbc)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSqliteReader.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSqliteReaderTest.kt`

**Step 1: Write failing tests (uses inner db extracted in test setup)**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwPubSqliteReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dbFile: java.nio.file.Path

    @BeforeTest fun setup() {
        val fixture = Paths.get(
            "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub"
        ).toAbsolutePath()
        dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
    }

    @Test fun `readPubCard returns expected meta`() {
        val pubCard = JwPubSqliteReader().readPubCard(dbFile)
        assertEquals(PubCard(4, "mwb26", 2026, "20260100"), pubCard)
    }

    @Test fun `readWeeks returns 8 rows for Class 106`() {
        val weeks = JwPubSqliteReader().readWeeks(dbFile)
        assertEquals(8, weeks.size)
        val first = weeks.first()
        assertEquals(202026001L, first.mepsDocumentId)
        assertEquals("5-11 gennaio", first.title)
        assertEquals("ISAIA 17-20", first.subtitle)
        assertTrue(first.content.isNotEmpty(), "Encrypted content must be present")
    }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubSqliteReaderTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import java.nio.file.Path
import java.sql.DriverManager

data class JwPubWeekRow(
    val documentId: Int,
    val mepsDocumentId: Long,
    val title: String,
    val subtitle: String?,
    val content: ByteArray,
)

class JwPubSqliteReader {

    fun readPubCard(dbFile: Path): PubCard = withConnection(dbFile) { conn ->
        conn.createStatement().executeQuery(
            "SELECT MepsLanguageIndex, Symbol, Year, IssueTagNumber FROM Publication LIMIT 1",
        ).use { rs ->
            if (!rs.next()) throw IllegalStateException("Publication table empty in $dbFile")
            PubCard(
                mepsLanguageIndex = rs.getInt(1),
                symbol = rs.getString(2),
                year = rs.getInt(3),
                issueTag = rs.getString(4),
            )
        }
    }

    fun readWeeks(dbFile: Path): List<JwPubWeekRow> = withConnection(dbFile) { conn ->
        val list = mutableListOf<JwPubWeekRow>()
        conn.createStatement().executeQuery(
            "SELECT DocumentId, MepsDocumentId, Title, Subtitle, Content FROM Document " +
                "WHERE Class='106' ORDER BY DocumentId",
        ).use { rs ->
            while (rs.next()) {
                list += JwPubWeekRow(
                    documentId = rs.getInt(1),
                    mepsDocumentId = rs.getLong(2),
                    title = rs.getString(3),
                    subtitle = rs.getString(4),
                    content = rs.getBytes(5),
                )
            }
        }
        list
    }

    private fun <T> withConnection(dbFile: Path, block: (java.sql.Connection) -> T): T {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use(block)
    }
}
```

Note: use-block over `Connection` throws `Inappropriate use of use on Connection?` in some IDE setups. If that happens, open/close explicitly:

```kotlin
val conn = DriverManager.getConnection(...)
try { return block(conn) } finally { conn.close() }
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubSqliteReaderTest"
```

Expected: both pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSqliteReader.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSqliteReaderTest.kt
git commit -m "feat(schemas): add JwPubSqliteReader for Publication + Document Class='106'"
```

---

## Task 11: JwPubContentDecryptor — decryptAndInflate round-trip

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptor.kt`
- Modify: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptorTest.kt`

**Step 1: Append failing test**

```kotlin
@Test
fun `decryptAndInflate produces HTML for first week from fixture`() {
    val fixture = Paths.get("src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub")
        .toAbsolutePath()
    val dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
    val pubCard = JwPubSqliteReader().readPubCard(dbFile)
    val weeks = JwPubSqliteReader().readWeeks(dbFile)
    val decryptor = JwPubContentDecryptor()
    val keyIv = decryptor.deriveKeyIv(pubCard)

    val html = decryptor.decryptAndInflate(weeks.first().content, keyIv)

    assertTrue(html.contains("5-11 GENNAIO"), "Expected week title in html")
    assertTrue(html.contains("ISAIA 17-20"), "Expected scripture ref in html")
    assertTrue(html.contains("Lettura biblica"), "Expected a known part label")
    Unit
}
```

Add the TemporaryFolder rule + import `java.nio.file.Paths` / `assertTrue` at the
top if missing.

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubContentDecryptorTest.decryptAndInflate*"
```

Expected: compile error (`decryptAndInflate` unresolved).

**Step 3: Implement**

Add to `JwPubContentDecryptor`:

```kotlin
fun decryptAndInflate(encryptedContent: ByteArray, keyIv: KeyIv): String {
    val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
    val keySpec = javax.crypto.spec.SecretKeySpec(keyIv.key, "AES")
    val ivSpec = javax.crypto.spec.IvParameterSpec(keyIv.iv)
    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
    val compressed = cipher.doFinal(encryptedContent)

    val inflater = java.util.zip.Inflater()
    inflater.setInput(compressed)
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) break
            output.write(buffer, 0, count)
        }
    } finally {
        inflater.end()
    }
    return output.toString(Charsets.UTF_8)
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubContentDecryptorTest"
```

Expected: all pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptor.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubContentDecryptorTest.kt
git commit -m "feat(schemas): add JwPubContentDecryptor AES-CBC + zlib round-trip"
```

---

## Task 12: JwPubHtmlPartsParser (jsoup)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubHtmlPartsParser.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubHtmlPartsParserTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwPubHtmlPartsParserTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var htmlByDocId: Map<Int, String>

    @BeforeTest fun setup() {
        val fixture = Paths.get(
            "src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub",
        ).toAbsolutePath()
        val dbFile = JwPubArchiveReader().extractInnerDb(fixture, tempFolder.root.toPath())
        val pubCard = JwPubSqliteReader().readPubCard(dbFile)
        val keyIv = JwPubContentDecryptor().deriveKeyIv(pubCard)
        val weeks = JwPubSqliteReader().readWeeks(dbFile)
        htmlByDocId = weeks.associate {
            it.documentId to JwPubContentDecryptor().decryptAndInflate(it.content, keyIv)
        }
    }

    @Test fun `parses week 1 parts with expected efficaci section`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(1))
        val efficaci = parts.filter { it.section == JwPubSection.EFFICACI }
        val titles = efficaci.map { it.title }
        assertEquals(
            listOf(
                "3. Lettura biblica",
                "4. Iniziare una conversazione",
                "5. Coltivare l'interesse",
                "6. Discorso",
            ),
            titles,
        )
    }

    @Test fun `spiegare part carries detailLine for week 3`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(3))
        val spiegare = parts.single { it.title.contains("Spiegare") }
        assertTrue(spiegare.detailLine?.contains("Dimostrazione") == true,
            "Expected detailLine with 'Dimostrazione', got: ${spiegare.detailLine}")
    }

    @Test fun `tesori section heading parts are classified outside EFFICACI`() {
        val parts = JwPubHtmlPartsParser().parseParts(htmlByDocId.getValue(1))
        val tesori = parts.filter { it.section == JwPubSection.TESORI }
        assertTrue(tesori.isNotEmpty(), "Expected at least one TESORI part")
        assertTrue(tesori.all { it.section == JwPubSection.TESORI })
    }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubHtmlPartsParserTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

enum class JwPubSection { HEADER, TESORI, EFFICACI, VITA_CRISTIANA, UNKNOWN }

data class JwPubPart(
    val pid: String,
    val title: String,
    val detailLine: String?,
    val section: JwPubSection,
    val sortOrder: Int,
)

class JwPubHtmlPartsParser {

    fun parseParts(html: String): List<JwPubPart> {
        val doc = Jsoup.parse(html)
        val root = doc.body()
        val result = mutableListOf<JwPubPart>()
        var currentSection = JwPubSection.HEADER
        var sortOrder = 0

        // Depth-first traversal preserving DOM order.
        for (element in root.allElements) {
            when (element.tagName()) {
                "h2" -> currentSection = classifySection(element.text())
                "h3" -> {
                    val id = element.id()
                    if (id.startsWith("p")) {
                        val title = cleanText(element.text())
                        if (title.isNotBlank()) {
                            val detailLine = findDetailLine(element)
                            result += JwPubPart(
                                pid = id,
                                title = title,
                                detailLine = detailLine,
                                section = currentSection,
                                sortOrder = sortOrder++,
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    private fun classifySection(text: String): JwPubSection {
        val upper = text.trim().uppercase()
        return when {
            "TESORI" in upper -> JwPubSection.TESORI
            "EFFICACI" in upper -> JwPubSection.EFFICACI
            "VITA" in upper -> JwPubSection.VITA_CRISTIANA
            else -> JwPubSection.UNKNOWN
        }
    }

    private fun findDetailLine(h3: Element): String? {
        var sibling = h3.nextElementSibling()
        while (sibling != null) {
            val p = when {
                sibling.tagName() == "p" -> sibling
                sibling.tagName() == "div" -> sibling.selectFirst("p")
                else -> null
            }
            if (p != null) {
                val text = cleanText(p.text())
                if (text.isNotBlank()) return text
            }
            sibling = sibling.nextElementSibling()
        }
        return null
    }

    private fun cleanText(value: String): String =
        value.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubHtmlPartsParserTest"
```

Expected: all pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubHtmlPartsParser.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubHtmlPartsParserTest.kt
git commit -m "feat(schemas): add JwPubHtmlPartsParser with EFFICACI section awareness"
```

---

## Task 13: JwPubCache (filesystem + metadata sidecar)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubCache.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubCacheTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwPubCacheTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val info = JwPubMediaInfo(
        url = "https://cfp2.jw-cdn.org/sample.jwpub",
        checksum = "8b35423df852a905ac2feea6d758ac7b",
        modifiedDatetime = "2025-07-09 10:20:40",
        filesize = 3_569_429,
    )

    @Test fun `find returns null when cache empty`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        assertNull(cache.find("202601", "I"))
    }

    @Test fun `store writes jwpub and metadata sidecar, find returns both`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "fake".toByteArray(), info)
        assertTrue(java.nio.file.Files.exists(cached.file))

        val found = assertNotNull(cache.find("202601", "I"))
        assertEquals(info, found.meta)
    }

    @Test fun `isUpToDate true when checksum matches`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "x".toByteArray(), info)
        assertTrue(cache.isUpToDate(cached, info))
    }

    @Test fun `isUpToDate false when checksum differs`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        val cached = cache.store("202601", "I", "x".toByteArray(), info)
        val changed = info.copy(checksum = "other")
        assertFalse(cache.isUpToDate(cached, changed))
    }

    @Test fun `find tolerates missing metadata sidecar by returning null`() {
        val cache = JwPubCache(tempFolder.root.toPath())
        cache.store("202601", "I", "x".toByteArray(), info)
        java.nio.file.Files.delete(tempFolder.root.toPath().resolve("mwb_I_202601.meta.json"))
        assertNull(cache.find("202601", "I"))
    }
}
```

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubCacheTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Serializable
data class JwPubMediaInfo(
    val url: String,
    val checksum: String,
    val modifiedDatetime: String,
    val filesize: Long,
)

data class CachedJwPub(val file: Path, val meta: JwPubMediaInfo)

@Serializable
private data class CacheSidecar(
    val info: JwPubMediaInfo,
    val fetchedAtEpoch: Long,
)

class JwPubCache(
    private val cacheDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false },
) {

    fun find(issue: String, lang: String): CachedJwPub? {
        val jwpubFile = jwpubPath(issue, lang)
        val metaFile = metaPath(issue, lang)
        if (!Files.exists(jwpubFile) || !Files.exists(metaFile)) return null
        val sidecar = runCatching {
            json.decodeFromString<CacheSidecar>(Files.readString(metaFile))
        }.getOrElse { return null }
        return CachedJwPub(file = jwpubFile, meta = sidecar.info)
    }

    fun store(
        issue: String,
        lang: String,
        bytes: ByteArray,
        info: JwPubMediaInfo,
    ): CachedJwPub {
        Files.createDirectories(cacheDir)
        val jwpubFile = jwpubPath(issue, lang)
        Files.write(jwpubFile, bytes)
        val sidecar = CacheSidecar(info = info, fetchedAtEpoch = Instant.now().epochSecond)
        Files.writeString(metaPath(issue, lang), json.encodeToString(
            CacheSidecar.serializer(),
            sidecar,
        ))
        return CachedJwPub(file = jwpubFile, meta = info)
    }

    fun isUpToDate(cached: CachedJwPub?, info: JwPubMediaInfo): Boolean =
        cached != null && cached.meta.checksum == info.checksum

    private fun jwpubPath(issue: String, lang: String): Path =
        cacheDir.resolve("mwb_${lang}_${issue}.jwpub")

    private fun metaPath(issue: String, lang: String): Path =
        cacheDir.resolve("mwb_${lang}_${issue}.meta.json")
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubCacheTest"
```

Expected: 5 pass.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubCache.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubCacheTest.kt
git commit -m "feat(schemas): add JwPubCache with metadata sidecar and checksum check"
```

---

## Task 14: JwPubMediaClient (ktor mock engine)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubMediaClient.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubMediaClientTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs

class JwPubMediaClientTest {

    private val sampleJson = """
{"files":{"I":{"JWPUB":[{"title":"Edizione normale",
"file":{"url":"https://cfp2.jw-cdn.org/a/mwb_I_202601.jwpub",
"modifiedDatetime":"2025-07-09 10:20:40",
"checksum":"8b35423df852a905ac2feea6d758ac7b"},
"filesize":3569429}]}}}
""".trimIndent()

    @Test fun `parses successful response`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(sampleJson),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = JwPubMediaClient(HttpClient(engine))
        val info = client.fetchMediaLinks("mwb", "202601", "I")
        assertIs<arrow.core.Either.Right<JwPubMediaInfo?>>(info)
        val value = assertNotNull((info as arrow.core.Either.Right).value)
        assertEquals("https://cfp2.jw-cdn.org/a/mwb_I_202601.jwpub", value.url)
        assertEquals("8b35423df852a905ac2feea6d758ac7b", value.checksum)
        assertEquals(3569429L, value.filesize)
    }

    @Test fun `404 maps to Right(null)`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val client = JwPubMediaClient(HttpClient(engine))
        val info = client.fetchMediaLinks("mwb", "299901", "I")
        assertEquals(arrow.core.Either.Right(null), info)
    }

    @Test fun `500 maps to Left Network`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val client = JwPubMediaClient(HttpClient(engine))
        val result = client.fetchMediaLinks("mwb", "202601", "I")
        assertIs<arrow.core.Either.Left<org.example.project.core.domain.DomainError>>(result)
        Unit
    }
}
```

Note: `assertNotNull` and `arrow.core.Either` may need imports adjusted.

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubMediaClientTest"
```

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.domain.DomainError

class JwPubMediaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun fetchMediaLinks(
        pub: String,
        issue: String,
        lang: String,
    ): Either<DomainError, JwPubMediaInfo?> = Either.catch {
        val response = httpClient.get(baseUrl) {
            parameter("output", "json")
            parameter("pub", pub)
            parameter("issue", issue)
            parameter("fileformat", "JWPUB")
            parameter("alllangs", "0")
            parameter("langwritten", lang)
        }
        if (response.status == HttpStatusCode.NotFound) return@catch null
        if (!response.status.isSuccess()) {
            throw java.io.IOException(
                "GETPUBMEDIALINKS $issue: HTTP ${response.status.value}",
            )
        }
        val dto = json.decodeFromString<MediaLinksDto>(response.bodyAsText())
        val jwpubEntry = dto.files[lang]?.jwpub?.firstOrNull()
            ?: throw java.io.IOException("GETPUBMEDIALINKS $issue: no JWPUB entry")
        JwPubMediaInfo(
            url = jwpubEntry.file.url,
            checksum = jwpubEntry.file.checksum,
            modifiedDatetime = jwpubEntry.file.modifiedDatetime,
            filesize = jwpubEntry.filesize,
        )
    }.mapLeft { DomainError.Network(it.message ?: "Errore GETPUBMEDIALINKS") }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    @Serializable
    private data class MediaLinksDto(val files: Map<String, LangFiles> = emptyMap())

    @Serializable
    private data class LangFiles(
        @kotlinx.serialization.SerialName("JWPUB")
        val jwpub: List<JwPubEntry> = emptyList(),
    )

    @Serializable
    private data class JwPubEntry(val file: FileDto, val filesize: Long)

    @Serializable
    private data class FileDto(
        val url: String,
        val modifiedDatetime: String,
        val checksum: String,
    )
}
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubMediaClientTest"
```

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubMediaClient.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubMediaClientTest.kt
git commit -m "feat(schemas): add JwPubMediaClient ktor wrapper for GETPUBMEDIALINKS"
```

---

## Task 15: JwPubDownloader (ktor mock)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubDownloader.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubDownloaderTest.kt`

**Step 1: Write failing tests**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JwPubDownloaderTest {

    @Test fun `download returns bytes on 200`() = runBlocking {
        val expected = ByteArray(64) { it.toByte() }
        val engine = MockEngine { respond(ByteReadChannel(expected), HttpStatusCode.OK) }
        val res = JwPubDownloader(HttpClient(engine)).download("https://ex.com/mwb.jwpub")
        val right = assertIs<Either.Right<ByteArray>>(res)
        assertEquals(expected.toList(), right.value.toList())
    }

    @Test fun `500 maps to Left Network`() = runBlocking {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val res = JwPubDownloader(HttpClient(engine)).download("https://ex.com/mwb.jwpub")
        assertIs<Either.Left<org.example.project.core.domain.DomainError>>(res)
        Unit
    }
}
```

**Step 2: Run — FAIL**

**Step 3: Implement**

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import org.example.project.core.domain.DomainError

class JwPubDownloader(private val httpClient: HttpClient) {

    suspend fun download(url: String): Either<DomainError, ByteArray> = Either.catch {
        val response = httpClient.get(url)
        if (response.status.value !in 200..299) {
            throw java.io.IOException("Download $url: HTTP ${response.status.value}")
        }
        response.readBytes()
    }.mapLeft { DomainError.Network(it.message ?: "Errore download jwpub") }
}
```

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubDownloader.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubDownloaderTest.kt
git commit -m "feat(schemas): add JwPubDownloader ktor wrapper"
```

---

## Task 16: Extend RemoteSchemaCatalog domain

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/SchemaCatalogRemoteSource.kt`

**Step 1: Extend data classes**

```kotlin
data class SkippedPart(
    val weekStartDate: String,        // ISO date
    val mepsDocumentId: Long,
    val label: String,
    val detailLine: String?,
)

data class RemoteSchemaCatalog(
    val version: String?,
    val partTypes: List<PartType>,
    val weeks: List<RemoteWeekSchemaTemplate>,
    val skippedUnknownParts: List<SkippedPart> = emptyList(),
    val downloadedIssues: List<String> = emptyList(),
)
```

Default values keep binary/source compat with existing
`GitHubSchemaCatalogDataSource` (removed in a later task but retains
compilation until then).

**Step 2: Compile**

```bash
./gradlew :composeApp:compileKotlinJvm
```

Expected: OK (existing code unaffected because new fields have defaults).

**Step 3: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/SchemaCatalogRemoteSource.kt
git commit -m "feat(schemas): extend RemoteSchemaCatalog with skippedUnknownParts and downloadedIssues"
```

---

## Task 17: JwPubSchemaCatalogDataSource (orchestrator)

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSchemaCatalogDataSource.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSchemaCatalogDataSourceTest.kt`

**Step 1: Write failing integration test**

The test assembles all real collaborators plus `MockEngine` for HTTP:

```kotlin
package org.example.project.feature.schemas.infrastructure.jwpub

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JwPubSchemaCatalogDataSourceTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun `fetchCatalog downloads, parses and returns weeks with downloadedIssues`() = runBlocking {
        val fixtureBytes = Files.readAllBytes(
            Paths.get("src/jvmTest/resources/fixtures/jwpub/mwb_I_202601.jwpub")
                .toAbsolutePath(),
        )
        val sampleJson = """
{"files":{"I":{"JWPUB":[{"file":{
  "url":"https://cfp2.jw-cdn.org/mwb_I_202601.jwpub",
  "modifiedDatetime":"2025-07-09 10:20:40",
  "checksum":"fakechecksum202601"},
  "filesize":${fixtureBytes.size}}]}}}
""".trimIndent()

        val engine = MockEngine { request ->
            when {
                "GETPUBMEDIALINKS" in request.url.fullPath && "issue=202601" in request.url.fullPath ->
                    respond(ByteReadChannel(sampleJson), HttpStatusCode.OK,
                        headersOf("Content-Type", "application/json"))
                "GETPUBMEDIALINKS" in request.url.fullPath ->
                    respondError(HttpStatusCode.NotFound) // no further issues
                request.url.toString().endsWith("mwb_I_202601.jwpub") ->
                    respond(ByteReadChannel(fixtureBytes), HttpStatusCode.OK)
                else -> respondError(HttpStatusCode.InternalServerError)
            }
        }
        val source = JwPubSchemaCatalogDataSource(
            httpClient = HttpClient(engine),
            cacheDir = tempFolder.root.toPath(),
            clock = Clock.fixed(Instant.parse("2026-01-05T09:00:00Z"), ZoneOffset.UTC),
            staticPartTypes = StaticPartTypesFixture.all(),  // helper defined below
        )

        val result = source.fetchCatalog()
        val catalog = assertIs<Either.Right<org.example.project.feature.schemas.application.RemoteSchemaCatalog>>(result).value

        assertTrue(catalog.weeks.size >= 8, "Expected at least one bimester of weeks")
        assertTrue(catalog.downloadedIssues.contains("202601"))
        assertTrue(catalog.weeks.first().partTypeCodes.isNotEmpty())
        assertTrue(catalog.partTypes.any { it.code == "LETTURA_DELLA_BIBBIA" })
    }
}
```

Helper: `StaticPartTypesFixture` — small util in the same test package that
returns the 7 known PartTypes. (Mirrors what the production source will
supply.)

**Step 2: Run — FAIL**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubSchemaCatalogDataSourceTest"
```

Expected: compile error.

**Step 3: Implement**

```kotlin
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
import org.example.project.feature.weeklyparts.domain.PartType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset

class JwPubSchemaCatalogDataSource(
    httpClient: HttpClient,
    cacheDir: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val staticPartTypes: List<PartType>,
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
    private val cacheDirResolved = cacheDir

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
                if (!hadAnyFascicolo) continue  // probe forward: not yet available
                break  // after ≥1 success, a 404 terminates the sequence
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

            parseFascicolo(cachedJwPub.file).let { parsed ->
                allWeeks += parsed.weeks
                skippedUnknownParts += parsed.skippedUnknown
                latestVersion = parsed.version ?: latestVersion
            }
        }

        RemoteSchemaCatalog(
            version = latestVersion,
            partTypes = staticPartTypes,
            weeks = allWeeks.sortedBy { it.weekStartDate },
            skippedUnknownParts = skippedUnknownParts,
            downloadedIssues = downloadedIssues,
        )
    }

    private fun parseFascicolo(jwpubFile: Path): FascicoloParse {
        val manifest = archiveReader.readManifest(jwpubFile)
        val tmpDir = Files.createTempDirectory(cacheDirResolved, "jwpub-")
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
```

**Step 4: Run — PASS**

```bash
./gradlew :composeApp:jvmTest --tests "*.JwPubSchemaCatalogDataSourceTest"
```

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSchemaCatalogDataSource.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/JwPubSchemaCatalogDataSourceTest.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/StaticPartTypesFixture.kt
git commit -m "feat(schemas): add JwPubSchemaCatalogDataSource orchestrator"
```

---

## Task 18: Extend AggiornaSchemiResult and propagate

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCase.kt`
- Modify: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/AggiornaSchemiUseCaseTest.kt`

**Step 1: Extend result**

```kotlin
data class AggiornaSchemiResult(
    val version: String?,
    val partTypesImported: Int,
    val weekTemplatesImported: Int,
    val eligibilityAnomalies: Int,
    val skippedUnknownParts: List<SkippedPart> = emptyList(),
    val downloadedIssues: List<String> = emptyList(),
)
```

Import `SkippedPart` from the same package.

**Step 2: Update use case**

In the `AggiornaSchemiResult(...)` constructor call at the bottom of `invoke()`:

```kotlin
AggiornaSchemiResult(
    version = catalog.version,
    partTypesImported = catalog.partTypes.size,
    weekTemplatesImported = catalog.weeks.size,
    eligibilityAnomalies = eligibilityCleanupCandidates.size,
    skippedUnknownParts = catalog.skippedUnknownParts,
    downloadedIssues = catalog.downloadedIssues,
)
```

**Step 3: Update existing test**

In `AggiornaSchemiUseCaseTest`, for each fake catalog instantiation, ensure
compile still works with defaults. Add a new test:

```kotlin
@Test fun `propagates skippedUnknownParts and downloadedIssues from source`() = runBlocking {
    val catalog = RemoteSchemaCatalog(
        version = "v1",
        partTypes = /* minimal list */,
        weeks = emptyList(),
        skippedUnknownParts = listOf(SkippedPart("2026-03-02", 123L, "X", null)),
        downloadedIssues = listOf("202603"),
    )
    val source = object : SchemaCatalogRemoteSource {
        override suspend fun fetchCatalog() = Either.Right(catalog)
    }
    val useCase = buildUseCase(source)
    val result = assertIs<Either.Right<AggiornaSchemiResult>>(useCase()).value
    assertEquals(1, result.skippedUnknownParts.size)
    assertEquals(listOf("202603"), result.downloadedIssues)
    Unit
}
```

**Step 4: Run tests**

```bash
./gradlew :composeApp:jvmTest --tests "*.AggiornaSchemiUseCaseTest"
```

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCase.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/AggiornaSchemiUseCaseTest.kt
git commit -m "feat(schemas): propagate skippedUnknownParts and downloadedIssues through use case"
```

---

## Task 19: ViewModel — force dialog open when there's something to report

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt`
- Create: `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/SchemaManagementViewModelTest.kt`
  (if absent)

**Step 1: Extend UI state**

```kotlin
internal data class SchemaManagementUiState(
    val today: LocalDate = LocalDate.now(),
    val isRefreshingSchemas: Boolean = false,
    val isRefreshingProgramFromSchemas: Boolean = false,
    val impactedProgramIds: Set<ProgramMonthId> = emptySet(),
    val notice: FeedbackBannerModel? = null,
    val pendingRefreshPreview: SchemaRefreshPreview? = null,
    val pendingRefreshProgramId: ProgramMonthId? = null,
    val pendingUnknownParts: List<SkippedPart> = emptyList(),          // NEW
    val pendingDownloadedIssues: List<String> = emptyList(),           // NEW
    val showRefreshResultDialog: Boolean = false,                      // NEW
)
```

**Step 2: Modify `refreshSchemasAndProgram()`**

After obtaining the successful result, capture the new fields:

```kotlin
_state.update { state ->
    state.copy(
        isRefreshingSchemas = false,
        impactedProgramIds = impactedProgramIds,
        notice = FeedbackBannerModel(
            buildSchemaUpdateNotice(result),
            FeedbackBannerKind.SUCCESS,
        ),
        pendingUnknownParts = result.skippedUnknownParts,
        pendingDownloadedIssues = result.downloadedIssues,
    )
}
```

**Step 3: Modify `requestProgramRefreshPreview()` dialog trigger**

Change:

```kotlin
if (!preview.hasEffectiveChanges()) {
    onComplete()
}
```

to:

```kotlin
val hasReport = _state.value.pendingUnknownParts.isNotEmpty() ||
    _state.value.pendingDownloadedIssues.isNotEmpty()
if (!preview.hasEffectiveChanges() && !hasReport) {
    onComplete()
    return@Right
}
pendingOnComplete = onComplete
_state.update {
    it.copy(
        pendingRefreshPreview = if (preview.hasEffectiveChanges()) preview else null,
        pendingRefreshProgramId = programId,
        showRefreshResultDialog = true,
    )
}
```

Also, when `selectedProgramId == null` in `refreshSchemasAndProgram`, if there
is a report (skipped / downloaded), force the dialog:

```kotlin
if (selectedProgramId != null) {
    requestProgramRefreshPreview(selectedProgramId, onProgramRefreshComplete)
} else if (result.skippedUnknownParts.isNotEmpty() || result.downloadedIssues.isNotEmpty()) {
    pendingOnComplete = onProgramRefreshComplete
    _state.update { it.copy(showRefreshResultDialog = true) }
} else {
    onProgramRefreshComplete()
}
```

**Step 4: Extend dismiss methods**

```kotlin
fun dismissRefreshResultDialog() {
    val onComplete = pendingOnComplete ?: {}
    pendingOnComplete = null
    _state.update {
        it.copy(
            pendingRefreshPreview = null,
            pendingRefreshProgramId = null,
            pendingUnknownParts = emptyList(),
            pendingDownloadedIssues = emptyList(),
            showRefreshResultDialog = false,
        )
    }
    onComplete()
}
```

Have `confirmProgramRefreshAll` / `confirmProgramRefreshOnlyUnassigned` /
`dismissProgramRefreshPreview` also clear `showRefreshResultDialog` +
`pendingUnknownParts` + `pendingDownloadedIssues`.

**Step 5: Write tests**

```kotlin
// (abridged; produce a ViewModel test covering):
// - result with skippedUnknownParts + no program selected → showRefreshResultDialog=true
// - result with no skipped + no program selected → showRefreshResultDialog=false, banner only
// - preview with effectiveChanges → showRefreshResultDialog=true
// - preview without effectiveChanges + skippedUnknownParts → dialog still opens
```

**Step 6: Run and commit**

```bash
./gradlew :composeApp:jvmTest --tests "*.SchemaManagementViewModelTest"
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaManagementViewModel.kt \
        composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/SchemaManagementViewModelTest.kt
git commit -m "feat(ui): force schema refresh dialog open when unknown parts or downloads to report"
```

---

## Task 20: SchemaRefreshResultDialog Compose component

**Files:**

- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaRefreshResultDialog.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`

**Step 1: Implement the composable**

```kotlin
@Composable
internal fun SchemaRefreshResultDialog(
    downloadedIssues: List<String>,
    pendingRefreshPreview: SchemaRefreshPreview?,
    unknownParts: List<SkippedPart>,
    onConfirmAll: () -> Unit,
    onConfirmOnlyUnassigned: () -> Unit,
    onDismiss: () -> Unit,
) { /* ... */ }
```

Design rules (from design doc + ui-ux-pro-max best practices used earlier):

- One primary CTA (`Aggiorna programma con tutte le modifiche`) visually
  prominent, secondary `Solo parti non assegnate`, destructive-adjacent
  `Chiudi senza aggiornare`.
- Sections only shown when populated (progressive disclosure).
- Unknown parts listed as `label - settimana - detailLine`. Use warning
  color (`MaterialTheme.colorScheme.error` or amber sketch palette).
- `cursorColor` on any text field (memory note).
- Light and dark variants — use `AppTheme` / `WorkspaceSketchPalette`.

**Step 2: Wire in `ProgramWorkspaceScreen.kt`**

Add near the existing dialog trigger (line 279):

```kotlin
if (schemaState.showRefreshResultDialog) {
    SchemaRefreshResultDialog(
        downloadedIssues = schemaState.pendingDownloadedIssues,
        pendingRefreshPreview = schemaState.pendingRefreshPreview,
        unknownParts = schemaState.pendingUnknownParts,
        onConfirmAll = { schemaVM.confirmProgramRefreshAll() },
        onConfirmOnlyUnassigned = { schemaVM.confirmProgramRefreshOnlyUnassigned() },
        onDismiss = { schemaVM.dismissRefreshResultDialog() },
    )
}
```

Remove or keep the original `pendingRefreshPreview?.let` block depending on
whether the new dialog fully supersedes it. Recommendation: **remove** the
old block — `SchemaRefreshResultDialog` replaces `SchemaRefreshConfirmDialog`
for all cases.

**Step 3: Build-only check (user runs the visual check)**

Run (Claude / subagent MUST NOT launch the app):

```bash
./gradlew :composeApp:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL. The user will launch `./gradlew :composeApp:run`
manually to verify the dialog renders correctly (light + dark).

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/SchemaRefreshResultDialog.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt
git commit -m "feat(ui): add SchemaRefreshResultDialog with downloaded issues and unknown parts"
```

---

## Task 21: DI swap in SchemasModule

**Files:**

- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/di/SchemasModule.kt`

**Step 1: Replace GitHub source with JwPub source**

```kotlin
val schemasModule = module {
    single<SchemaTemplateStore> { SqlDelightSchemaTemplateStore(get()) }
    single<SchemaUpdateAnomalyStore> { SqlDelightSchemaUpdateAnomalyStore(get()) }
    single<SchemaCatalogRemoteSource> {
        JwPubSchemaCatalogDataSource(
            httpClient = get(),
            cacheDir = get<AppPaths>().jwpubCacheDir,
            staticPartTypes = StaticMeetingWorkbookPartTypes.all(),
        )
    }
    factory { AggiornaSchemiUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { ArchivaAnomalieSchemaUseCase(get(), get()) }
    factory { CaricaCatalogoSchemiSettimanaliUseCase(get(), get()) }
}
```

**Step 2: Create `StaticMeetingWorkbookPartTypes`**

```kotlin
// composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/StaticMeetingWorkbookPartTypes.kt
object StaticMeetingWorkbookPartTypes {
    fun all(): List<PartType> = listOf(
        PartType(code = "LETTURA_DELLA_BIBBIA", label = "Lettura Biblica", peopleCount = 1,
            sexRule = SexRule.UOMO, fixed = true, /* id / order fields as per existing model */),
        // + the other 6 codes, values per seed/migration-1
    )
}
```

Copy exact values from `wintmi-seed.json` and migration `1.sqm`.

**Step 3: Compile and run existing tests**

```bash
./gradlew :composeApp:jvmTest
```

Expected: all pass.

**Step 4: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/di/SchemasModule.kt \
        composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/jwpub/StaticMeetingWorkbookPartTypes.kt
git commit -m "feat(schemas): switch DI to JwPubSchemaCatalogDataSource"
```

---

## Task 22: Remove GitHubSchemaCatalogDataSource and RemoteConfig

**Files:**

- Delete: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/GitHubSchemaCatalogDataSource.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/config/RemoteConfig.kt`
  (remove `SCHEMAS_CATALOG_URL` constant).
- Search + update: any remaining references (`grep -rn SCHEMAS_CATALOG_URL
  composeApp/src`).

**Step 1: Delete file**

```bash
git rm composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/GitHubSchemaCatalogDataSource.kt
```

**Step 2: Remove constant from RemoteConfig**

Edit `RemoteConfig.kt`:

- If the file becomes empty, delete it.
- Otherwise remove just the one constant and adjust imports.

**Step 3: Grep for lingering references**

```bash
grep -rn "GitHubSchemaCatalogDataSource\|SCHEMAS_CATALOG_URL" composeApp/src
```

Expected: only in the CLI `GenerateWolEfficaciCatalog.kt` (kept as a
standalone tool — do not remove). Update if the CLI imports
`RemoteConfig.SCHEMAS_CATALOG_URL`: either hard-code the URL inside the CLI
or leave the old constant repatriated there as a private const. Decision:
keep CLI self-contained with a private constant.

**Step 4: Build and test**

```bash
./gradlew :composeApp:jvmTest
```

**Step 5: Commit**

```bash
git add -A composeApp/
git commit -m "refactor(schemas): remove GitHub-based catalog data source"
```

---

## Task 23: Golden path manual verification

**Important:** All steps in this task are performed **manually by the user**,
not by Claude or any subagent. Claude / subagents MUST NOT execute
`:composeApp:run` or run the packaged jar. Build, test, and package
commands are allowed; launching the app is not.

**Steps (user-executed):**

1. Start the app in dev:
   ```bash
   ./gradlew :composeApp:run
   ```
2. Click `Aggiorna catalogo`. Verify:
   - Logs show `downloading mwb_I_<issue>` for each issue.
   - Files created under
     `~/.ScuolaDiMinisterData/cache/GuidaAdunanza/` (or
     `LOCALAPPDATA/ScuolaDiMinisterData/cache/GuidaAdunanza/` on Windows).
   - Dialog renders with `Fascicoli scaricati` section.
3. Click `Aggiorna catalogo` again. Verify:
   - Logs show `cached (checksum match)` for each issue.
   - Banner says "catalogo già aggiornato", dialog not shown.
4. Remove one `.jwpub` manually. Click `Aggiorna catalogo`. Verify:
   - Only that issue is re-downloaded.
5. Screenshot light + dark using the documented xvfb + xwd technique.

**Commit screenshots to `docs/plans/screenshots/jwpub-catalog-update/`:**

```bash
mkdir -p docs/plans/screenshots/jwpub-catalog-update
# save PNGs here
git add docs/plans/screenshots/jwpub-catalog-update
git commit -m "docs: add golden-path screenshots for jwpub catalog update"
```

---

## Completion

When all tasks complete:

- `./gradlew :composeApp:jvmTest koverHtmlReport` — verify coverage has not
  regressed (new modules should be >70% coverage).
- `./gradlew clean && ./gradlew :composeApp:packageUberJarForCurrentOS` —
  confirm packaging works.
- Tag implementation: `git tag jwpub-catalog-update-v1`.
- Invoke `finishing-a-development-branch` skill to decide merge/PR next step.
