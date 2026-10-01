package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ExpenseTest {
    init { Providers } // start the fake providers before anything is sent

    private val day = LocalDate(2026, 9, 26)

    @Test
    fun `any Member records who paid, read by everyone, newest first`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)

        sri.addExpense(circle, tukiman, " Biaya MRI ", 250_000, paidBy = sri.me(), day = LocalDate(2026, 9, 20))
        dewi.addExpense(circle, tukiman, "Alarm jatuh (DP)", 45_000, paidBy = sri.me(), day = day)

        val list = sri.expenses(circle)
        assertEquals(listOf("Alarm jatuh (DP)", "Biaya MRI"), list.map { it.what })
        assertEquals(listOf(45_000L, 250_000L), list.map { it.amount })
        assertEquals(listOf(sri.me(), sri.me()), list.map { it.paidBy })
        assertEquals(list, dewi.expenses(circle))
    }

    @Test
    fun `expenses follow Tagihan & uang`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)
        sri.addExpense(circle, tukiman, "Biaya fisioterapi ×4", 160_000, paidBy = sri.me(), day = day)

        sri.setHidden(tukiman, dewi.me(), DataCategory.money, hidden = true)

        assertTrue(dewi.expenses(circle).isEmpty())
        assertFails { dewi.addExpense(circle, tukiman, "Kursi mandi", 96_000, paidBy = dewi.me(), day = day) }
        assertEquals(1, sri.expenses(circle).size)

        sri.setHidden(tukiman, dewi.me(), DataCategory.money, hidden = false)
        assertEquals(1, dewi.expenses(circle).size)
    }

    @Test
    fun `strangers and viewers don't write, nor blank or zero ones, nor paid by an outsider or a viewer`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val viewer = signedInSibling(sri, circle, Role.viewer)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        assertFails { rudi.addExpense(circle, tukiman, "Obat", 18_000, paidBy = rudi.me(), day = day) }
        assertFails { viewer.addExpense(circle, tukiman, "Obat", 18_000, paidBy = viewer.me(), day = day) }
        assertFails { sri.addExpense(circle, tukiman, "  ", 18_000, paidBy = sri.me(), day = day) }
        assertFails { sri.addExpense(circle, tukiman, "Obat", 0, paidBy = sri.me(), day = day) }
        assertFails { sri.addExpense(circle, tukiman, "Obat", 18_000, paidBy = rudi.me(), day = day) }
        assertFails { sri.addExpense(circle, tukiman, "Obat", 18_000, paidBy = viewer.me(), day = day) }
        assertTrue(rudi.expenses(circle).isEmpty())
        assertTrue(viewer.expenses(circle).isEmpty())
    }
}
