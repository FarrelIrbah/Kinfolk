package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TaskTest {
    init { Providers } // start the fake providers before anything is sent

    private val wib = TimeZone.of("Asia/Jakarta")
    private val today = Clock.System.todayIn(wib)

    /** The minute-by-minute job at [at]: queues what is due (refill Tasks too), then sends what is waiting. */
    private fun job(at: Instant = Clock.System.now()) = assertEquals(
        200,
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp")).header("authorization", "Bearer local")
                .POST(HttpRequest.BodyPublishers.ofString("""{"at":"$at"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode(),
    )

    private suspend fun pastVisit(sri: io.github.jan.supabase.SupabaseClient, circle: String, attendee: String): String {
        val tukiman = sri.careRecipients(circle).single()
        val rao = sri.addProvider(circle, "Dr. Anand Rao")
        return sri.scheduleAppointment(
            AppointmentDraft(circle, tukiman.id, rao.id, "Kontrol neurologi", "RS Panti Rapih", Clock.System.now() - 1.days, attendeeId = attendee),
        ).id
    }

    @Test
    fun `a Next Step saved with owner and due is a Task, updated and removed with it`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val budi = signedInSibling(sri, circle)
        val visit = pastVisit(sri, circle, sri.me())
        val due = today + DatePeriod(days = 7)

        sri.saveVisitNote(visit, emptyMap(), listOf(NextStepDraft("Fisioterapi 2x/minggu", budi.me(), due), NextStepDraft("MRI ulang", sri.me(), due)), "")
        val physio = budi.tasks(circle).single { it.text == "Fisioterapi 2x/minggu" }
        assertEquals(budi.me(), physio.ownerId)
        assertEquals(due, physio.due)
        assertEquals(Task.Source.step, physio.source)
        assertEquals("Kontrol neurologi", physio.fromTitle)
        assertEquals(sri.me(), physio.addedBy)
        budi.markTaskDone(physio.id, true)

        // Steps come back with their Task ids: changing one keeps it (and done), emptying one removes it.
        val steps = sri.visitNote(visit)!!.steps
        assertEquals(listOf("Fisioterapi 2x/minggu", "MRI ulang"), steps.map { it.text })
        val later = due + DatePeriod(days = 3)
        sri.saveVisitNote(visit, emptyMap(), listOf(NextStepDraft("Fisioterapi 3x/minggu", sri.me(), later, steps[0].id)), "")
        val kept = budi.tasks(circle).single()
        assertEquals(listOf(physio.id, "Fisioterapi 3x/minggu", sri.me(), later, true), listOf(kept.id, kept.text, kept.ownerId, kept.due, kept.done))

        // The Task form changes the Next Step too, so the Visit Note and the Timeline agree.
        budi.editTask(kept.id, "Fisioterapi 2x/minggu di RS", budi.me(), later)
        assertEquals(listOf("Fisioterapi 2x/minggu di RS"), sri.visitNote(visit)!!.steps.map { it.text })
        assertEquals(
            listOf("Fisioterapi 2x/minggu di RS"),
            sri.timeline(circle).single { it.kind == TimelineEntry.Kind.visit_note }.nextSteps,
        )
    }

    @Test
    fun `Members add, change and finish Tasks, viewers and outsiders can not`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budi = signedInSibling(sri, circle)
        val viewer = signedInSibling(sri, circle, Role.viewer)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        val t = budi.addTask(tukiman, "Perpanjang izin parkir disabilitas", budi.me(), today)
        assertEquals(listOf(Task.Source.added, budi.me()), listOf(t.source, t.addedBy))
        sri.editTask(t.id, "Perpanjang izin parkir", sri.me(), today + DatePeriod(days = 1))
        sri.markTaskDone(t.id, true)
        val done = viewer.tasks(circle).single()
        assertEquals(listOf("Perpanjang izin parkir", sri.me(), true, sri.me()), listOf(done.text, done.ownerId, done.done, done.doneBy))
        assertNotNull(done.doneAt)
        sri.markTaskDone(t.id, false) // "Urungkan"
        assertEquals(false, budi.tasks(circle).single().done)

        assertTrue(rudi.tasks(circle).isEmpty())
        assertFails { rudi.addTask(tukiman, "Palsu", rudi.me(), today) }
        assertFails { viewer.addTask(tukiman, "Palsu", viewer.me(), today) }
        viewer.markTaskDone(t.id, true) // silently nothing
        assertEquals(false, sri.tasks(circle).single().done)
    }

    @Test
    fun `the job adds a refill Task 4 days before, once, for whoever refills it`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        sri.setHidden(tukiman.id, dewi.me(), DataCategory.medications, hidden = true)
        val refill = today + DatePeriod(days = 30)
        sri.addMedication(
            MedicationDraft(circle, tukiman.id, "Omeprazole", "20 mg", "pagi", LocalTime(7, 0), refillOn = refill, refillBy = budi.me()),
        )
        sri.addMedication(MedicationDraft(circle, tukiman.id, "Amlodipine", "5 mg", "pagi", LocalTime(7, 0), refillOn = refill)) // nobody refills it
        fun at(day: kotlinx.datetime.LocalDate) = LocalDateTime(day, LocalTime(8, 0)).toInstant(wib)

        job(at(refill - DatePeriod(days = 5)))
        assertTrue(budi.tasks(circle).none { it.source == Task.Source.refill })

        job(at(refill - DatePeriod(days = 4)))
        job(at(refill - DatePeriod(days = 3)))
        val t = budi.tasks(circle).single { it.source == Task.Source.refill }
        assertEquals(listOf("Ambil isi ulang Omeprazole", budi.me(), refill), listOf(t.text, t.ownerId, t.due))
        assertTrue(dewi.tasks(circle).isEmpty()) // follows Obat
    }

    @Test
    fun `Ingatkan sends the owner the WhatsApp template once`() = runBlocking<Unit> {
        val sriPhone = newNumber()
        val sri = signedInAs(sriPhone)
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budiPhone = newNumber()
        sri.invite(circle, "Budi", budiPhone)
        val budi = signedInAs(budiPhone).apply { acceptInvitation(myInvitations().single().id) }
        val due = kotlinx.datetime.LocalDate(2026, 10, 6)
        val t = sri.addTask(tukiman, "Perpanjang izin parkir disabilitas", budi.me(), due)

        assertFails { budi.remindTask(t.id) } // not your own
        sri.remindTask(t.id)
        assertFails { sri.remindTask(t.id) } // "Diingatkan": once
        assertNotNull(budi.tasks(circle).single().remindedAt)
        job()

        val sent = Providers.to(budiPhone).filter { "kinfolk_task_reminder" in it.text }
        assertEquals(1, sent.size)
        for (p in listOf("Sri", "Perpanjang izin parkir disabilitas", "6 Okt")) assertTrue(""""text":"$p"""" in sent.single().text, p)
    }
}
