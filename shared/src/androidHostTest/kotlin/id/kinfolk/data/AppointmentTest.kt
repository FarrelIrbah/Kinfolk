package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AppointmentTest {

    private val now = Clock.System.now()

    @Test
    fun `a Member schedules an Appointment with a Provider and the circle sees it as next`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet())
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")

        sri.scheduleAppointment(
            AppointmentDraft(
                circle, tukiman.id, rao.id, "Kontrol neurologi", "Peninsula Neurology, Suite 204",
                startsAt = now + 3.hours, departsAt = now + 2.hours,
                bring = "Daftar obat, hasil MRI Juni, kartu asuransi, catatan tekanan darah.",
            ),
        )

        val next = sri.nextAppointment(circle, since = now)!!
        assertEquals("Kontrol neurologi", next.title)
        assertEquals("Dr. Anand Rao", next.provider.name)
        assertEquals("Peninsula Neurology, Suite 204", next.location)
        assertEquals("Daftar obat, hasil MRI Juni, kartu asuransi, catatan tekanan darah.", next.bring)
        assertEquals((now + 3.hours).epochSeconds, next.startsAt.epochSeconds)
    }

    @Test
    fun `the next Appointment is the earliest upcoming one that isn't cancelled`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        fun at(title: String, startsAt: kotlin.time.Instant) = AppointmentDraft(circle, tukiman.id, rao.id, title, null, startsAt)

        sri.scheduleAppointment(at("Kemarin", now - 1.days))
        sri.scheduleAppointment(at("Lusa", now + 2.days))
        val tomorrow = sri.scheduleAppointment(at("Besok", now + 1.days))
        sri.scheduleAppointment(at("Nanti", now + 2.hours)).also { sri.cancelAppointment(it.id) }

        assertEquals("Besok", sri.nextAppointment(circle, since = now)?.title)
        sri.cancelAppointment(tomorrow.id)
        assertEquals("Lusa", sri.nextAppointment(circle, since = now)?.title)
        assertEquals("Kemarin", sri.nextAppointment(circle, since = now - 2.days)?.title)
    }

    @Test
    fun `an Appointment can be edited, including its Provider, time and Bawa`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        val lim = sri.addProvider(circle, "dr. Lim")
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, rao.id, "Kontrol", null, now + 1.days))

        sri.editAppointment(appt.id, appt.draft().copy(providerId = lim.id, startsAt = now + 2.days, bring = "Kartu BPJS"))

        val edited = sri.nextAppointment(circle, since = now)!!
        assertEquals("dr. Lim", edited.provider.name)
        assertEquals("Kartu BPJS", edited.bring)
        assertEquals((now + 2.days).epochSeconds, edited.startsAt.epochSeconds)

        sri.editAppointment(appt.id, edited.draft().copy(bring = ""))
        assertEquals("", sri.nextAppointment(circle, since = now)!!.bring)
    }

    @Test
    fun `the Driver and the Attendee may be the same Member, or two different ones`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val budi = joinedMember(circle)
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, rao.id, "Kontrol", null, now + 1.days, driverId = budi, attendeeId = budi))

        assertEquals(budi to budi, sri.nextAppointment(circle, now)!!.let { it.driverId to it.attendeeId })

        sri.editAppointment(appt.id, appt.draft().copy(attendeeId = sri.me()))
        assertEquals(budi to sri.me(), sri.nextAppointment(circle, now)!!.let { it.driverId to it.attendeeId })

        sri.editAppointment(appt.id, appt.draft().copy(driverId = null, attendeeId = null))
        assertEquals(null to null, sri.nextAppointment(circle, now)!!.let { it.driverId to it.attendeeId })
    }

    @Test
    fun `only a Member of the Care Circle can be Driver or Attendee`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val stranger = signedInNewcomer().me()
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        val draft = AppointmentDraft(circle, tukiman.id, rao.id, "Kontrol", null, now + 1.days)

        assertFails { sri.scheduleAppointment(draft.copy(driverId = stranger)) }
        assertFails { sri.scheduleAppointment(draft.copy(attendeeId = stranger)) }
        assertNull(sri.nextAppointment(circle, now))
    }

    @Test
    fun `a Provider's history follows the doctor across locations, leaving out cancelled visits`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single()
        val drBudi = sri.addProvider(circle, "dr. Budi")
        val lim = sri.addProvider(circle, "dr. Lim")
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, drBudi.id, "Kontrol", "RS Kariadi", now - 30.days))
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, lim.id, "Mata", "RS Kariadi", now - 20.days))
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, drBudi.id, "Kontrol", "Klinik Pandanaran", now + 5.days))
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, drBudi.id, "Batal", "RS Elisabeth", now + 9.days)).also { sri.cancelAppointment(it.id) }

        assertEquals(listOf("Klinik Pandanaran", "RS Kariadi"), sri.appointmentsWith(drBudi.id).map { it.location })
        assertEquals(listOf("dr. Budi", "dr. Lim"), sri.providers(circle).map { it.name })
    }

    @Test
    fun `a Member of another Care Circle can't see, schedule, edit or cancel its Appointments`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, rao.id, "Kontrol", null, now + 1.days))
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        assertNull(rudi.nextAppointment(circle, now))
        assertTrue(rudi.providers(circle).isEmpty())
        assertTrue(rudi.appointmentsWith(rao.id).isEmpty())
        assertFails { rudi.addProvider(circle, "dr. Palsu") }
        assertFails { rudi.scheduleAppointment(AppointmentDraft(circle, tukiman.id, rao.id, "Palsu", null, now + 1.days)) }
        rudi.editAppointment(appt.id, appt.draft().copy(title = "Palsu"))
        rudi.cancelAppointment(appt.id)

        assertEquals("Kontrol", sri.nextAppointment(circle, now)?.title)
    }
}
