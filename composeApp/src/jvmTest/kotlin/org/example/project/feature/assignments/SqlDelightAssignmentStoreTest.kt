package org.example.project.feature.assignments

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.infrastructure.SqlDelightAssignmentStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.infrastructure.SqlDelightWeekPlanStore
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightAssignmentStoreTest {

    @Test
    fun `save propagates persistence exception without IllegalStateException wrapper`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val store = SqlDelightAssignmentStore(database)

        val assignment = Assignment(
            id = AssignmentId("assignment-missing-fk"),
            weeklyPartId = WeeklyPartId("missing-weekly-part"),
            personId = ProclamatoreId("missing-person"),
            slot = 1,
        )

        val error = assertFailsWith<Exception> {
            store.save(assignment)
        }

        assertTrue(
            error !is IllegalStateException,
            "Expected original persistence exception, got IllegalStateException wrapper: ${error::class.simpleName}",
        )
    }

    @Test
    fun `suggestedProclamatori uses absolute distance when latest assignment is in the future`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "part-type-1",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        queries.insertWeekPlan(
            id = "week-past",
            week_start_date = "2026-02-02",
        )
        queries.insertWeekPlan(
            id = "week-future",
            week_start_date = "2026-02-16",
        )
        queries.insertWeeklyPart(
            id = "weekly-part-past",
            week_plan_id = "week-past",
            part_type_id = "part-type-1",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.insertWeeklyPart(
            id = "weekly-part-future",
            week_plan_id = "week-future",
            part_type_id = "part-type-1",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.upsertAssignment(
            id = "assignment-past",
            weekly_part_id = "weekly-part-past",
            person_id = "person-1",
            slot = 1,
        )
        queries.upsertAssignment(
            id = "assignment-future",
            weekly_part_id = "weekly-part-future",
            person_id = "person-1",
            slot = 1,
        )

        val store = SqlDelightAssignmentStore(database)
        val suggestion = store.suggestedProclamatori(
            partTypeId = PartTypeId("part-type-1"),
            slot = 1,
            referenceDate = LocalDate.parse("2026-02-09"),
        ).firstOrNull { it.proclamatore.id.value == "person-1" }

        assertNotNull(suggestion)
        assertEquals(1, suggestion.lastGlobalWeeks)
        assertEquals(1, suggestion.lastForPartTypeWeeks)
        assertEquals(1, suggestion.lastGlobalBeforeWeeks)
        assertEquals(1, suggestion.lastGlobalAfterWeeks)
        assertEquals(1, suggestion.lastForPartTypeBeforeWeeks)
        assertEquals(1, suggestion.lastForPartTypeAfterWeeks)
    }

    @Test
    fun `suggestedProclamatori uses absolute distance for future-only assignments`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "part-type-1",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        queries.insertWeekPlan(
            id = "week-future",
            week_start_date = "2026-02-16",
        )
        queries.insertWeeklyPart(
            id = "weekly-part-future",
            week_plan_id = "week-future",
            part_type_id = "part-type-1",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.upsertAssignment(
            id = "assignment-future",
            weekly_part_id = "weekly-part-future",
            person_id = "person-1",
            slot = 1,
        )

        val store = SqlDelightAssignmentStore(database)
        val suggestion = store.suggestedProclamatori(
            partTypeId = PartTypeId("part-type-1"),
            slot = 1,
            referenceDate = LocalDate.parse("2026-02-09"),
        ).firstOrNull { it.proclamatore.id.value == "person-1" }

        assertNotNull(suggestion)
        assertEquals(1, suggestion.lastGlobalWeeks)
        assertEquals(1, suggestion.lastForPartTypeWeeks)
        assertEquals(null, suggestion.lastGlobalBeforeWeeks)
        assertEquals(1, suggestion.lastGlobalAfterWeeks)
        assertEquals(null, suggestion.lastForPartTypeBeforeWeeks)
        assertEquals(1, suggestion.lastForPartTypeAfterWeeks)
    }

    // -------------------------------------------------------------------------
    // Test A — cross-parts check (WeekPlanAggregate.validateAssignment)
    // -------------------------------------------------------------------------
    // Il controllo "persona già assegnata nella stessa settimana" è implementato
    // in WeekPlanAggregate.validateAssignment (riga 83):
    //   assignments.any { it.personId == personId }
    // È un controllo in-memory sull'aggregato caricato da SqlDelightWeekPlanStore.
    // L'aggregato include TUTTE le assegnazioni della settimana, anche da parti diverse.
    // -------------------------------------------------------------------------

    @Test
    fun `addAssignment rileva duplicato cross-part nella stessa settimana`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "part-type-a",
            code = "LETTURA",
            label = "Lettura",
            people_count = 2,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )
        queries.upsertPartType(
            id = "part-type-b",
            code = "DISCORSO",
            label = "Discorso",
            people_count = 2,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 1,
        )

        // Week A: stessa settimana con due parti
        queries.insertWeekPlan(id = "week-a", week_start_date = "2026-03-02")
        queries.insertWeeklyPart(
            id = "part-a-1",
            week_plan_id = "week-a",
            part_type_id = "part-type-a",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.insertWeeklyPart(
            id = "part-a-2",
            week_plan_id = "week-a",
            part_type_id = "part-type-b",
            part_type_revision_id = null,
            sort_order = 1,
        )
        // Person-1 già assegnata alla parte A della settimana A
        queries.upsertAssignment(
            id = "assign-a",
            weekly_part_id = "part-a-1",
            person_id = "person-1",
            slot = 1,
        )

        // Week B: settimana diversa — person-1 NON è assegnata qui
        queries.insertWeekPlan(id = "week-b", week_start_date = "2026-03-09")
        queries.insertWeeklyPart(
            id = "part-b-1",
            week_plan_id = "week-b",
            part_type_id = "part-type-a",
            part_type_revision_id = null,
            sort_order = 0,
        )

        val weekPlanStore = SqlDelightWeekPlanStore(database)

        // Caso 1: assegnare person-1 alla PARTE B della stessa settimana A → errore PersonaGiaAssegnata
        val aggregateWeekA = weekPlanStore.loadAggregateByDate(LocalDate.parse("2026-03-02"))
        assertNotNull(aggregateWeekA)
        val crossPartAssignment = Assignment(
            id = AssignmentId("new-assign-x"),
            weeklyPartId = WeeklyPartId("part-a-2"),
            personId = ProclamatoreId("person-1"),
            slot = 1,
        )
        val resultCrossPart = aggregateWeekA.addAssignment(crossPartAssignment, personSuspended = false)
        assertTrue(resultCrossPart.isLeft(), "Dovrebbe fallire: stessa persona, stessa settimana, parte diversa")
        assertIs<DomainError.PersonaGiaAssegnata>(resultCrossPart.leftOrNull())

        // Caso 2: assegnare person-1 alla settimana B (diversa) → nessun errore
        val aggregateWeekB = weekPlanStore.loadAggregateByDate(LocalDate.parse("2026-03-09"))
        assertNotNull(aggregateWeekB)
        val otherWeekAssignment = Assignment(
            id = AssignmentId("new-assign-y"),
            weeklyPartId = WeeklyPartId("part-b-1"),
            personId = ProclamatoreId("person-1"),
            slot = 1,
        )
        val resultOtherWeek = aggregateWeekB.addAssignment(otherWeekAssignment, personSuspended = false)
        assertTrue(resultOtherWeek.isRight(), "Dovrebbe riuscire: stessa persona, settimana diversa")

        // Caso 3: persona non assegnata nella settimana A → nessun errore
        queries.upsertProclaimer(
            id = "person-2",
            first_name = "Luigi",
            last_name = "Bianchi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        val freshAggregateWeekA = weekPlanStore.loadAggregateByDate(LocalDate.parse("2026-03-02"))
        assertNotNull(freshAggregateWeekA)
        val unassignedPersonAssignment = Assignment(
            id = AssignmentId("new-assign-z"),
            weeklyPartId = WeeklyPartId("part-a-2"),
            personId = ProclamatoreId("person-2"),
            slot = 1,
        )
        val resultUnassigned = freshAggregateWeekA.addAssignment(unassignedPersonAssignment, personSuspended = false)
        assertTrue(resultUnassigned.isRight(), "Dovrebbe riuscire: persona non ancora assegnata in questa settimana")
    }

    // -------------------------------------------------------------------------
    // Test B — preloadSuggestionRanking: correttezza cache
    // -------------------------------------------------------------------------

    @Test
    fun `preloadSuggestionRanking popola globalBeforeByDate correttamente`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "person-a",
            first_name = "Anna",
            last_name = "Verdi",
            sex = "F",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertProclaimer(
            id = "person-b",
            first_name = "Carlo",
            last_name = "Neri",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "pt-1",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        // person-a assegnata in settimana 2026-02-02 (PRIMA della reference date 2026-02-09)
        queries.insertWeekPlan(id = "w1", week_start_date = "2026-02-02")
        queries.insertWeeklyPart(
            id = "wp1",
            week_plan_id = "w1",
            part_type_id = "pt-1",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.upsertAssignment(id = "a1", weekly_part_id = "wp1", person_id = "person-a", slot = 1)

        // person-a assegnata anche in settimana 2026-02-16 (DOPO la reference date 2026-02-09)
        queries.insertWeekPlan(id = "w2", week_start_date = "2026-02-16")
        queries.insertWeeklyPart(
            id = "wp2",
            week_plan_id = "w2",
            part_type_id = "pt-1",
            part_type_revision_id = null,
            sort_order = 0,
        )
        queries.upsertAssignment(id = "a2", weekly_part_id = "wp2", person_id = "person-a", slot = 1)

        // person-b: nessuna assegnazione

        val store = SqlDelightAssignmentStore(database)
        val refDate = LocalDate.parse("2026-02-09")
        val partTypeId = PartTypeId("pt-1")

        val cache = store.preloadSuggestionRanking(
            referenceDates = setOf(refDate),
            partTypeIds = setOf(partTypeId),
        )

        // globalLast: person-a ha l'ultima assegnazione globale (la più recente in assoluto = 2026-02-16)
        val globalLastA = cache.globalLast["person-a"]
        assertNotNull(globalLastA, "person-a deve avere un'ultima assegnazione globale")
        assertEquals("2026-02-16", globalLastA)

        // globalLast: person-b non ha assegnazioni
        assertNull(cache.globalLast["person-b"], "person-b non ha assegnazioni, globalLast deve essere null/assente")

        // globalBeforeByDate[refDate]: person-a ha un'assegnazione PRIMA del 2026-02-09 → 2026-02-02
        val beforeMap = cache.globalBeforeByDate[refDate]
        assertNotNull(beforeMap, "globalBeforeByDate deve avere una voce per la refDate")
        assertEquals("2026-02-02", beforeMap["person-a"],
            "L'ultima assegnazione di person-a prima del 2026-02-09 deve essere 2026-02-02")
        assertNull(beforeMap["person-b"],
            "person-b non ha assegnazioni prima della refDate")

        // globalAfterByDate[refDate]: person-a ha la prima assegnazione DOPO il 2026-02-09 → 2026-02-16
        val afterMap = cache.globalAfterByDate[refDate]
        assertNotNull(afterMap, "globalAfterByDate deve avere una voce per la refDate")
        assertEquals("2026-02-16", afterMap["person-a"],
            "La prima assegnazione di person-a dopo il 2026-02-09 deve essere 2026-02-16")
        assertNull(afterMap["person-b"],
            "person-b non ha assegnazioni dopo la refDate")
    }

    @Test
    fun `preloadSuggestionRanking costruisce cache corretta per più proclamatori e più date di riferimento`() = runBlocking {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val database = MinisteroDatabase(driver)
        val queries = database.ministeroDatabaseQueries

        queries.upsertProclaimer(
            id = "p1",
            first_name = "Mario",
            last_name = "Rossi",
            sex = "M",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertProclaimer(
            id = "p2",
            first_name = "Giulia",
            last_name = "Bianchi",
            sex = "F",
            suspended = 0,
            can_assist = 1,
        )
        queries.upsertPartType(
            id = "pt-x",
            code = "LETTURA",
            label = "Lettura",
            people_count = 1,
            sex_rule = "STESSO_SESSO",
            fixed = 0,
            sort_order = 0,
        )

        // p1: assegnato il 2026-01-05
        queries.insertWeekPlan(id = "wk-jan", week_start_date = "2026-01-05")
        queries.insertWeeklyPart(id = "wkp-jan", week_plan_id = "wk-jan", part_type_id = "pt-x", part_type_revision_id = null, sort_order = 0)
        queries.upsertAssignment(id = "ass-p1-jan", weekly_part_id = "wkp-jan", person_id = "p1", slot = 1)

        // p2: assegnata il 2026-02-02
        queries.insertWeekPlan(id = "wk-feb", week_start_date = "2026-02-02")
        queries.insertWeeklyPart(id = "wkp-feb", week_plan_id = "wk-feb", part_type_id = "pt-x", part_type_revision_id = null, sort_order = 0)
        queries.upsertAssignment(id = "ass-p2-feb", weekly_part_id = "wkp-feb", person_id = "p2", slot = 1)

        val store = SqlDelightAssignmentStore(database)
        val refDate1 = LocalDate.parse("2026-01-19")  // dopo assegnazione p1, prima di p2
        val refDate2 = LocalDate.parse("2026-02-16")  // dopo entrambi
        val partTypeId = PartTypeId("pt-x")

        val cache = store.preloadSuggestionRanking(
            referenceDates = setOf(refDate1, refDate2),
            partTypeIds = setOf(partTypeId),
        )

        // Entrambe le refDate devono avere una voce in globalBeforeByDate
        assertNotNull(cache.globalBeforeByDate[refDate1])
        assertNotNull(cache.globalBeforeByDate[refDate2])

        // refDate1 (2026-01-19): p1 ha assegnazione before (2026-01-05), p2 no
        assertEquals("2026-01-05", cache.globalBeforeByDate[refDate1]!!["p1"])
        assertNull(cache.globalBeforeByDate[refDate1]!!["p2"])

        // refDate2 (2026-02-16): entrambi hanno assegnazione before
        assertEquals("2026-01-05", cache.globalBeforeByDate[refDate2]!!["p1"])
        assertEquals("2026-02-02", cache.globalBeforeByDate[refDate2]!!["p2"])

        // globalLast: p1 → 2026-01-05, p2 → 2026-02-02
        assertEquals("2026-01-05", cache.globalLast["p1"])
        assertEquals("2026-02-02", cache.globalLast["p2"])

        // partTypeLastByType deve avere una voce per pt-x
        val ptLast = cache.partTypeLastByType[partTypeId]
        assertNotNull(ptLast)
        assertEquals("2026-01-05", ptLast["p1"])
        assertEquals("2026-02-02", ptLast["p2"])
    }
}
