package id.kinfolk.ui.timeline

import androidx.compose.ui.graphics.Color
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

    private fun entry(kind: Kind, steps: List<String> = emptyList(), notes: String = "", text: String = "") =
        TimelineEntry(kind, "a", "u", "Sri", now, "Kontrol neurologi", "Dr. Anand Rao", at(1, 9, 0, month = 10), steps, notes, text)

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

    @Test
    fun `a Driver's confirmation reads as written when it happened`() {
        val confirmed = "Konfirmasi via WhatsApp: mengantar ke kontrol neurologi jam 13.45."
        assertEquals(confirmed, text(entry(Kind.drive_confirmed, text = confirmed), wib))
    }

    @Test
    fun `each kind has the design's label and colour, and filters by it`() {
        assertEquals(EntryType.Visit, type(Kind.appointment))
        assertEquals(EntryType.Visit, type(Kind.visit_note))
        assertEquals(EntryType.Rota, type(Kind.drive_confirmed))
        assertEquals(EntryType.CheckIn, type(Kind.access_change))
        assertEquals(
            listOf(0xFF2F5D4A, 0xFF9A7A2F, 0xFF3E6E8E, 0xFF6C5A8E, 0xFFB0643A).map { Color(it) },
            EntryType.entries.map { it.color },
        )
        // Semua, Kunjungan, Obat, Cek, Dokumen: no chip for Rota, which only Semua shows.
        assertEquals(listOf(null, EntryType.Visit, EntryType.Medicine, EntryType.CheckIn, EntryType.Document), Filters)
    }
}
