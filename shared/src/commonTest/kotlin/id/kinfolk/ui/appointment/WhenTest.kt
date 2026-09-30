package id.kinfolk.ui.appointment

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhenTest {
    private val wib = TimeZone.of("Asia/Jakarta")
    private fun at(d: Int, h: Int, m: Int, month: Int = 9) = LocalDateTime(2026, month, d, h, m).toInstant(wib)
    private val now = at(29, 11, 20)

    @Test
    fun `an Appointment says when it is the way the design does`() {
        assertEquals("Hari ini · 14.30", whenLabel(at(29, 14, 30), now, wib))
        assertEquals("Besok · 09.00", whenLabel(at(30, 9, 0), now, wib))
        assertEquals("Kam, 1 Okt · 09.00", whenLabel(at(1, 9, 0, month = 10), now, wib))
    }

    @Test
    fun `the countdown pill counts minutes, hours, then days`() {
        assertEquals("40 mnt lagi", countdown(at(29, 12, 0), now, wib))
        assertEquals("40 mnt lagi", countdown(at(30, 0, 30), at(29, 23, 50), wib))
        assertEquals("3 jam 10 mnt lagi", countdown(at(29, 14, 30), now, wib))
        assertEquals("3 jam lagi", countdown(at(29, 14, 20), now, wib))
        assertEquals("1 hari lagi", countdown(at(30, 9, 0), now, wib))
        assertEquals("3 hari lagi", countdown(at(2, 8, 0, month = 10), now, wib))
        assertEquals("Sedang berlangsung", countdown(at(29, 11, 0), now, wib))
    }

    @Test
    fun `dates and times read like the design`() {
        assertEquals("Selasa, 29 Sept", longDate(LocalDate(2026, 9, 29)))
        assertEquals("Sel, 29 Sept", shortDate(LocalDate(2026, 9, 29)))
        assertEquals("14.30", hm(LocalTime(14, 30)))
        assertEquals("09.05", hm(LocalTime(9, 5)))
    }

    @Test
    fun `typed times are read back as clock times`() {
        assertEquals(LocalTime(14, 30), parseHm("1430"))
        assertEquals(LocalTime(9, 5), parseHm("0905"))
        assertNull(parseHm("930"))
        assertNull(parseHm("2460"))
    }
}
