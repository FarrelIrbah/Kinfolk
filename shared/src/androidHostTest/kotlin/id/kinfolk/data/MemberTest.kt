package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class MemberTest {
    init { Providers } // start the fake providers before anything is sent

    private val now = Clock.System.now()

    private suspend fun sriWithCircle() = signedInNewcomer().let { it to it.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri") }

    @Test
    fun `Members see who is in the Care Circle and their Role, in join order`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)

        val members = budi.members(circle)
        assertEquals(listOf(sri.me() to Role.admin, budi.me() to Role.sibling), members.map { it.userId to it.role })
        assertEquals(listOf("Sri", "Budi"), members.map { it.name })
        assertTrue(members.all { it.leftAt == null })
        assertEquals(members, sri.members(circle))
    }

    @Test
    fun `an admin promotes a Member to admin, a sibling can't promote or remove anyone`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)

        assertFails { budi.promoteToAdmin(circle, dewi.me()) }
        assertFails { budi.removeMember(circle, dewi.me()) }
        assertFails { budi.removeMember(circle, sri.me()) }
        assertEquals(Role.sibling, dewi.roleIn(circle))

        sri.promoteToAdmin(circle, budi.me())
        assertEquals(Role.admin, budi.roleIn(circle))
        budi.removeMember(circle, dewi.me()) // a promoted admin can remove
        assertNull(dewi.myCareCircle())
    }

    @Test
    fun `the last admin can't leave until someone else is admin, so the Care Circle always has one`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)

        val e = assertFails { sri.leaveCareCircle(circle) }
        assertTrue(e.isLastAdmin())
        assertEquals(Role.admin, sri.roleIn(circle))

        sri.promoteToAdmin(circle, budi.me())
        sri.leaveCareCircle(circle)
        assertNull(sri.myCareCircle())
        assertTrue(assertFails { budi.leaveCareCircle(circle) }.isLastAdmin())
        assertEquals(listOf(Role.admin), budi.members(circle).filter { it.leftAt == null }.map { it.role })
    }

    @Test
    fun `a sibling leaves on their own`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)

        budi.leaveCareCircle(circle)

        assertNull(budi.myCareCircle())
        assertEquals(1, sri.myCareCircle()?.memberCount)
    }

    @Test
    fun `a Former Member can read and write nothing, but what they wrote stays under their name`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val past = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now - 3.days)).id
        val upcoming = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now + 3.days)).id
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val budi = signedInSibling(sri, circle)
        budi.askQuestion(circle, upcoming, "Kapan Bapak boleh menyetir lagi?")

        sri.removeMember(circle, budi.me())

        assertNull(budi.myCareCircle())
        assertNull(budi.roleIn(circle))
        assertTrue(budi.members(circle).isEmpty())
        assertTrue(budi.careRecipients(circle).isEmpty())
        assertTrue(budi.medications(circle).isEmpty())
        assertTrue(budi.questions(upcoming).isEmpty())
        assertNull(budi.nextAppointment(circle, now - 7.days))
        assertFails { budi.askQuestion(circle, upcoming, "Boleh sendirian malam hari?") }
        assertFails { budi.addMedication(MedicationDraft(circle, tukiman, "Amlodipine", "5 mg", "pagi", LocalTime(7, 0))) }
        assertEquals(0, budi.renameCareRecipient(tukiman, "Budi"))

        val q = sri.questions(upcoming).single()
        assertEquals(budi.me() to "Budi", q.askedBy to q.askedByName)
        val former = sri.members(circle).single { it.userId == budi.me() }
        assertEquals("Budi", former.name)
        assertNotNull(former.leftAt)
        assertEquals(1, sri.myCareCircle()?.memberCount)
        assertEquals(listOf(past, upcoming), sri.appointmentsWith(provider).map { it.id }.reversed())
    }

    @Test
    fun `removal takes a Former Member off upcoming Appointments but keeps them on past ones`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val budi = signedInSibling(sri, circle)
        fun at(days: Int) = AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now + days.days, driverId = budi.me(), attendeeId = budi.me())
        val past = sri.scheduleAppointment(at(-3)).id
        val upcoming = sri.scheduleAppointment(at(3)).id

        sri.removeMember(circle, budi.me())

        val byId = sri.appointmentsWith(provider).associateBy { it.id }
        assertEquals(budi.me() to budi.me(), byId.getValue(past).let { it.driverId to it.attendeeId })
        assertEquals(null to null, byId.getValue(upcoming).let { it.driverId to it.attendeeId })
    }

    @Test
    fun `a Former Attendee can't write the Visit Note of a past visit`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val budi = signedInSibling(sri, circle)
        val past = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now - 1.days, attendeeId = budi.me())).id

        sri.removeMember(circle, budi.me())

        assertFails { budi.saveVisitNote(past, emptyMap(), listOf("MRI ulang"), "") }
        assertNull(sri.visitNote(past))
    }

    @Test
    fun `a Former Member invited again joins as a Member again`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Budi", phone)
        val budi = signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }
        sri.removeMember(circle, budi.me())

        sri.invite(circle, "Budi", phone)
        budi.acceptInvitation(budi.myInvitations().single().id)

        assertEquals(Role.sibling, budi.roleIn(circle))
        assertEquals(2, sri.myCareCircle()?.memberCount)
    }

    @Test
    fun `a viewer reads everything and changes nothing`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now + 1.days))
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))
        val rina = signedInSibling(sri, circle, Role.viewer)

        assertEquals(Role.viewer, rina.roleIn(circle))
        assertEquals(appt.id, rina.nextAppointment(circle, now)?.id)
        assertEquals(1, rina.medications(circle).size)
        assertEquals(1, rina.careContacts(circle).size)

        assertFails { rina.addProvider(circle, "Dr. Lim") }
        assertFails { rina.scheduleAppointment(appt.draft()) }
        assertFails { rina.askQuestion(circle, appt.id, "Kapan Bapak boleh menyetir lagi?") }
        assertFails { rina.addMedication(MedicationDraft(circle, tukiman, "Amlodipine", "5 mg", "pagi", LocalTime(7, 0))) }
        assertFails { rina.addCareContact(CareContactDraft(circle, "Pak RT", "Tetangga", "+6281234567891", ContactGroup.Home)) }
        assertFails { rina.addCareRecipient(circle, "Sumarni", Relation.Mother) }
        assertEquals(0, rina.renameCareRecipient(tukiman, "Rina"))
        rina.editAppointment(appt.id, appt.draft().copy(title = "Diubah"))
        rina.cancelAppointment(appt.id)
        rina.saveEmergencyInfo(tukiman, "Penisilin", "")
        rina.removeCareContact(sri.careContacts(circle).single().id)

        assertEquals("Kontrol neurologi", sri.nextAppointment(circle, now)?.title)
        assertEquals("", sri.careRecipients(circle).single().allergies)
        assertEquals(1, sri.careContacts(circle).size)
    }
}
