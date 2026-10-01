package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class DutyTest {
    init { Providers } // start the fake providers before anything is sent

    // The server's "today" (joining, invitee) is Jakarta's.
    private val today = Clock.System.todayIn(TimeZone.of("Asia/Jakarta"))
    private fun day(n: Int) = today + DatePeriod(days = n)
    private val sevenPm = LocalTime(19, 0)

    private suspend fun sriWithCircle() = signedInNewcomer().let { it to it.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri") }

    private suspend fun io.github.jan.supabase.SupabaseClient.turn(circle: String, day: LocalDate) =
        dutyWeek(circle, day).single { it.day == day }

    private suspend fun io.github.jan.supabase.SupabaseClient.holders(circle: String, days: IntRange) =
        days.map { n -> turn(circle, day(n)).holder }

    @Test
    fun `an admin creates a Duty that passes to the next Member in its order every day`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)

        assertFails { budi.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(budi.me()), today) }
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(dewi.me(), sri.me(), budi.me()), today)

        assertEquals(listOf(dewi.me(), sri.me(), budi.me(), dewi.me()), budi.holders(circle, 0..3))
        val week = budi.dutyWeek(circle, today)
        assertEquals((0..6).map { weekOf(today) + DatePeriod(days = it) }, week.map { it.day }) // one row a day, Monday first
        val turn = budi.turn(circle, today)
        assertEquals("Telepon cek malam" to sevenPm, turn.name to turn.timeOfDay)
        assertEquals(listOf(dewi.me(), sri.me(), budi.me()), turn.rotation)
        assertTrue(signedInNewcomer().dutyWeek(circle, today).isEmpty())

        // Reordering keeps today's holder; deleting it clears the rota.
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(budi.me(), sri.me(), dewi.me()), today, id = turn.dutyId)
        assertEquals(listOf(dewi.me(), budi.me(), sri.me()), dewi.holders(circle, 0..2))
        assertFails { budi.deleteDuty(turn.dutyId) }
        sri.deleteDuty(turn.dutyId)
        assertTrue(sri.dutyWeek(circle, today).isEmpty())
    }

    @Test
    fun `a swap moves one day only, after the other Member agrees`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), today)

        assertFails { budi.askSwap(duty, today, dewi.me()) } // not Budi's day
        val swap = sri.askSwap(duty, today, budi.me())
        val waiting = dewi.turn(circle, today)
        assertEquals(Triple(sri.me(), swap, budi.me()), Triple(waiting.holder, waiting.swapId, waiting.swapTo))
        assertNull(dewi.turn(circle, day(3)).swapId) // Sri's next day isn't asked

        assertFails { dewi.answerSwap(swap, accept = true) } // only Budi can agree
        assertFails { sri.answerSwap(swap, accept = true) }
        budi.answerSwap(swap, accept = true)
        val swapped = dewi.turn(circle, today)
        assertEquals(budi.me() to null, swapped.holder to swapped.swapId)
        assertEquals(listOf(budi.me(), budi.me(), dewi.me(), sri.me()), dewi.holders(circle, 0..3)) // only that day

        // Budi can pass it on in turn; a decline leaves it with him.
        val again = budi.askSwap(duty, today, dewi.me())
        dewi.answerSwap(again, accept = false)
        assertFails { dewi.answerSwap(again, accept = true) }
        assertEquals(budi.me() to null, sri.turn(circle, today).let { it.holder to it.swapId })
    }

    @Test
    fun `new Members join the rotation only after accepting, and a Former Member's days pass to the next Member`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), today)

        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Agus", phone)
        val agus = signedInAs(phone)
        assertEquals(3, sri.turn(circle, today).rotation.size) // invited is not in yet
        agus.acceptInvitation(agus.myInvitations().single().id)
        assertEquals(listOf(sri.me(), budi.me(), dewi.me(), agus.me()), sri.turn(circle, today).rotation)

        val swap = sri.askSwap(sri.turn(circle, today).dutyId, today, budi.me())
        sri.removeMember(circle, budi.me())
        assertEquals(listOf(sri.me(), dewi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..4))
        assertNull(sri.turn(circle, today).swapId) // a swap asked of a Former Member lapses
        assertFails { budi.answerSwap(swap, accept = true) }
        assertEquals(listOf(sri.me(), dewi.me(), agus.me()), sri.turn(circle, today).rotation)
    }

    @Test
    fun `whoever holds today keeps it when someone joins or the admin edits the Duty`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), day(-1))
        val swap = budi.askSwap(duty, today, dewi.me())

        val agus = signedInSibling(sri, circle)
        assertEquals(listOf(budi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..3))
        assertEquals(swap, sri.turn(circle, today).swapId)

        sri.saveDuty(circle, "Telepon cek malam", LocalTime(20, 0), listOf(sri.me(), budi.me(), dewi.me(), agus.me()), today, id = duty)
        assertEquals(listOf(budi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..3))

        val viewer = signedInSibling(sri, circle, Role.viewer)
        assertTrue(viewer.me() !in sri.turn(circle, today).rotation)
    }

    @Test
    fun `an invitee is offered the first open day and can take it once they have joined`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), today)

        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Rina", phone)
        val rina = signedInAs(phone)
        assertNull(rina.myInvitations().single().dutyId) // nothing open

        sri.askSwap(duty, day(3), budi.me())
        dewi.askSwap(duty, day(2), budi.me())
        val inv = rina.myInvitations().single()
        assertEquals(listOf(duty, "Telepon cek malam", day(2), sevenPm), listOf(inv.dutyId, inv.duty, inv.dutyDay, inv.dutyTime))
        assertFails { rina.takeTurn(duty, day(2)) } // not a Member yet

        rina.acceptInvitation(inv.id)
        rina.takeTurn(duty, day(2))
        assertEquals(listOf(sri.me(), budi.me(), rina.me()), budi.holders(circle, 0..2))
        assertNull(sri.turn(circle, day(2)).swapId) // Dewi's ask is settled
    }
}
