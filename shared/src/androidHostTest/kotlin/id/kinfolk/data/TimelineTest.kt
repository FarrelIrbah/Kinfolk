package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class TimelineTest {

    private val now = Clock.System.now()

    @Test
    fun `Members see Appointments and Visit Notes newest first, with who and when, but not cancelled ones`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val recipient = sri.careRecipients(circle).single().id
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        val budi = signedInSibling(sri, circle)

        val neuro = sri.scheduleAppointment(AppointmentDraft(circle, recipient, rao, "Kontrol neurologi", null, now, attendeeId = budi.me())).id
        budi.scheduleAppointment(AppointmentDraft(circle, recipient, rao, "Fisioterapi", null, now + 7.days)).id
            .also { sri.cancelAppointment(it) }
        budi.saveVisitNote(neuro, emptyMap(), budi.steps("Fisioterapi 2x/minggu", "MRI ulang 3 bulan lagi"), "Tensi 130/85.")

        val entries = sri.timeline(circle)
        assertEquals(
            listOf(
                Triple(TimelineEntry.Kind.visit_note, "Budi", neuro),
                Triple(TimelineEntry.Kind.appointment, "Sri", neuro),
            ),
            entries.map { Triple(it.kind, it.byName, it.appointmentId) },
        )
        val (note, appt) = entries
        assertEquals(budi.me(), note.by)
        assertEquals(listOf("Fisioterapi 2x/minggu", "MRI ulang 3 bulan lagi"), note.nextSteps)
        assertEquals("Tensi 130/85.", note.notes)
        assertEquals(sri.me(), appt.by)
        assertEquals("Kontrol neurologi" to "Dr. Anand Rao", appt.title to appt.provider)
        assertTrue(appt.at <= note.at && note.at <= Clock.System.now())
        assertEquals(entries, budi.timeline(circle))
        assertTrue(signedInNewcomer().timeline(circle).isEmpty())

        // A new Attendee saving again takes the Visit Note over; the Appointment keeps who scheduled it.
        val a = sri.appointment(neuro)!!
        budi.editAppointment(neuro, a.draft().copy(attendeeId = sri.me()))
        sri.saveVisitNote(neuro, emptyMap(), sri.steps("MRI ulang 3 bulan lagi"), "")
        assertEquals(listOf("Sri", "Sri"), sri.timeline(circle).map { it.byName })
    }

    @Test
    fun `entries by a Former Member still appear with their name`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val recipient = sri.careRecipients(circle).single().id
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        val budi = signedInSibling(sri, circle)
        val appt = budi.scheduleAppointment(AppointmentDraft(circle, recipient, rao, "Kontrol neurologi", null, now - 1.days, attendeeId = budi.me())).id
        budi.saveVisitNote(appt, emptyMap(), emptyList(), "Tensi 130/85.")

        sri.removeMember(circle, budi.me())

        assertEquals(listOf("Budi", "Budi"), sri.timeline(circle).map { it.byName })
        assertTrue(budi.timeline(circle).isEmpty())
    }
}
