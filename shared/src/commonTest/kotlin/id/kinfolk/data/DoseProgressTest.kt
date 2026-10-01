package id.kinfolk.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DoseProgressTest {
    private val today = LocalDate(2026, 9, 29)
    private fun med(id: String, h: Int) = Medication(id, "c", "r", id, "", "", LocalTime(h, 0), active = true)
    private fun given(id: String) = DoseLog(id, today, "sri", Instant.fromEpochSeconds(0))
    private val meds = listOf(med("Omeprazole", 6), med("Clopidogrel", 7), med("Amlodipine", 7), med("Atorvastatin", 21))

    @Test
    fun `the ring counts today's doses and names the first one not yet given`() {
        assertEquals(DoseProgress(0, 4, meds[0]), meds.progress(emptyList()))
        assertEquals(DoseProgress(2, 4, meds[0]), meds.progress(listOf(given("Clopidogrel"), given("Amlodipine"))))
        assertEquals(DoseProgress(3, 4, meds[3]), meds.progress(listOf(given("Omeprazole"), given("Clopidogrel"), given("Amlodipine"))))
    }

    @Test
    fun `with every dose given, next is the first one tomorrow`() {
        assertEquals(DoseProgress(4, 4, meds[0]), meds.progress(meds.map { given(it.id) }))
        assertEquals(DoseProgress(0, 0, null), emptyList<Medication>().progress(emptyList()))
    }

    @Test
    fun `morning doses are the ones before noon`() {
        assertEquals(meds.take(3), (meds + med("Noon", 12)).morning())
    }
}
