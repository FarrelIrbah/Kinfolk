package id.kinfolk.ui.tasks

import id.kinfolk.data.Task
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTextTest {
    private val today = LocalDate(2026, 10, 1)
    private fun task(due: LocalDate, done: Boolean = false, id: String = "t") =
        Task(id, "c", "r", "Perpanjang izin parkir", "budi", due, Task.Source.added, null, "budi", done)

    @Test
    fun `due reads Tenggat until it passes, then Terlambat N hari`() {
        assertEquals("Tenggat 6 Okt", dueLabel(LocalDate(2026, 10, 6), today))
        assertEquals("Tenggat 1 Okt", dueLabel(today, today))
        assertEquals("Terlambat 1 hari", dueLabel(LocalDate(2026, 9, 30), today))
        assertEquals("Terlambat 2 hari", dueLabel(LocalDate(2026, 9, 29), today))
    }

    @Test
    fun `overdue are the open Tasks past due, oldest first`() {
        val tasks = listOf(
            task(LocalDate(2026, 9, 30), id = "a"), task(LocalDate(2026, 9, 20), id = "b"),
            task(LocalDate(2026, 9, 1), done = true, id = "c"), task(today, id = "d"),
        )
        assertEquals(listOf("b", "a"), tasks.overdue(today).map { it.id })
    }

    @Test
    fun `a step's source is its Appointment, in the sentence`() {
        assertEquals("kontrol neurologi", fromWhat("Kontrol neurologi"))
    }
}
