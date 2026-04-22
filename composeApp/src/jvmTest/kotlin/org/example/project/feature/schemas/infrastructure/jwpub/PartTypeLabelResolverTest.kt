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

    @Test fun `gemme spirituali is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("2. Gemme spirituali", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `studio biblico congregazione is non-efficaci`() {
        val r = PartTypeLabelResolver.resolve("9. Studio biblico di congregazione", null)
        assertEquals(PartTypeLabelResolver.ResolveOutcome.NotEfficaci, r)
    }

    @Test fun `caller is expected to filter by section, unscoped tesori label is Unknown`() {
        val r = PartTypeLabelResolver.resolve("1. Impariamo dall'esempio di Sebna", null)
        assertIs<PartTypeLabelResolver.ResolveOutcome.Unknown>(r)
        Unit
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
