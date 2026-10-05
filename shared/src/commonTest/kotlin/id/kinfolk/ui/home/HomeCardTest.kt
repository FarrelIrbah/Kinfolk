package id.kinfolk.ui.home

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCardTest {
    private val today = LocalDate(2026, 9, 29)
    private val tomorrow = LocalDate(2026, 9, 30)
    private fun at(h: Int, m: Int = 0) = LocalDateTime(2026, 9, 29, h, m)

    @Test
    fun `each card shows on its own`() {
        assertEquals(HomeCard.AfterVisit, homeCard(at(15), today, summaryReady = true, holdsTonight = false, morningDue = false))
        assertEquals(HomeCard.VisitToday, homeCard(at(9), today, summaryReady = false, holdsTonight = false, morningDue = false))
        assertEquals(HomeCard.Evening, homeCard(at(17), null, summaryReady = false, holdsTonight = true, morningDue = false))
        assertEquals(HomeCard.Morning, homeCard(at(10, 59), null, summaryReady = false, holdsTonight = false, morningDue = true))
        assertEquals(HomeCard.Next, homeCard(at(12), tomorrow, summaryReady = false, holdsTonight = false, morningDue = false))
        assertEquals(HomeCard.Empty, homeCard(at(12), null, summaryReady = false, holdsTonight = false, morningDue = false))
    }

    @Test
    fun `higher cards win`() {
        assertEquals(HomeCard.AfterVisit, homeCard(at(18), today, summaryReady = true, holdsTonight = true, morningDue = true))
        assertEquals(HomeCard.VisitToday, homeCard(at(8), today, summaryReady = false, holdsTonight = true, morningDue = true))
        assertEquals(HomeCard.Evening, homeCard(at(20), tomorrow, summaryReady = false, holdsTonight = true, morningDue = true))
        assertEquals(HomeCard.Morning, homeCard(at(8), tomorrow, summaryReady = false, holdsTonight = true, morningDue = true))
    }

    @Test
    fun `outside their hours the evening and morning cards give way`() {
        assertEquals(HomeCard.Next, homeCard(at(16, 59), tomorrow, summaryReady = false, holdsTonight = true, morningDue = false))
        assertEquals(HomeCard.Next, homeCard(at(11), tomorrow, summaryReady = false, holdsTonight = false, morningDue = true))
        assertEquals(HomeCard.Empty, homeCard(at(20), null, summaryReady = false, holdsTonight = false, morningDue = true))
        // A Visit Note for a visit on another day is not "after visit".
        assertEquals(HomeCard.Next, homeCard(at(12), tomorrow, summaryReady = true, holdsTonight = false, morningDue = false))
    }
}
