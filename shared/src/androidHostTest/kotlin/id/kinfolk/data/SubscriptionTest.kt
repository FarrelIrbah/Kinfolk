package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SubscriptionTest {
    init { Providers }

    private val audio = "ftyp-m4a kunjungan".encodeToByteArray()

    @Test
    fun `recording needs the circle's subscription with the add-on, written by RevenueCat's webhook`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)
        val provider = sri.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol", null, Clock.System.now(), attendeeId = sri.me())).id
        Providers.transcript = """{"segments":[],"qa":[],"next_steps":[]}"""

        assertNull(sri.subscription(circle))
        assertFails { sri.transcribe(circle, appt, audio) } // free

        assertEquals(401, revenueCat(circle, secret = "guess"))
        assertNull(sri.subscription(circle))

        val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()) // as the database keeps it
        assertEquals(200, revenueCat(circle, transcription = null, plan = now + 30.days)) // Budi pays for the plan alone
        assertEquals(Subscription(transcription = false, trial = false, expiresAt = now + 30.days), budi.subscription(circle))
        assertFails { sri.transcribe(circle, appt, audio) }

        assertEquals(200, revenueCat(circle, transcription = now + 14.days, plan = now + 30.days)) // Sri adds it, in its trial
        assertEquals(Subscription(transcription = true, trial = true, expiresAt = now + 14.days), budi.subscription(circle))
        sri.transcribe(circle, appt, audio)

        assertEquals(200, revenueCat(circle, transcription = now - 1.minutes, plan = null)) // lapsed
        assertNull(sri.subscription(circle))
        assertEquals(200, revenueCat("00000000-0000-0000-0000-000000000000")) // no such circle: nothing to retry
    }
}
