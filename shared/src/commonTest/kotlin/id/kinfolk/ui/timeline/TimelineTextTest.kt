package id.kinfolk.ui.timeline

import id.kinfolk.data.TimelineEntry
import id.kinfolk.data.TimelineEntry.Kind
import id.kinfolk.ui.appointment.ago
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineTextTest {
    private val wib = TimeZone.of("Asia/Jakarta")
    private fun at(d: Int, h: Int, m: Int, month: Int = 9) = LocalDateTime(2026, month, d, h, m).toInstant(wib)
    private val now = at(29, 16, 0)

    private fun entry(kind: Kind, steps: List<String> = emptyList(), notes: String = "") =
        TimelineEntry(kind, "a", "u", "Sri", now, "Kontrol neurologi", "Dr. Anand Rao", at(1, 9, 0, month = 10), steps, notes)

    @Test
    fun `entries say when like the design, newest as clock times`() {
        assertEquals("Hari ini, 15.10", ago(at(29, 15, 10), now, wib))
        assertEquals("Sen, 19.10", ago(at(28, 19, 10), now, wib))
        assertEquals("Rab, 08.00", ago(at(23, 8, 0), now, wib))
        assertEquals("22 Sept", ago(at(22, 8, 0), now, wib))
    }

    @Test
    fun `a Visit Note reads as its next steps, else its notes`() {
        assertEquals(
            "Kontrol neurologi: fisioterapi 2x/minggu, MRI ulang 3 bulan lagi.",
            text(entry(Kind.visit_note, listOf("fisioterapi 2x/minggu", "MRI ulang 3 bulan lagi.")), wib),
        )
        assertEquals("Kontrol neurologi: Tensi 130/85.", text(entry(Kind.visit_note, notes = "Tensi 130/85."), wib))
        assertEquals("Kontrol neurologi", text(entry(Kind.visit_note), wib))
    }

    @Test
    fun `an Appointment reads as who scheduled what`() {
        assertEquals("Menjadwalkan Kontrol neurologi dengan Dr. Anand Rao, Kam, 1 Okt · 09.00.", text(entry(Kind.appointment), wib))
    }
}
