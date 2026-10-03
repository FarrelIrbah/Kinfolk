package id.kinfolk.ui.bapak

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BapakTextTest {
    @Test
    fun `greeting follows the WIB hour and the relation, approved in #37`() {
        assertEquals("Selamat pagi, Pak Tukiman", greeting(10, "father", "Tukiman"))
        assertEquals("Selamat siang, Bu Warsini", greeting(11, "mother", "Warsini"))
        assertEquals("Selamat sore, Pak Tukiman", greeting(15, "father", "Tukiman"))
        assertEquals("Selamat malam, Kakek", greeting(18, "grandparent", "Kakek"))
        assertEquals("Selamat malam, Tukiman", greeting(2, null, "Tukiman"))
    }

    @Test
    fun `today's plan is v3's line, with or without a Driver`() {
        assertEquals("Dr. Rao jam 14.30. Budi menjemput jam 13.45.", todayPlan("Dr. Rao", LocalTime(14, 30), "Budi", LocalTime(13, 45)))
        assertEquals("Dr. Rao jam 14.30. Budi menjemput.", todayPlan("Dr. Rao", LocalTime(14, 30), "Budi", null))
        assertEquals("Dr. Rao jam 14.30.", todayPlan("Dr. Rao", LocalTime(14, 30), null, null))
    }

    @Test
    fun `help line adds who is near only when a distance is filled`() {
        assertEquals("Menghubungi Sri dan Budi sekarang.", helpLine("Sri dan Budi", null))
        assertEquals("Menghubungi Sri dan Budi sekarang. Budi 10 menit dari sini.", helpLine("Sri dan Budi", "Budi" to "10 menit"))
    }
}
