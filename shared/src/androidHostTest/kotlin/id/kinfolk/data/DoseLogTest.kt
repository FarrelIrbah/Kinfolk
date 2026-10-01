package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DoseLogTest {
    init { Providers } // start the fake providers before anything is sent

    private val today = LocalDate(2026, 10, 1)

    @Test
    fun `one Dose Log per Medication per day, seen by the circle with who gave it, and untoggling removes it`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val clop = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))

        budi.giveDose(clop, today)
        sri.giveDose(clop, today) // already given: stays Budi's
        sri.giveDose(clop, LocalDate(2026, 9, 30))

        val logs = sri.doseLogs(circle, today)
        assertEquals(listOf(clop.id to budi.me()), logs.map { it.medicationId to it.givenBy })
        val entry = sri.timeline(circle).filter { it.kind == TimelineEntry.Kind.dose_given }
        assertEquals(listOf("Clopidogrel 75 mg diberikan."), entry.map { it.text }.distinct())
        assertEquals(budi.me(), entry.first { it.at == logs.single().at }.by)

        sri.takeBackDose(clop, today)
        assertTrue(budi.doseLogs(circle, today).isEmpty())
        assertEquals(1, budi.timeline(circle).count { it.kind == TimelineEntry.Kind.dose_given })
    }

    @Test
    fun `a Member who can't see Medications sees no Dose Logs, can't mark them, and nobody outside sees them`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)
        val clop = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        sri.giveDose(clop, today)
        sri.setHidden(tukiman, dewi.me(), DataCategory.medications, hidden = true)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        assertTrue(dewi.doseLogs(circle, today).isEmpty())
        assertTrue(dewi.timeline(circle).none { it.kind == TimelineEntry.Kind.dose_given })
        assertTrue(rudi.doseLogs(circle, today).isEmpty())
        assertFails { dewi.giveDose(clop, LocalDate(2026, 10, 2)) }
        assertFails { rudi.giveDose(clop, LocalDate(2026, 10, 2)) }
        dewi.takeBackDose(clop, today)
        rudi.takeBackDose(clop, today)

        assertEquals(1, sri.doseLogs(circle, today).size)
    }

    @Test
    fun `a Medication keeps its note, blood-thinner flag and refill`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val agus = signedInSibling(sri, circle).me()

        val clop = sri.addMedication(
            MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0),
                note = "Jangan berhenti tanpa Dr. Rao.", bloodThinner = true, refillOn = LocalDate(2026, 10, 5), refillBy = agus),
        )

        val read = sri.medications(circle).single()
        assertEquals(clop, read)
        assertEquals("Jangan berhenti tanpa Dr. Rao.", read.note)
        assertTrue(read.bloodThinner)
        assertEquals(LocalDate(2026, 10, 5) to agus, read.refillOn to read.refillBy)

        sri.editMedication(clop.id, clop.draft().copy(bloodThinner = false, refillOn = null, refillBy = null))
        assertEquals(null to false, sri.medications(circle).single().let { it.refillBy to it.bloodThinner })
    }
}
