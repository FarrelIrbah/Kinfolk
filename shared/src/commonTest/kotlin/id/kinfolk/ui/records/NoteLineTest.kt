package id.kinfolk.ui.records

import androidx.compose.ui.graphics.Color
import id.kinfolk.data.Medication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteLineTest {
    private val today = LocalDate(2026, 9, 29)
    private val red = Color(0xFF9E3B1E)
    private val gold = Color(0xFF9A7A2F)
    private val grey = Color(0xFF6B6A60)
    private fun med(note: String = "", thinner: Boolean = false, refill: LocalDate? = null) =
        Medication("m", "c", "r", "Omeprazole", "20 mg", "sebelum sarapan", LocalTime(6, 0), true, note, thinner, refill, "agus")

    @Test
    fun `a blood thinner comes first, in red, with its note`() {
        assertEquals("Pengencer darah. Jangan berhenti tanpa Dr. Rao." to red, med("Jangan berhenti tanpa Dr. Rao.", thinner = true, refill = today).noteLine(today, "Agus"))
        assertEquals("Pengencer darah." to red, med(thinner = true).noteLine(today, null))
    }

    @Test
    fun `a refill due within a week shows in gold with who refills it`() {
        assertEquals("Isi ulang 4 hari lagi · Agus" to gold, med("Lambung", refill = LocalDate(2026, 10, 3)).noteLine(today, "Agus"))
        assertEquals("Isi ulang besok · Agus" to gold, med(refill = LocalDate(2026, 9, 30)).noteLine(today, "Agus"))
        assertEquals("Isi ulang hari ini" to gold, med(refill = LocalDate(2026, 9, 27)).noteLine(today, null))
    }

    @Test
    fun `otherwise the note in grey, or nothing`() {
        assertEquals("Kolesterol" to grey, med("Kolesterol", refill = LocalDate(2026, 10, 7)).noteLine(today, "Agus"))
        assertNull(med().noteLine(today, null))
    }
}
