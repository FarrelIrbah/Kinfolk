package id.kinfolk.ui.records

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RupiahTest {
    @Test
    fun `amounts read like Rp 250_000`() {
        assertEquals("Rp 0", rupiah(0))
        assertEquals("Rp 500", rupiah(500))
        assertEquals("Rp 250.000", rupiah(250_000))
        assertEquals("Rp 1.250.000", rupiah(1_250_000))
    }

    @Test
    fun `typed amounts keep only the digits, dots being thousands`() {
        assertEquals(250_000, parseRupiah("Rp 250.000"))
        assertEquals(45_000, parseRupiah("45000"))
        assertNull(parseRupiah(""))
        assertNull(parseRupiah("Rp 0"))
        assertNull(parseRupiah("1234567890123456"))
    }

    @Test
    fun `the total is headed by this month`() {
        assertEquals("September sejauh ini", soFar(LocalDate(2026, 9, 30)))
        assertEquals("Oktober sejauh ini", soFar(LocalDate(2026, 10, 1)))
    }
}
