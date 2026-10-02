package id.kinfolk.ui.search

import id.kinfolk.data.CareContact
import id.kinfolk.data.ContactGroup
import id.kinfolk.data.Document
import id.kinfolk.data.Medication
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SuggestionsTest {
    private fun med(name: String, active: Boolean = true, schedule: String = "pagi", thinner: Boolean = false) =
        Medication("id", "c", "r", name, "75 mg", schedule, LocalTime(7, 0), active, bloodThinner = thinner)

    @Test
    fun `one of each kind, skipping what's missing`() {
        val doc = Document("d", "MRI otak", "PDF", false, 1, "p", "u", Instant.fromEpochSeconds(0))
        val rao = CareContact("k", "c", "Dr. Rao", "Neurolog", "+62", ContactGroup.Medical, false)
        assertEquals(
            listOf("clopidogrel", "MRI", "Budi", "Dr. Rao"),
            suggestions(listOf(med("Sertraline", active = false), med("Clopidogrel")), listOf(doc), listOf("Budi", "Dewi"), listOf(rao)),
        )
        assertEquals(listOf("Budi"), suggestions(emptyList(), emptyList(), listOf("Budi"), emptyList()))
    }

    @Test
    fun `medicine sub`() {
        assertEquals("Pengencer darah · pagi", medSub(med("Clopidogrel", thinner = true), "Pengencer darah"))
        assertEquals("Pengencer darah", medSub(med("Clopidogrel", schedule = "", thinner = true), "Pengencer darah"))
        assertEquals("pagi", medSub(med("Amlodipine"), "Pengencer darah"))
    }
}
