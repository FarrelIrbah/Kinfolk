package id.kinfolk.data

import kotlinx.coroutines.delay
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
        "medication": {"name": "clopidogrel", "change": "75 mg → 37,5 mg", "segment": 0}
    }"""

    private suspend fun io.github.jan.supabase.SupabaseClient.awaitRecording(appointment: String): Recording {
        repeat(100) { recording(appointment)?.takeIf { it.status != Recording.Status.processing }?.let { return it }; delay(100) }
        error("still processing")
    }

    @Test
    fun `the Attendee alone reads the transcript until they share it, then it follows Rekaman kunjungan`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
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
        assertFails { sri.shareRecording(appt) }
        sri.setHidden(tukiman, dewi.me(), DataCategory.visit_notes, hidden = true)
        budi.shareRecording(appt)
        assertEquals(t, sri.recording(appt)!!.transcript)
        assertNull(dewi.recording(appt))
    }

    @Test
    fun `a failed job can be recorded again`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
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
        assertFails { budi.shareRecording(appt) }

        Providers.transcript = output
        budi.transcribe(circle, appt, audio)
        assertEquals(Recording.Status.ready, budi.awaitRecording(appt).status)
        assertFails { budi.transcribe(circle, appt, audio) } // a finished one stays
    }
}
