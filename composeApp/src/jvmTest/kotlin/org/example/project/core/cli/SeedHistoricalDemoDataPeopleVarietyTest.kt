package org.example.project.core.cli

import org.example.project.feature.weeklyparts.domain.SexRule
import kotlin.test.Test
import kotlin.test.assertTrue

class SeedHistoricalDemoDataPeopleVarietyTest {
    @Test
    fun `seedPeople generates many and varied proclaimers`() {
        val people = invokeSeedPeople()

        assertTrue(people.size >= 60, "Attesi almeno 60 proclamatori seed, trovati ${people.size}")

        val maleCount = people.count { readString(it, "getSex") == "M" }
        val femaleCount = people.count { readString(it, "getSex") == "F" }
        val inactiveCount = people.count { !readBoolean(it, "getActive") }
        val suspendedCount = people.count { readBoolean(it, "getSuspended") }
        val canAssistTrueCount = people.count { readBoolean(it, "getCanAssist") }
        val canAssistFalseCount = people.count { !readBoolean(it, "getCanAssist") }

        assertTrue(maleCount >= 20, "Attesi almeno 20 uomini, trovati $maleCount")
        assertTrue(femaleCount >= 20, "Attese almeno 20 donne, trovate $femaleCount")
        assertTrue(inactiveCount >= 3, "Attesi almeno 3 inattivi, trovati $inactiveCount")
        assertTrue(suspendedCount >= 3, "Attesi almeno 3 sospesi, trovati $suspendedCount")
        assertTrue(canAssistTrueCount >= 20, "Attesi almeno 20 con puoAssistere=true, trovati $canAssistTrueCount")
        assertTrue(canAssistFalseCount >= 20, "Attesi almeno 20 con puoAssistere=false, trovati $canAssistFalseCount")
    }

    @Test
    fun `seed canLead grants broad eligibility for compatible active proclaimers`() {
        val people = invokeSeedPeople()
        val partRules = listOf(
            SexRule.STESSO_SESSO,
            SexRule.UOMO,
            SexRule.STESSO_SESSO,
            SexRule.STESSO_SESSO,
            SexRule.UOMO,
            SexRule.STESSO_SESSO,
            SexRule.STESSO_SESSO,
            SexRule.STESSO_SESSO,
            SexRule.UOMO,
            SexRule.STESSO_SESSO,
            SexRule.STESSO_SESSO,
            SexRule.STESSO_SESSO,
        )

        var compatibleSlots = 0
        var canLeadSlots = 0

        people.forEachIndexed { personIndex, person ->
            val active = readBoolean(person, "getActive")
            val suspended = readBoolean(person, "getSuspended")
            val sex = readString(person, "getSex")
            val canAssist = readBoolean(person, "getCanAssist")
            partRules.forEachIndexed { partIndex, sexRule ->
                val compatible = active && !suspended && (sexRule != SexRule.UOMO || sex == "M")
                if (compatible) {
                    compatibleSlots += 1
                    if (shouldSeedCanLead(personIndex, partIndex, active, suspended, sex, canAssist, sexRule)) {
                        canLeadSlots += 1
                    }
                }
            }
        }

        val leadRatio = canLeadSlots.toDouble() / compatibleSlots.toDouble()
        assertTrue(
            leadRatio >= 0.8,
            "Attesa una quota idoneità canLead >= 80%, trovata ${(leadRatio * 100).toInt()}%",
        )
    }

    private fun invokeSeedPeople(): List<Any> {
        val fileClass = Class.forName("org.example.project.core.cli.SeedHistoricalDemoDataKt")
        val method = fileClass.getDeclaredMethod("seedPeople")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null) as List<Any>
    }

    private fun readString(target: Any, getterName: String): String {
        return target.javaClass.getMethod(getterName).invoke(target) as String
    }

    private fun readBoolean(target: Any, getterName: String): Boolean {
        return target.javaClass.getMethod(getterName).invoke(target) as Boolean
    }
}
