package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
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

    private val week = weekOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    private fun week(n: Int) = week + DatePeriod(days = 7 * n)
    private val sevenPm = LocalTime(19, 0)

    private suspend fun sriWithCircle() = signedInNewcomer().let { it to it.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri") }

    private suspend fun io.github.jan.supabase.SupabaseClient.holders(circle: String, weeks: IntRange) =
        weeks.map { n -> dutyWeek(circle, week(n)).single().holder }

    @Test
    fun `an admin creates a Duty that passes to the next Member in its order every week`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)

        assertFails { budi.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(budi.me()), week) }
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(dewi.me(), sri.me(), budi.me()), week)

        assertEquals(listOf(dewi.me(), sri.me(), budi.me(), dewi.me()), budi.holders(circle, 0..3))
        assertEquals(dewi.me(), budi.dutyWeek(circle, week + DatePeriod(days = 3)).single().holder) // any day finds its week
        val turn = budi.dutyWeek(circle, week).single()
        assertEquals("Telepon cek malam" to sevenPm, turn.name to turn.timeOfDay)
        assertEquals(listOf(dewi.me(), sri.me(), budi.me()), turn.rotation)
        assertTrue(signedInNewcomer().dutyWeek(circle, week).isEmpty())

        // Reordering keeps the Duty; deleting it clears the rota.
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(budi.me(), sri.me()), week, id = turn.dutyId)
        assertEquals(listOf(budi.me(), sri.me(), budi.me()), dewi.holders(circle, 0..2))
        assertFails { budi.deleteDuty(turn.dutyId) }
        sri.deleteDuty(turn.dutyId)
        assertTrue(sri.dutyWeek(circle, week).isEmpty())
    }

    @Test
    fun `a swap only takes effect after the other Member agrees`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), week)

        assertFails { budi.askSwap(duty, week, dewi.me()) } // not Budi's turn
        val swap = sri.askSwap(duty, week, budi.me())
        val waiting = dewi.dutyWeek(circle, week).single()
        assertEquals(Triple(sri.me(), swap, budi.me()), Triple(waiting.holder, waiting.swapId, waiting.swapTo))

        assertFails { dewi.answerSwap(swap, accept = true) } // only Budi can agree
        assertFails { sri.answerSwap(swap, accept = true) }
        budi.answerSwap(swap, accept = true)
        val swapped = dewi.dutyWeek(circle, week).single()
        assertEquals(budi.me() to null, swapped.holder to swapped.swapId)
        assertEquals(listOf(budi.me(), dewi.me()), dewi.holders(circle, 1..2)) // only that week

        // Budi can pass it on in turn; a decline leaves it with him.
        val again = budi.askSwap(duty, week, dewi.me())
        dewi.answerSwap(again, accept = false)
        assertFails { dewi.answerSwap(again, accept = true) }
        assertEquals(budi.me() to null, sri.dutyWeek(circle, week).single().let { it.holder to it.swapId })
    }

    @Test
    fun `new Members join the rotation only after accepting, and a Former Member's turns pass to the next Member`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), week)

        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Agus", phone)
        val agus = signedInAs(phone)
        assertEquals(3, sri.dutyWeek(circle, week).single().rotation.size) // invited is not in yet
        agus.acceptInvitation(agus.myInvitations().single().id)
        assertEquals(listOf(sri.me(), budi.me(), dewi.me(), agus.me()), sri.dutyWeek(circle, week).single().rotation)

        val swap = sri.askSwap(sri.dutyWeek(circle, week).single().dutyId, week, budi.me())
        sri.removeMember(circle, budi.me())
        assertEquals(listOf(sri.me(), dewi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..4))
        assertNull(sri.dutyWeek(circle, week).single().swapId) // a swap asked of a Former Member lapses
        assertFails { budi.answerSwap(swap, accept = true) }
        assertEquals(listOf(sri.me(), dewi.me(), agus.me()), sri.dutyWeek(circle, week).single().rotation)
    }

    @Test
    fun `whoever holds this week keeps it when someone joins or the admin edits the Duty`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me(), dewi.me()), week(-1))
        val swap = budi.askSwap(duty, week, dewi.me())

        val agus = signedInSibling(sri, circle)
        assertEquals(listOf(budi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..3))
        assertEquals(swap, sri.dutyWeek(circle, week).single().swapId)

        sri.saveDuty(circle, "Telepon cek malam", LocalTime(20, 0), listOf(sri.me(), budi.me(), dewi.me(), agus.me()), week, id = duty)
        assertEquals(listOf(budi.me(), dewi.me(), agus.me(), sri.me()), sri.holders(circle, 0..3))

        val viewer = signedInSibling(sri, circle, Role.viewer)
        assertTrue(viewer.me() !in sri.dutyWeek(circle, week).single().rotation)
    }

    @Test
    fun `an invitee sees next week's turn and can take it once they have joined`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val duty = sri.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.me(), budi.me()), week)

        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Rina", phone)
        val rina = signedInAs(phone)
        val inv = rina.myInvitations().single()
        assertEquals(Triple(duty, "Telepon cek malam", "Budi"), Triple(inv.dutyId, inv.duty, inv.dutyHolder))
        assertFails { rina.takeTurn(duty, week(1)) } // not a Member yet

        rina.acceptInvitation(inv.id)
        rina.takeTurn(duty, week(1))
        assertEquals(listOf(sri.me(), rina.me()), budi.holders(circle, 0..1))
    }
}
