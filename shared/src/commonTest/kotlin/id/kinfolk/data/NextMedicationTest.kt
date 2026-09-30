package id.kinfolk.data

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextMedicationTest {
    private fun med(name: String, h: Int, m: Int = 0) = Medication(name, "c", "r", name, "", "", LocalTime(h, m), active = true)
    private val day = listOf(med("Omeprazole", 6, 30), med("Clopidogrel", 7), med("Atorvastatin", 21))

    @Test
    fun `the next Medication is the first due at or after now, else tomorrow's first`() {
        assertEquals("Omeprazole", day.next(LocalTime(5, 0))?.name)
        assertEquals("Clopidogrel", day.next(LocalTime(7, 0))?.name)
        assertEquals("Atorvastatin", day.next(LocalTime(7, 1))?.name)
        assertEquals("Omeprazole", day.next(LocalTime(22, 0))?.name)
        assertNull(emptyList<Medication>().next(LocalTime(8, 0)))
    }
}
