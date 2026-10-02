package id.kinfolk.ui.inbox

import id.kinfolk.data.Question
import id.kinfolk.data.SwapAsk
import id.kinfolk.data.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class InboxTest {
    private val today = LocalDate(2026, 10, 1)
    private val now = Instant.parse("2026-10-01T05:00:00Z")
    private val swap = SwapAsk("s", "d", "Telepon cek malam", LocalTime(19, 0), today, "agus", now - 20.minutes)
    private fun question(id: String, by: String, askedIn: String = "a") =
        Question(id, "a", askedIn, now, by, null, "Amankah Tukiman sendirian di malam hari?")
    private fun task(id: String, due: LocalDate, owner: String = "budi", done: Boolean = false) =
        Task(id, "c", "r", "Perpanjangan izin parkir", owner, due, Task.Source.added, null, owner, done)

    @Test
    fun `swaps first, then the others' new Questions newest first, then overdue Tasks oldest first`() {
        val items = inbox(
            listOf(swap),
            listOf(question("q1", "rina"), question("mine", "sri"), question("old", "rina", askedIn = "earlier"), question("q2", "budi")),
            noteReady = false,
            listOf(task("late", LocalDate(2026, 9, 30)), task("later", LocalDate(2026, 9, 20)), task("due", today), task("done", LocalDate(2026, 9, 1), done = true)),
            me = "sri", today,
        )
        assertEquals(
            listOf(InboxItem.Swap(swap), InboxItem.Asked(question("q2", "budi")), InboxItem.Asked(question("q1", "rina")),
                InboxItem.Late(task("later", LocalDate(2026, 9, 20))), InboxItem.Late(task("late", LocalDate(2026, 9, 30)))),
            items,
        )
    }

    @Test
    fun `Questions drop off once the Visit Note is written`() {
        assertEquals(emptyList(), inbox(emptyList(), listOf(question("q1", "rina")), noteReady = true, emptyList(), "sri", today))
    }

    @Test
    fun `copy follows v3, with the Duty, WhatsApp-era time and owner in place`() {
        assertEquals("Agus meminta Anda ambil telepon cek malam Kam 1 Okt, 19.00", swapTitle("Agus", swap))
        assertEquals("Dikirim 20 mnt lalu.", swapSub(swap, now))
        assertEquals("Perpanjangan izin parkir terlambat 2 hari", lateTitle(task("t", LocalDate(2026, 9, 29)), today))
        assertEquals("Tugas Budi · Anda bisa mengingatkan", lateSub(task("t", today), "sri", "Budi", canRemind = true))
        assertEquals("Tugas Budi · Diingatkan", lateSub(task("t", today).copy(remindedAt = now), "sri", "Budi", canRemind = true))
        assertEquals("Tugas Budi · Mantan anggota", lateSub(task("t", today), "sri", "Budi · Mantan anggota", canRemind = false))
        assertEquals("Tugas Anda", lateSub(task("t", today, owner = "sri"), "sri", "Anda", canRemind = false))
    }
}
