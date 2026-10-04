package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class OfflineTest {
    init { Providers } // start the fake providers before anything is sent

    private val now = Clock.System.now()
    private val day = LocalDate(2026, 10, 1)

    @Test
    fun `Budi keeps the next Appointment, Medications and Emergency Info on his phone, and reads them back without a connection`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman, rao, "Kontrol neurologi", "RS Siloam", now + 2.days, driverId = budi.me()))
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman, rao, "Kontrol lagi", null, now + 30.days))
        val next = budi.nextAppointment(circle, now)!!
        budi.askQuestion(circle, next.id, "Boleh menyetir lagi?")
        sri.giveDose(sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0))), day)
        sri.saveEmergencyInfo(tukiman, EmergencyDraft(null, null, "Penisilin", "", "Stroke iskemik"))
        sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))

        val kept = Json.encodeToString(budi.snapshot(since = now, today = day)!!)
        val offline = Json.decodeFromString<Snapshot>(kept)

        assertEquals("Tukiman", offline.circle.name)
        assertEquals("Kontrol neurologi", offline.next!!.title)
        assertEquals(budi.me(), offline.next.driverId)
        assertEquals(listOf("Boleh menyetir lagi?"), offline.questions.map { it.text })
        assertEquals(listOf("Clopidogrel"), offline.medications.map { it.name })
        assertEquals("Penisilin", offline.recipient!!.allergies)
        assertEquals(listOf("Clopidogrel 75 mg"), offline.emergency?.medications)
        assertEquals(listOf("Dr. Anand Rao"), offline.contacts.filter { it.emergency }.map { it.name })
        assertEquals(budi.emergencyCard(tukiman).url, offline.card!!.url)
        assertEquals(listOf("Sri", "Budi"), offline.members.map { it.name })
        assertEquals(listOf(sri.me()), offline.doses.map { it.givenBy })
    }

    @Test
    fun `Dewi's phone never keeps what is hidden from her, except the Medications Emergency Info always shows`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        sri.scheduleAppointment(AppointmentDraft(circle, tukiman, rao, "Kontrol psikiatri", null, now + 2.days))
        sri.addMedication(MedicationDraft(circle, tukiman, "Sertraline", "50 mg", "pagi", LocalTime(7, 0)))
        sri.setHidden(tukiman, dewi.me(), DataCategory.appointments, hidden = true)
        sri.setHidden(tukiman, dewi.me(), DataCategory.medications, hidden = true)

        val offline = dewi.snapshot(since = now, today = day)!!

        assertNull(offline.next)
        assertTrue(offline.medications.isEmpty())
        assertTrue(offline.timeline.all { it.kind == TimelineEntry.Kind.access_change })
        assertEquals(listOf("Sertraline 50 mg"), offline.emergency?.medications)
    }

    @Test
    fun `what Budi wrote offline is sent in order once reachable, and sending it again changes nothing`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budi = signedInSibling(sri, circle)
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        val visit = sri.scheduleAppointment(AppointmentDraft(circle, tukiman.id, rao, "Kontrol neurologi", null, now + 2.days))
        val clopidogrel = sri.addMedication(MedicationDraft(circle, tukiman.id, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val omeprazole = sri.addMedication(MedicationDraft(circle, tukiman.id, "Omeprazole", "20 mg", "pagi", LocalTime(7, 0)))
        val task = sri.addTask(tukiman, "Perpanjang izin parkir", budi.me(), day)

        // Kept on the phone, as the app keeps them, then sent twice: a retry after a reply that never arrived.
        val pending = Json.decodeFromString<List<Write>>(Json.encodeToString(listOf(
            Write.Doses(listOf(clopidogrel, omeprazole), day, give = true),
            Write.Doses(listOf(omeprazole), day, give = false), // the tap undone, after it
            Write.SaveCheckIn(tukiman, day, CheckInDraft(128, 80, Ate.yes, walked = true, Mood.good, "")),
            Write.AddNote(circle, "Bapak minta radio lamanya.", private = false),
            Write.TaskDone(task.id, done = true),
            Write.Ask(circle, visit.id, "Boleh menyetir lagi?"),
        )))
        pending.forEach { budi.send(it) }
        pending.forEach { budi.send(it) }

        assertEquals(listOf(clopidogrel.id), budi.doseLogs(circle, day).map { it.medicationId })
        assertEquals(128, budi.checkIn(tukiman.id, day)!!.sys)
        assertEquals(listOf("Bapak minta radio lamanya."), budi.notes(circle).map { it.text })
        assertTrue(budi.tasks(circle).single { it.id == task.id }.done)
        assertEquals(listOf("Boleh menyetir lagi?"), budi.questions(visit.id).map { it.text })
    }

    @Test
    fun `a write the server refuses is dropped rather than sent forever`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val budi = signedInSibling(sri, circle)
        sri.removeMember(circle, budi.me())

        budi.send(Write.AddNote(circle, "Bapak minta radio lamanya.", private = false)) // doesn't throw, so the queue moves on

        assertTrue(sri.notes(circle).isEmpty())
    }

    @Test
    fun `a Former Member has nothing left to keep`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val budi = signedInSibling(sri, circle)
        assertTrue(budi.snapshot(since = now, today = day) != null)

        sri.removeMember(circle, budi.me())

        assertNull(budi.snapshot(since = now, today = day))
    }
}
