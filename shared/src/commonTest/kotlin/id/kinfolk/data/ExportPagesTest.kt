package id.kinfolk.data

import id.kinfolk.data.ExportSection.allergies
import id.kinfolk.data.ExportSection.docs
import id.kinfolk.data.ExportSection.history
import id.kinfolk.data.ExportSection.meds
import id.kinfolk.data.ExportSection.trends
import id.kinfolk.data.ExportSection.visits
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportPagesTest {
    private val full = ExportContent(
        conditions = "Stroke iskemik, April 2026.", allergies = "Penisilin", emergencyContacts = 1,
        medications = 3, checkIns = 30, visitNotes = 5, documentPages = listOf(1, 3),
    )

    @Test
    fun `each section gets its own pages, like v3's sample`() =
        assertEquals(mapOf(history to 1, meds to 1, trends to 1, visits to 3, allergies to 1, docs to 4), full.pages())

    @Test
    fun `Medications take a page per 12`() {
        assertEquals(1, full.copy(medications = 12).pages()[meds])
        assertEquals(2, full.copy(medications = 13).pages()[meds])
    }

    @Test
    fun `a page per Visit Note, the latest 3`() {
        assertEquals(1, full.copy(visitNotes = 1).pages()[visits])
        assertEquals(3, full.copy(visitNotes = 3).pages()[visits])
    }

    @Test
    fun `a section with nothing in it has no pages`() {
        val empty = ExportContent("", " ", 0, 0, 0, 0, emptyList())
        assertEquals(ExportSection.entries.associateWith { 0 }, empty.pages())
        assertEquals(1, empty.copy(emergencyContacts = 1).pages()[allergies]) // contacts alone still make the page
    }

    @Test
    fun `the total counts ticked sections only`() {
        assertEquals(4, full.total(setOf(history, visits)))
        assertEquals(0, full.total(emptySet()))
    }
}
