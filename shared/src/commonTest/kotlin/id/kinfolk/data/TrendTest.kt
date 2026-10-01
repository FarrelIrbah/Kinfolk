package id.kinfolk.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TrendTest {
    private val start = LocalDate(2026, 9, 1)
    private fun ci(day: Int, sys: Int, dia: Int, ate: Ate = Ate.yes, walked: Boolean = true, mood: Mood = Mood.good) =
        CheckIn(start.plus(DatePeriod(days = day)), "sri", Instant.fromEpochSeconds(0), sys, dia, ate, walked, mood)

    @Test
    fun `averages round like v3 and count readings at 140 or above`() {
        val t = listOf(ci(0, 130, 80), ci(1, 141, 85), ci(2, 140, 84)).trend()
        // (130+141+140)/3 = 137 (136.99…), (80+85+84)/3 = 83
        assertEquals(Trend(137, 83, high = 2, ate = 3, walked = 3, good = 3), t)
    }

    @Test
    fun `only Ya, walked and Baik count as good`() {
        val t = listOf(
            ci(0, 120, 80, Ate.some, walked = false, Mood.okay),
            ci(1, 120, 80, Ate.no, walked = true, Mood.low),
            ci(2, 120, 80, Ate.yes, walked = false, Mood.good),
        ).trend()
        assertEquals(Trend(120, 80, high = 0, ate = 1, walked = 1, good = 1), t)
    }

    @Test
    fun `the last 30 Check-ins, oldest first`() {
        val all = (0 until 35).map { ci(it, if (it < 5) 180 else 120, 80) }.shuffled()
        val last = all.last30()
        assertEquals((5 until 35).map { start.plus(DatePeriod(days = it)) }, last.map { it.day })
        assertEquals(0, last.trend().high)
    }
}
