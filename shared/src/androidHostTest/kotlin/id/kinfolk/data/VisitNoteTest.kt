package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class VisitNoteTest {

    private val now = Clock.System.now()

    private class Visit(val sri: io.github.jan.supabase.SupabaseClient, val circle: String, val recipient: String, val provider: String) {
        suspend fun at(startsAt: Instant, location: String? = null, attendee: String? = null, provider: String = this.provider) =
            sri.scheduleAppointment(AppointmentDraft(circle, recipient, provider, "Kontrol neurologi", location, startsAt, attendeeId = attendee)).id
    }

    private suspend fun visit(): Visit {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        return Visit(sri, circle, sri.careRecipients(circle).single().id, sri.addProvider(circle, "Dr. Anand Rao").id)
    }

    @Test
    fun `Members add Questions to an upcoming Appointment and everyone sees who asked`() = runBlocking {
        val v = visit()
        val budi = signedInSibling(v.sri, v.circle)
        val appt = v.at(now + 1.days)

        v.sri.askQuestion(v.circle, appt, "Apakah kaki kanan yang terseret masih membaik atau sudah mentok?")
        budi.askQuestion(v.circle, appt, "Kapan Bapak boleh menyetir lagi?")

        val qs = v.sri.questions(appt)
        assertEquals(listOf("Sri" to "Apakah kaki kanan yang terseret masih membaik atau sudah mentok?", "Budi" to "Kapan Bapak boleh menyetir lagi?"), qs.map { it.askedByName to it.text })
        assertTrue(qs.none { it.carried || it.answer != null })
        assertEquals(qs, budi.questions(appt))
    }

    @Test
    fun `only the Attendee writes the Visit Note, Members read it, and it can be edited after saving`() = runBlocking {
        val v = visit()
        val budi = signedInSibling(v.sri, v.circle)
        val appt = v.at(now, attendee = budi.me())
        v.sri.askQuestion(v.circle, appt, "Boleh berhenti clopidogrel sebelum perawatan gigi Oktober?")
        val q = v.sri.questions(appt).single()

        assertFails { v.sri.saveVisitNote(appt, mapOf(q.id to "Boleh"), emptyList(), "") }
        assertNull(v.sri.visitNote(appt))

        budi.saveVisitNote(appt, mapOf(q.id to "Jangan dihentikan."), budi.steps("Fisioterapi dua kali seminggu"), "Tensi 130/85.")
        val note = v.sri.visitNote(appt)!!
        assertEquals(Triple(appt, listOf("Fisioterapi dua kali seminggu"), "Tensi 130/85."), Triple(note.appointmentId, note.steps.map { it.text }, note.notes))
        assertEquals("Jangan dihentikan.", v.sri.questions(appt).single().answer)

        budi.saveVisitNote(appt, mapOf(q.id to "Jangan dihentikan untuk gigi."), budi.steps("Fisioterapi dua kali seminggu", "MRI ulang 3 bulan lagi"), "")
        assertEquals(listOf("Fisioterapi dua kali seminggu", "MRI ulang 3 bulan lagi"), v.sri.visitNote(appt)!!.steps.map { it.text })
        assertEquals("Jangan dihentikan untuk gigi.", v.sri.questions(appt).single().answer)
        assertFails { v.sri.saveVisitNote(appt, mapOf(q.id to "Boleh"), emptyList(), "") }
        assertEquals("Jangan dihentikan untuk gigi.", v.sri.questions(appt).single().answer)
    }

    @Test
    fun `an unanswered Question carries over to the next Appointment with the same Provider, even at another place`() = runBlocking {
        val v = visit()
        val lim = v.sri.addProvider(v.circle, "dr. Lim")
        val today = v.at(now, "Peninsula Neurology", attendee = v.sri.me())
        v.at(now + 3.days, provider = lim.id)
        v.at(now + 5.days, "Peninsula Neurology").also { v.sri.cancelAppointment(it) }
        val next = v.at(now + 10.days, "RS Kariadi")
        v.sri.askQuestion(v.circle, today, "Boleh berhenti clopidogrel?")
        v.sri.askQuestion(v.circle, today, "Kapan Bapak boleh menyetir lagi?")
        val (clopidogrel, driving) = v.sri.questions(today)
        // Before the visit, nothing is carried yet.
        assertTrue(v.sri.questions(next).isEmpty())

        v.sri.saveVisitNote(today, mapOf(clopidogrel.id to "Jangan dihentikan.", driving.id to ""), emptyList(), "")

        assertEquals(listOf("Jangan dihentikan." to false, "" to false), v.sri.questions(today).map { it.answer to it.carried })
        val carried = v.sri.questions(next).single()
        assertEquals("Kapan Bapak boleh menyetir lagi?", carried.text)
        assertTrue(carried.carried)
        assertEquals(today, carried.askedIn)
        assertEquals(now.epochSeconds, carried.askedFor.epochSeconds)
        assertNull(carried.answer)
    }

    @Test
    fun `a carried Question keeps carrying until it is answered`() = runBlocking {
        val v = visit()
        val first = v.at(now - 20.days, attendee = v.sri.me())
        val second = v.at(now, attendee = v.sri.me())
        val third = v.at(now + 20.days)
        v.sri.askQuestion(v.circle, first, "Kapan Bapak boleh menyetir lagi?")
        val q = v.sri.questions(first).single()

        v.sri.saveVisitNote(first, mapOf(q.id to ""), emptyList(), "")
        assertEquals(first, v.sri.questions(second).single().askedIn)
        v.sri.saveVisitNote(second, mapOf(q.id to "Belum, tunggu 3 bulan."), emptyList(), "")

        assertEquals("Belum, tunggu 3 bulan.", v.sri.questions(second).single().answer)
        assertEquals("", v.sri.questions(first).single().answer)
        assertTrue(v.sri.questions(third).isEmpty())
    }

    @Test
    fun `a Visit Note answers only its own Questions, and one asked while it was being written counts as unanswered`() = runBlocking {
        val v = visit()
        val lim = v.sri.addProvider(v.circle, "dr. Lim")
        val today = v.at(now, attendee = v.sri.me())
        val eyes = v.at(now + 3.days, provider = lim.id)
        v.sri.askQuestion(v.circle, eyes, "Perlu kacamata baru?")
        val other = v.sri.questions(eyes).single()
        v.sri.askQuestion(v.circle, today, "Kapan Bapak boleh menyetir lagi?")
        val late = v.sri.questions(today).single() // asked after the form opened with nothing on it

        v.sri.saveVisitNote(today, mapOf(other.id to "Ya"), emptyList(), "")

        assertEquals(listOf(late.id to ""), v.sri.questions(today).map { it.id to it.answer })
        assertNull(v.sri.questions(eyes).single().answer)
    }

    @Test
    fun `a Member of another Care Circle can't see or add Questions or read the Visit Note`() = runBlocking {
        val v = visit()
        val appt = v.at(now, attendee = v.sri.me())
        v.sri.askQuestion(v.circle, appt, "Kapan Bapak boleh menyetir lagi?")
        val q = v.sri.questions(appt).single()
        v.sri.saveVisitNote(appt, mapOf(q.id to ""), v.sri.steps("MRI ulang"), "")
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        assertTrue(rudi.questions(appt).isEmpty())
        assertNull(rudi.visitNote(appt))
        assertFails { rudi.askQuestion(v.circle, appt, "Palsu") }
        assertFails { rudi.saveVisitNote(appt, mapOf(q.id to "Palsu"), emptyList(), "") }
        assertFalse(v.sri.questions(appt).single().answer!!.isNotEmpty())
    }
}
