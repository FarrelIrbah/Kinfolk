package id.kinfolk.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PendingTest {
    private val today = LocalDate(2026, 10, 5)
    private val at = Instant.fromEpochSeconds(1_000)
    private fun med(id: String) = Medication(id, "c", "r", id, "", "", LocalTime(7, 0), active = true)
    private val tukiman = CareRecipient("r", "c", "Tukiman")
    private val visit = Appointment("a", "c", "r", Provider("p", "c", "Dr. Anand Rao"), "Kontrol neurologi", startsAt = at)
    private val task = Task("t", "c", "r", "Perpanjang izin parkir", "budi", today, Task.Source.added, addedBy = "sri", done = false)
    private val kept = Snapshot(
        CareCircle("c", "Tukiman", 2), tukiman, visit, emptyList(), null, listOf(med("Clopidogrel"), med("Omeprazole")),
        emptyList(), emptyList(), emptyList(), null, today = today, tasks = listOf(task),
    )

    @Test
    fun `what waits on the phone shows on what the last load kept, and showing it twice changes nothing`() {
        val pending = listOf(
            Write.Doses(listOf(med("Clopidogrel"), med("Omeprazole")), today, give = true),
            Write.Doses(listOf(med("Omeprazole")), today, give = false),
            Write.SaveCheckIn(tukiman, today, CheckInDraft(128, 80, Ate.yes, walked = true, Mood.good, "")),
            Write.TaskDone("t", done = true),
            Write.Ask("c", "a", "Boleh menyetir lagi?", id = "q"),
            Write.AddNote("c", "Bapak minta radio lamanya.", private = false),
        )

        val shown = kept.with(pending, "budi", at)

        assertEquals(listOf("Clopidogrel"), shown.doses.map { it.medicationId })
        assertEquals(128, shown.checkIn!!.sys)
        assertEquals(listOf(true), shown.tasks.map { it.done })
        assertEquals(listOf("Boleh menyetir lagi?"), shown.questions.map { it.text })
        assertEquals(shown, shown.with(pending, "budi", at))
    }

    @Test
    fun `yesterday's doses and another Appointment's Questions don't show on today's Home`() {
        val pending = listOf(
            Write.Doses(listOf(med("Clopidogrel")), LocalDate(2026, 10, 4), give = true),
            Write.Ask("c", "elsewhere", "Boleh menyetir lagi?"),
        )

        assertEquals(kept, kept.with(pending, "budi", at))
    }
}
