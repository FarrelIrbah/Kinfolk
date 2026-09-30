package id.kinfolk.data

import kotlinx.coroutines.runBlocking
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
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        sri.saveEmergencyInfo(tukiman, allergies = "Penisilin", conditions = "Stroke iskemik")
        sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))

        val kept = Json.encodeToString(budi.snapshot(since = now)!!)
        val offline = Json.decodeFromString<Snapshot>(kept)

        assertEquals("Tukiman", offline.circle.name)
        assertEquals("Kontrol neurologi", offline.next!!.title)
        assertEquals(budi.me(), offline.next.driverId)
        assertEquals(listOf("Boleh menyetir lagi?"), offline.questions.map { it.text })
        assertEquals(listOf("Clopidogrel"), offline.medications.map { it.name })
        assertEquals("Penisilin", offline.recipient!!.allergies)
        assertEquals(listOf("Clopidogrel"), offline.emergencyMedications.map { it.name })
        assertEquals(listOf("Dr. Anand Rao"), offline.contacts.filter { it.emergency }.map { it.name })
        assertEquals(budi.emergencyCard(tukiman).url, offline.card!!.url)
        assertEquals(listOf("Sri", "Budi"), offline.members.map { it.name })
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

        val offline = dewi.snapshot(since = now)!!

        assertNull(offline.next)
        assertTrue(offline.medications.isEmpty())
        assertTrue(offline.timeline.isEmpty())
        assertEquals(listOf("Sertraline"), offline.emergencyMedications.map { it.name })
    }

    @Test
    fun `a Former Member has nothing left to keep`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val budi = signedInSibling(sri, circle)
        assertTrue(budi.snapshot(since = now) != null)

        sri.removeMember(circle, budi.me())

        assertNull(budi.snapshot(since = now))
    }
}
