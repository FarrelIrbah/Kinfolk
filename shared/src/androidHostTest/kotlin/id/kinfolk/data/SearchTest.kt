package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {
    init { Providers }

    @Test
    fun `finds word prefixes in v3's order of kinds, and never what the Member can't see`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val dewi = signedInSibling(sri, circle)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        val clop = sri.addMedication(MedicationDraft(circle, tukiman.id, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        sri.uploadDocument(circle, tukiman.id, "Resep clopidogrel", "PDF", byteArrayOf(1), legal = false)
        sri.uploadDocument(circle, tukiman.id, "Surat kuasa clopidogrel", "PDF", byteArrayOf(1), legal = true)
        val rao = sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Neurolog", "+6281234567890", ContactGroup.Medical))
        val task = sri.addTask(tukiman, "Ambil isi ulang clopidogrel", sri.me(), LocalDate(2026, 10, 6))
        val (power, recipe) = sri.documents(circle)

        val uploads = sri.timeline(circle).filter { it.kind == TimelineEntry.Kind.document }.map { it.at }
        assertEquals(
            listOf(
                Hit.Kind.medication to clop.id, Hit.Kind.document to power.id, Hit.Kind.document to recipe.id,
                Hit.Kind.timeline to uploads[0], Hit.Kind.timeline to uploads[1], Hit.Kind.task to task.id,
            ),
            dewi.search(circle, "CLOP").map { it.kind to (it.at ?: it.id) },
        )
        assertEquals(listOf(rao.id), dewi.search(circle, "dr. ra").map { it.id })
        assertEquals(listOf(task.id), dewi.search(circle, "sri isi").map { it.id }) // the owner's name too
        assertTrue(dewi.search(circle, "c").isEmpty()) // 2 characters at least
        assertTrue(dewi.search(circle, "idogrel").isEmpty()) // prefixes, not any substring
        val warfarin = sri.addMedication(MedicationDraft(circle, tukiman.id, "Warfarin", "2.5 mg", "malam", LocalTime(21, 0)))
        assertEquals(listOf(warfarin.id), dewi.search(circle, "2.5").map { it.id }) // numbers stay whole

        sri.setHidden(tukiman.id, dewi.me(), DataCategory.medications, hidden = true)
        sri.setHidden(tukiman.id, dewi.me(), DataCategory.wishes, hidden = true)
        assertEquals(listOf(recipe.id, uploads[1], task.id), dewi.search(circle, "clop").map { it.at ?: it.id })
        assertTrue(rudi.search(circle, "clop").isEmpty())
    }

    @Test
    fun `at most 14`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        repeat(15) { sri.addTask(tukiman, "Telepon apotek $it", sri.me(), LocalDate(2026, 10, 6)) }
        assertEquals(14, sri.search(circle, "apotek").size)
    }
}
