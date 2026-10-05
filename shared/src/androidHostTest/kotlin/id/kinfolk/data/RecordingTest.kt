package id.kinfolk.data

import id.kinfolk.ui.appointment.dayMonth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RecordingTest {
    init { Providers } // start the fake providers before anything is sent

    private val audio = "ftyp-m4a kunjungan".encodeToByteArray()

    /** The worker's answer for a visit: two lines, the second flagged, a Q&A, a step for Budi and a dose change. */
    private val output = """{
        "segments": [
          {"t": 0.0, "speaker": "provider", "text": "Clopidogrel diturunkan jadi 37,5 mg."},
          {"t": 6.5, "speaker": "attendee", "text": "Boleh berhenti sebelum cabut gigi?", "flagged": true}
        ],
        "qa": [{"question": "Boleh berhenti clopidogrel sebelum perawatan gigi?", "answer": "Jangan dihentikan.", "segments": [1]}],
        "next_steps": [{"text": "Antar fisioterapi Selasa", "owner": "budi", "due": "2026-10-13"}, {"text": "MRI ulang", "owner": null, "due": null}],
        "medication": {"name": "clopidogrel", "dose": "37,5 mg", "segment": 0}
    }"""

    private fun io.github.jan.supabase.SupabaseClient.inbox() = Providers.to(auth.currentUserOrNull()!!.phone!!)
    private val Providers.Message.template get() = Regex(""""template":\{"name":"(\w+)"""").find(text)?.groupValues?.get(1)
    private val Providers.Message.params get() = Regex(""""type":"text","text":"((?:[^"\\]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.toList()

    /** The minute-by-minute WhatsApp job: sends what is waiting. */
    private fun deliver() {
        val req = HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp")).header("authorization", "Bearer local")
            .POST(HttpRequest.BodyPublishers.ofString("""{"at":"${Clock.System.now()}"}""")).build()
        assertEquals(200, HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).statusCode())
    }

    private suspend fun io.github.jan.supabase.SupabaseClient.awaitRecording(appointment: String): Recording {
        repeat(100) { recording(appointment)?.takeIf { it.status != Recording.Status.processing }?.let { return it }; delay(100) }
        error("still processing")
    }

    @Test
    fun `the Attendee alone reads the transcript until they share it, then it follows Rekaman kunjungan`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle, name = "Dewi")
        val clopidogrel = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me())).id

        Providers.transcript = output
        assertFails { sri.transcribe(circle, appt, audio) } // only the Attendee records
        budi.transcribe(circle, appt, audio)
        val job = Providers.jobs.last()
        assertTrue("recordings/$circle/$appt" in job && "Clopidogrel" in job && "Budi" in job, job)

        val ready = budi.awaitRecording(appt)
        assertEquals(Recording.Status.ready, ready.status)
        val t = ready.transcript!!
        assertEquals(listOf(Speaker.provider to false, Speaker.attendee to true), t.segments.map { it.speaker to it.flagged })
        assertEquals(listOf(1), t.qa.single().segments)
        assertEquals(listOf(budi.me(), null), t.steps.map { it.ownerId })
        assertEquals(LocalDate(2026, 10, 13), t.steps.first().due)
        assertEquals(clopidogrel.id, t.medication!!.medicationId)

        assertNull(sri.recording(appt))
        assertNull(sri.visitNote(appt)) // nothing of the summary reaches the circle before sharing
        assertTrue(sri.search(circle, "cabut").isEmpty())
        assertEquals(listOf(Hit.Kind.transcript to 1), budi.search(circle, "cabut").map { it.kind to it.segment })
        assertFails { sri.shareRecording(appt, emptyMap(), emptyList()) }
        sri.setHidden(tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)
        budi.shareRecording(appt, emptyMap(), t.drafts(budi.me(), LocalDate(2026, 10, 12)))
        assertEquals(t, sri.recording(appt)!!.transcript)
        assertEquals(listOf("00:06 · Budi · Kontrol neurologi"), sri.search(circle, "cabut").map { it.label })
        assertNull(dewi.recording(appt))
        assertTrue(dewi.search(circle, "cabut").isEmpty())
        assertFails { budi.shareRecording(appt, emptyMap(), emptyList()) } // once
    }

    @Test
    fun `sharing saves the summary as the Visit Note and tells the circle on WhatsApp, not who can't see recordings`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle, name = "Dewi")
        val rina = signedInSibling(sri, circle, name = "Rina")
        sri.setHidden(tukiman, rina.me(), DataCategory.visit_notes, hidden = true)
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me())).id
        sri.askQuestion(circle, appt, "Boleh berhenti clopidogrel sebelum perawatan gigi?")
        dewi.askQuestion(circle, appt, "Kapan Bapak boleh menyetir lagi?")

        Providers.transcript = output
        budi.transcribe(circle, appt, audio, seconds = 252)
        val t = budi.awaitRecording(appt).also { assertEquals(252, it.seconds) }.transcript!!
        val questions = budi.questions(appt)
        val answers = t.answersTo(questions)
        assertEquals(listOf("Jangan dihentikan.", ""), questions.map { answers[it.id] })
        val steps = t.drafts(budi.me(), LocalDate(2026, 10, 12)).let { (a, b) -> listOf(a, b.copy(owner = dewi.me())) }
        assertEquals(listOf("Sri", "Dewi"), budi.shareRecording(appt, answers, steps))
        assertEquals(listOf("Sri", "Dewi"), sri.recording(appt)!!.told)
        deliver()

        val note = sri.visitNote(appt)!!
        assertEquals(listOf(Triple("Antar fisioterapi Selasa", budi.me(), LocalDate(2026, 10, 13)), Triple("MRI ulang", dewi.me(), LocalDate(2026, 10, 12))),
            note.steps.map { Triple(it.text, it.owner, it.due) })
        val message = listOf(listOf("Budi", "Tukiman", "Kontrol neurologi: Antar fisioterapi Selasa, MRI ulang"))
        assertEquals(message, dewi.inbox().filter { it.template == "kinfolk_visit_note" }.map { it.params })
        assertTrue(rina.inbox().none { it.template == "kinfolk_visit_note" })
        assertTrue(budi.inbox().none { it.template == "kinfolk_visit_note" })
    }

    @Test
    fun `an unanswered Question moves to the next visit with any Provider, and whoever asked is told`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val rao = sri.addProvider(circle, "Dr. Anand Rao").id
        val physio = sri.addProvider(circle, "Fisioterapi Sehat").id
        val now = Clock.System.now()
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, rao, "Kontrol neurologi", null, now, attendeeId = sri.me())).id
        val later = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, rao, "Kontrol neurologi", null, now + 30.days)).id
        val next = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, physio, "Fisioterapi", null, now + 2.days, attendeeId = sri.me())).id
        budi.askQuestion(circle, appt, "Kapan Bapak boleh menyetir lagi?")
        sri.askQuestion(circle, appt, "Perlu tongkat baru?")
        val (driving, cane) = sri.questions(appt)

        assertFails { budi.moveQuestion(driving.id, appt) } // the Attendee's
        val moved = sri.moveQuestion(driving.id, appt)
        assertEquals(Moved("Fisioterapi", sri.appointment(next)!!.startsAt, "Budi"), moved)
        assertEquals(null, sri.moveQuestion(cane.id, appt).told) // asked it herself
        assertEquals(listOf(driving.id, cane.id), sri.questions(next).map { it.id })
        assertTrue(sri.questions(appt).isEmpty())
        deliver()
        val day = dayMonth(sri.appointment(next)!!.startsAt.toLocalDateTime(TimeZone.of("Asia/Jakarta")).date)
        assertEquals(listOf(listOf("Sri", "Kapan Bapak boleh menyetir lagi?", "fisioterapi $day")),
            budi.inbox().filter { it.template == "kinfolk_question_moved" }.map { it.params })

        // Unanswered there too, it carries over from the physio visit, not back to Dr. Rao.
        sri.saveVisitNote(next, emptyMap(), emptyList(), "")
        assertTrue(sri.questions(later).isEmpty())
    }

    @Test
    fun `a failed job can be recorded again`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me())).id

        // The audio is uploaded but the job refused: it can still be uploaded again.
        sri.setHidden(tukiman, budi.me(), DataCategory.visit_notes, hidden = true)
        assertFails { budi.transcribe(circle, appt, audio) }
        sri.setHidden(tukiman, budi.me(), DataCategory.visit_notes, hidden = false)

        Providers.transcript = null
        budi.transcribe(circle, appt, audio)
        assertEquals(Recording.Status.failed, budi.awaitRecording(appt).status)
        assertFails { budi.shareRecording(appt, emptyMap(), emptyList()) }

        Providers.transcript = output
        budi.transcribe(circle, appt, audio)
        assertEquals(Recording.Status.ready, budi.awaitRecording(appt).status)
        assertFails { budi.transcribe(circle, appt, audio) } // a finished one stays
    }

    @Test
    fun `after sharing the Attendee deletes the audio, and the transcript and summary stay`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me())).id
        val path = "$circle/$appt"

        Providers.transcript = output
        budi.transcribe(circle, appt, audio)
        val t = budi.awaitRecording(appt).transcript!!
        assertFails { budi.deleteRecordingAudio(circle, appt) } // not before sharing
        budi.shareRecording(appt, emptyMap(), t.drafts(budi.me(), LocalDate(2026, 10, 12)))

        assertFails { sri.deleteRecordingAudio(circle, appt) } // only who recorded
        assertTrue(budi.storage.from("recordings").downloadAuthenticated(path).isNotEmpty())

        budi.deleteRecordingAudio(circle, appt)
        assertFails { budi.storage.from("recordings").downloadAuthenticated(path) }
        assertFails { budi.transcribe(circle, appt, audio) } // it doesn't come back
        assertFails { budi.storage.from("recordings").downloadAuthenticated(path) }
        val kept = sri.recording(appt)!!
        assertTrue(kept.audioDeletedAt != null)
        assertEquals(t, kept.transcript)
        assertEquals(listOf("Antar fisioterapi Selasa", "MRI ulang"), sri.visitNote(appt)!!.steps.map { it.text })
    }

    @Test
    fun `lines to check lock sharing until the Attendee confirms each`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = sri.me())).id

        Providers.transcript = output.replace(""""segments": [1]}]""", """"segments": [1], "check": "Cek: berlaku untuk cabut gigi?"}]""")
            .replace(""""owner": null, "due": null}""", """"owner": null, "due": null, "check": "Cek tanggal."}""")
        sri.transcribe(circle, appt, audio)
        assertEquals(listOf("q0", "n1"), sri.awaitRecording(appt).unchecked)
        assertFails { sri.shareRecording(appt, emptyMap(), emptyList()) }
        sri.checkLine(appt, "q0")
        assertFails { sri.checkLine(appt, "n0") } // nothing to check there
        assertEquals(listOf("n1"), sri.recording(appt)!!.unchecked)
        sri.checkLine(appt, "n1")
        assertTrue(sri.recording(appt)!!.unchecked.isEmpty())
        sri.shareRecording(appt, emptyMap(), emptyList())
    }

    @Test
    fun `applying the dose change updates the Medication, logs a Timeline entry and tells who sees Obat`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle, name = "Dewi")
        val rina = signedInSibling(sri, circle, name = "Rina")
        sri.setHidden(tukiman, rina.me(), DataCategory.medications, hidden = true)
        val clopidogrel = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me())).id

        Providers.transcript = output.replace(""""t": 0.0""", """"t": 130.4""")
        budi.transcribe(circle, appt, audio)
        budi.awaitRecording(appt)
        val pending = DoseChange(appt, clopidogrel.id, "Clopidogrel", "75 mg", "37,5 mg", 130.4, applied = false, saidBy = "Dr. Anand Rao")
        assertEquals(listOf(pending), budi.doseChanges(circle))
        assertTrue(sri.doseChanges(circle).isEmpty()) // not shared yet
        assertFails { sri.applyDoseChange(appt) }

        assertEquals(listOf("Sri", "Dewi"), budi.applyDoseChange(appt))
        assertEquals("37,5 mg", sri.medications(circle).single().dose)
        assertEquals(listOf(pending.copy(applied = true)), budi.doseChanges(circle))
        assertFails { budi.applyDoseChange(appt) } // once
        val entry = sri.timeline(circle).first()
        assertEquals(TimelineEntry.Kind.dose_change to "Clopidogrel dari 75 mg ke 37,5 mg (Dr. Anand Rao, menit 02:10 rekaman). Pengingat diperbarui.", entry.kind to entry.text)
        assertEquals(budi.me(), entry.by)
        assertTrue(rina.timeline(circle).none { it.kind == TimelineEntry.Kind.dose_change })
        assertEquals(listOf(AppliedDoseChange(clopidogrel.id, "75 mg", "Dr. Anand Rao", entry.at)), sri.appliedDoseChanges(circle))

        // "Urungkan": back to 75 mg, the entry and the unsent WhatsApp go.
        budi.undoDoseChange(appt)
        assertEquals("75 mg", sri.medications(circle).single().dose)
        assertTrue(sri.timeline(circle).none { it.kind == TimelineEntry.Kind.dose_change })
        budi.applyDoseChange(appt)
        deliver()
        val message = listOf(listOf("Budi", "Tukiman", "Clopidogrel", "75 mg", "37,5 mg", "Dr. Anand Rao"))
        assertEquals(message, dewi.inbox().filter { it.template == "kinfolk_dose_change" }.map { it.params })
        assertTrue(rina.inbox().none { it.template == "kinfolk_dose_change" })
        assertTrue(budi.inbox().none { it.template == "kinfolk_dose_change" })

        // Edited since: "Urungkan" doesn't overwrite it.
        sri.editMedication(clopidogrel.id, clopidogrel.draft().copy(dose = "50 mg"))
        assertFails { budi.undoDoseChange(appt) }
        assertEquals("50 mg", sri.medications(circle).single().dose)
    }

    @Test
    fun `the handoff goes to who wasn't in the room and may read the summary, once, the dose line only to who sees Obat`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        revenueCat(circle)
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle, name = "Dewi")
        val rina = signedInSibling(sri, circle, name = "Rina")
        val agus = signedInSibling(sri, circle, name = "Agus")
        val eka = signedInSibling(sri, circle, name = "Eka")
        sri.setHidden(tukiman, rina.me(), DataCategory.visit_notes, hidden = true)
        sri.setHidden(tukiman, eka.me(), DataCategory.medications, hidden = true)
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(
            AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, Clock.System.now(), attendeeId = budi.me(), driverId = agus.me())).id

        Providers.transcript = output
        budi.transcribe(circle, appt, audio)
        val t = budi.awaitRecording(appt).transcript!!
        assertFails { budi.sendHandoff(appt) } // not before sharing
        budi.shareRecording(appt, t.answersTo(budi.questions(appt)), t.drafts(budi.me(), LocalDate(2026, 10, 12)))

        // Not the Attendee, the Driver, or Rina, hidden from Rekaman kunjungan.
        assertEquals(listOf(sri.me(), dewi.me(), eka.me()), budi.handoffTo(appt))
        assertFails { sri.sendHandoff(appt) } // the Attendee's
        assertEquals(listOf("Sri", "Dewi", "Eka"), budi.sendHandoff(appt))
        assertEquals(listOf("Sri", "Dewi", "Eka"), budi.recording(appt)!!.handoffTold)
        assertFails { budi.sendHandoff(appt) } // once
        deliver()

        val said = listOf("Budi", "kontrol neurologi", "Jangan dihentikan")
        val steps = "Budi: Antar fisioterapi Selasa, 13 Okt; Budi: MRI ulang, 12 Okt"
        assertEquals(listOf(said + "Clopidogrel dari 75 mg ke 37,5 mg. Pengingat belum diperbarui" + steps),
            dewi.inbox().filter { it.template == "kinfolk_handoff" }.map { it.params })
        assertEquals(listOf(said + steps), eka.inbox().filter { it.template == "kinfolk_handoff_nomed" }.map { it.params })
        listOf(budi, agus, rina).forEach { m -> assertTrue(m.inbox().none { it.template.orEmpty().startsWith("kinfolk_handoff") }) }
    }
}
