package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareCircleTest {

    @Test
    fun `whoever creates a Care Circle is its only Member, as admin`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, setOf(Need.Visits, Need.Medicines))

        assertEquals(CareCircle(circle, "Tukiman", memberCount = 1), sri.myCareCircle())
        assertEquals(Role.admin, sri.roleIn(circle))
        assertEquals(listOf("Tukiman"), sri.careRecipients(circle).map { it.name })
    }

    @Test
    fun `a Care Circle can hold more than one Care Recipient`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet())
        sri.addCareRecipient(circle, "Sumarni", Relation.Mother)

        assertEquals(listOf("Tukiman", "Sumarni"), sri.careRecipients(circle).map { it.name })
    }

    @Test
    fun `a Member of one Care Circle can't read or write anything in another`() = runBlocking {
        val sri = signedInNewcomer()
        val sriCircle = sri.createCareCircle("Tukiman", Relation.Father, emptySet())
        val tukiman = sri.careRecipients(sriCircle).single()
        val rudi = signedInNewcomer()
        val rudiCircle = rudi.createCareCircle("Warsini", Relation.Mother, emptySet())

        assertEquals("Warsini", rudi.myCareCircle()?.name)
        assertTrue(rudi.careRecipients(sriCircle).isEmpty())
        assertNull(rudi.roleIn(sriCircle).also { assertEquals(Role.admin, rudi.roleIn(rudiCircle)) })
        assertFails { rudi.addCareRecipient(sriCircle, "Penyusup", null) }
        assertEquals(0, rudi.renameCareRecipient(tukiman.id, "Penyusup"))

        assertEquals(listOf("Tukiman"), sri.careRecipients(sriCircle).map { it.name })
        assertEquals(1, sri.myCareCircle()?.memberCount)
    }

    @Test
    fun `someone who belongs to no Care Circle sees none`() = runBlocking {
        signedInNewcomer().createCareCircle("Tukiman", null, emptySet())
        assertNull(signedInNewcomer().myCareCircle())
    }

    @Test
    fun `creating a Care Circle requires signing in`() = runBlocking {
        assertFails { signedOut().createCareCircle("Tukiman", null, emptySet()) }
        Unit
    }
}
