package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class DataCategoryTest {
    init { Providers } // start the fake providers before anything is sent

    private val now = Clock.System.now()

    /** Sri's Care Circle for Tukiman, with a past visit that has a Visit Note, an upcoming one, and a Medication. */
    private class Family(val sri: SupabaseClient, val circle: String, val tukiman: String, val past: String, val upcoming: String)

    private suspend fun family(): Family {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val past = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol psikiatri", null, now - 3.days, attendeeId = sri.me())).id
        sri.askQuestion(circle, past, "Apakah dosisnya perlu diubah?")
        sri.saveVisitNote(past, sri.questions(past).associate { it.id to "Tetap" }, listOf("Kontrol lagi sebulan"), "Tidur membaik")
        val upcoming = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol psikiatri", null, now + 3.days)).id
        sri.askQuestion(circle, upcoming, "Boleh menyetir lagi?")
        sri.addMedication(MedicationDraft(circle, tukiman, "Sertraline", "50 mg", "pagi", LocalTime(7, 0)))
        return Family(sri, circle, tukiman, past, upcoming)
    }

    /** Tukiman himself, invited by [admin] as the parent Member who is the Care Recipient. */
    private suspend fun Family.tukimanJoins(): SupabaseClient {
        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Tukiman", phone, Role.parent, recipientId = tukiman)
        return signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }
    }

    private suspend fun SupabaseClient.seesVisitNotes(f: Family) =
        visitNote(f.past) != null && timeline(f.circle).any { it.kind == TimelineEntry.Kind.visit_note }

    private suspend fun SupabaseClient.seesAppointments(f: Family) =
        appointment(f.upcoming) != null && timeline(f.circle).isNotEmpty() && questions(f.upcoming).isNotEmpty()

    @Test
    fun `Dewi can't read Tukiman's Visit Notes once they are hidden from her, on any screen, but still sees his Appointments`() = runBlocking<Unit> {
        val f = family()
        val dewi = signedInSibling(f.sri, f.circle)
        assertTrue(dewi.seesVisitNotes(f))

        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)

        assertNull(dewi.visitNote(f.past))
        assertTrue(dewi.timeline(f.circle).none { it.kind == TimelineEntry.Kind.visit_note })
        assertTrue(dewi.seesAppointments(f))
        // Answered Questions belong to the Visit Note, and aren't mistaken for open ones that carry over.
        assertTrue(dewi.questions(f.past).isEmpty())
        assertEquals(listOf("Boleh menyetir lagi?"), dewi.questions(f.upcoming).map { it.text })
        assertEquals(1, dewi.medications(f.circle).size)
        assertTrue(f.sri.seesVisitNotes(f))

        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = false)
        assertTrue(dewi.seesVisitNotes(f))
    }

    @Test
    fun `hiding Appointments hides their Questions, Visit Notes and Timeline entries too`() = runBlocking<Unit> {
        val f = family()
        val dewi = signedInSibling(f.sri, f.circle)

        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.appointments, hidden = true)

        assertNull(dewi.appointment(f.upcoming))
        assertNull(dewi.nextAppointment(f.circle, now - 7.days))
        assertTrue(dewi.questions(f.upcoming).isEmpty())
        assertNull(dewi.visitNote(f.past))
        assertTrue(dewi.timeline(f.circle).isEmpty())
        assertEquals(1, dewi.medications(f.circle).size)
        assertTrue(f.sri.seesAppointments(f))
    }

    @Test
    fun `hidden Medications disappear from the list but stay on Emergency Info`() = runBlocking<Unit> {
        val f = family()
        val dewi = signedInSibling(f.sri, f.circle)

        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.medications, hidden = true)

        assertTrue(dewi.medications(f.circle).isEmpty())
        assertEquals(listOf("Sertraline"), dewi.emergencyMedications(f.tukiman).map { it.name })
        assertTrue(signedInNewcomer().let { stranger -> runCatching { stranger.emergencyMedications(f.tukiman) }.getOrDefault(emptyList()) }.isEmpty())
    }

    @Test
    fun `when the Care Recipient isn't a Member, admins set restrictions, siblings can't, and admins always see everything`() = runBlocking<Unit> {
        val f = family()
        val budi = signedInSibling(f.sri, f.circle)
        val dewi = signedInSibling(f.sri, f.circle)

        assertFails { budi.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = true) }
        assertFails { f.sri.setHidden(f.tukiman, f.sri.me(), DataCategory.visit_notes, hidden = true) }
        assertTrue(f.sri.hidden(f.circle).isEmpty())

        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)
        assertEquals(listOf(Hidden(f.tukiman, dewi.me(), DataCategory.visit_notes)), budi.hidden(f.circle))

        // A promoted admin sees everything; their restriction stays for if they ever stop being one.
        f.sri.promoteToAdmin(f.circle, dewi.me())
        assertTrue(dewi.seesVisitNotes(f))
        assertFails { f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.medications, hidden = true) }
    }

    @Test
    fun `when the Care Recipient is a Member, only he sets restrictions, and admins can't override or get around them`() = runBlocking<Unit> {
        val f = family()
        val dewi = signedInSibling(f.sri, f.circle)
        val tukiman = f.tukimanJoins()
        assertEquals(tukiman.me(), f.sri.careRecipients(f.circle).single().memberId)

        tukiman.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)
        tukiman.setHidden(f.tukiman, f.sri.me(), DataCategory.medications, hidden = true)

        assertFails { f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = false) }
        assertFails { f.sri.setHidden(f.tukiman, f.sri.me(), DataCategory.medications, hidden = false) }
        assertFails { f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.medications, hidden = true) }
        assertFails { tukiman.setHidden(f.tukiman, tukiman.me(), DataCategory.visit_notes, hidden = true) }
        assertTrue(f.sri.medications(f.circle).isEmpty()) // even the admin
        assertFalse(dewi.seesVisitNotes(f))

        f.sri.promoteToAdmin(f.circle, dewi.me())
        assertFalse(dewi.seesVisitNotes(f))
        assertTrue(tukiman.seesVisitNotes(f) && tukiman.medications(f.circle).size == 1)

        // Nobody makes themselves the Care Recipient, or adds one that is them.
        assertFails { dewi.from("care_recipients").update(buildJsonObject { put("member_id", dewi.me()) }) { filter { eq("id", f.tukiman) } } }
        assertFails { dewi.from("care_recipients").insert(buildJsonObject { put("circle_id", f.circle); put("name", "Dewi"); put("member_id", dewi.me()) }) }
        assertEquals(tukiman.me(), f.sri.careRecipients(f.circle).single().memberId)
    }

    @Test
    fun `once the Care Recipient leaves, the admins take over his restrictions`() = runBlocking<Unit> {
        val f = family()
        val dewi = signedInSibling(f.sri, f.circle)
        val tukiman = f.tukimanJoins()
        tukiman.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)
        tukiman.setHidden(f.tukiman, f.sri.me(), DataCategory.visit_notes, hidden = true)

        tukiman.leaveCareCircle(f.circle)

        assertTrue(f.sri.seesVisitNotes(f))
        assertFalse(dewi.seesVisitNotes(f))
        f.sri.setHidden(f.tukiman, dewi.me(), DataCategory.visit_notes, hidden = false)
        assertTrue(dewi.seesVisitNotes(f))
    }

    @Test
    fun `with Bapak atur per orang on onb3, siblings who join start without Visit Notes, admins and the Care Recipient don't`() = runBlocking<Unit> {
        val f = family()
        val budi = signedInSibling(f.sri, f.circle) // joined before the choice

        assertFails { budi.hideByDefault(f.circle, setOf(DataCategory.visit_notes)) }
        f.sri.hideByDefault(f.circle, setOf(DataCategory.visit_notes))
        val phone = newNumber()
        Providers.to(phone)
        f.sri.invite(f.circle, "Dewi", phone)
        val dewi = signedInAs(phone)
        assertEquals(listOf(DataCategory.visit_notes), dewi.myInvitations().single().hidden)
        dewi.acceptInvitation(dewi.myInvitations().single().id)
        val tukiman = f.tukimanJoins()

        assertFalse(dewi.seesVisitNotes(f))
        assertTrue(dewi.seesAppointments(f) && dewi.medications(f.circle).isNotEmpty())
        assertTrue(budi.seesVisitNotes(f))
        assertTrue(tukiman.seesVisitNotes(f))
        assertTrue(f.sri.hidden(f.circle).none { it.memberId == tukiman.me() })
    }

    @Test
    fun `nothing stays hidden from someone who comes back as the Care Recipient`() = runBlocking<Unit> {
        val f = family()
        f.sri.hideByDefault(f.circle, setOf(DataCategory.visit_notes))
        val phone = newNumber()
        Providers.to(phone)
        f.sri.invite(f.circle, "Tukiman", phone)
        val tukiman = signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }
        assertFalse(tukiman.seesVisitNotes(f))
        tukiman.leaveCareCircle(f.circle)

        f.sri.invite(f.circle, "Tukiman", phone, Role.parent, recipientId = f.tukiman)
        tukiman.acceptInvitation(tukiman.myInvitations().single().id)

        assertTrue(tukiman.seesVisitNotes(f))
    }

    @Test
    fun `a Care Recipient can only be invited as the parent Member once, and only by an admin`() = runBlocking<Unit> {
        val f = family()
        val budi = signedInSibling(f.sri, f.circle)
        assertFails { budi.invite(f.circle, "Tukiman", newNumber(), Role.parent, recipientId = f.tukiman) }
        assertFails { f.sri.invite(f.circle, "Tukiman", newNumber(), Role.parent) }
        f.tukimanJoins()
        assertNotNull(f.sri.careRecipients(f.circle).single().memberId)
        assertFails { f.sri.invite(f.circle, "Tukiman", newNumber(), Role.parent, recipientId = f.tukiman) }
    }
}
