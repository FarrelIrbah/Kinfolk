package id.kinfolk.ui.rota

import id.kinfolk.data.DutyTurn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class HandOffTest {
    private val today = LocalDate(2026, 9, 29) // Tuesday
    private fun turn(day: Int, holder: String, swapTo: String? = null, duty: String = "call") =
        DutyTurn(duty, "Telepon cek malam", LocalTime(19, 0), LocalDate(2026, if (day < 28) 10 else 9, day), holder, swapTo = swapTo)

    @Test
    fun `each of my days from today goes to whoever has the least, counting what they were just given`() {
        val turns = listOf(
            turn(28, "sri"), turn(29, "sri"), turn(30, "budi"), turn(1, "sri"), turn(2, "sri", swapTo = "dewi"), turn(3, "sri"),
        )
        val load = mapOf("budi" to 3, "dewi" to 1, "rina" to 1)
        // Monday is past, Friday is already asked; ties go to the first in order.
        assertEquals(
            listOf(LocalDate(2026, 9, 29) to "dewi", LocalDate(2026, 10, 1) to "rina", LocalDate(2026, 10, 3) to "dewi"),
            handOff(turns, "sri", today, listOf("budi", "dewi", "rina")) { load.getValue(it) }.map { (t, to) -> t.day to to },
        )
    }

    @Test
    fun `nothing to hand off without my days or anyone to take them`() {
        assertEquals(emptyList(), handOff(listOf(turn(30, "budi")), "sri", today, listOf("budi")) { 0 })
        assertEquals(emptyList(), handOff(listOf(turn(30, "sri")), "sri", today, emptyList()) { 0 })
    }

    @Test
    fun `names read Budi, Budi dan Dewi, then Budi, Dewi, dan Agus`() {
        assertEquals("Budi", listing(listOf("Budi"), "dan"))
        assertEquals("Budi dan Dewi", listing(listOf("Budi", "Dewi"), "dan"))
        assertEquals("Budi, Dewi, dan Agus", listing(listOf("Budi", "Dewi", "Agus"), "dan"))
    }
}
