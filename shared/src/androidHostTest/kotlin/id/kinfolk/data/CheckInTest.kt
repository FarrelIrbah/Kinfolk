package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CheckInTest {
    init { Providers } // start the fake providers before anything is sent

    private val today = LocalDate(2026, 10, 1)

    /** The minute-by-minute WhatsApp job: sends what is waiting. */
    private fun deliver() = assertEquals(
        200,
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp")).header("authorization", "Bearer local")
                .POST(HttpRequest.BodyPublishers.ofString("""{"at":"${Clock.System.now()}"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode(),
    )

    private fun alerts(phone: String) = Providers.to(phone).filter { "kinfolk_bp_high" in it.text }

    @Test
    fun `one Check-in per day, read by every Member, on the Timeline with v3's text`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budi = signedInSibling(sri, circle)
        sri.setHidden(tukiman.id, budi.me(), DataCategory.medications, hidden = true) // no Data Category of its own

        sri.saveCheckIn(tukiman, today, CheckInDraft(128, 80, Ate.yes, walked = true, Mood.good, "Agak lelah"))
        sri.saveCheckIn(tukiman, today, CheckInDraft(126, 78, Ate.some, walked = false, Mood.low, "")) // "Ubah"

        val c = budi.checkIn(tukiman.id, today)!!
        assertEquals(listOf(126, 78), listOf(c.sys, c.dia))
        assertEquals(sri.me(), c.by)
        assertNull(budi.checkIn(tukiman.id, LocalDate(2026, 9, 30)))
        assertEquals(
            listOf("Telepon malam: tensi 126/78, makan sedikit, tidak berjalan, murung."),
            budi.timeline(circle).filter { it.kind == TimelineEntry.Kind.check_in }.map { it.text },
        )
        sri.saveCheckIn(tukiman, today, CheckInDraft(128, 80, Ate.yes, walked = true, Mood.good, " Agak lelah "))
        assertEquals(
            "Telepon malam: tensi 128/80, sudah makan malam, berjalan, suasana hati baik. Agak lelah",
            budi.timeline(circle).single { it.kind == TimelineEntry.Kind.check_in }.text,
        )
    }

    @Test
    fun `nobody outside the Care Circle reads or logs a Check-in, and viewers only read`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val viewer = signedInSibling(sri, circle, Role.viewer)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }
        sri.saveCheckIn(tukiman, today, CheckInDraft(128, 80, Ate.yes, walked = true, Mood.good, ""))

        assertNull(rudi.checkIn(tukiman.id, today))
        assertEquals(128, viewer.checkIn(tukiman.id, today)?.sys)
        assertFails { rudi.saveCheckIn(tukiman, LocalDate(2026, 10, 2), CheckInDraft(150, 90, Ate.no, walked = false, Mood.low, "")) }
        assertFails { viewer.saveCheckIn(tukiman, LocalDate(2026, 10, 2), CheckInDraft(150, 90, Ate.no, walked = false, Mood.low, "")) }
        assertFails { sri.saveCheckIn(tukiman, LocalDate(2026, 10, 2), CheckInDraft(60, 30, Ate.no, walked = false, Mood.low, "")) }
    }

    @Test
    fun `140 or more texts every other Member but the Care Recipient on WhatsApp, once`() = runBlocking<Unit> {
        val sriPhone = newNumber()
        val sri = signedInAs(sriPhone)
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budiPhone = newNumber()
        sri.invite(circle, "Budi", budiPhone)
        signedInAs(budiPhone).apply { acceptInvitation(myInvitations().single().id) }
        val dewiPhone = newNumber()
        sri.invite(circle, "Dewi", dewiPhone)
        val dewi = signedInAs(dewiPhone).apply { acceptInvitation(myInvitations().single().id) }
        val tukimanPhone = newNumber()
        sri.invite(circle, "Tukiman", tukimanPhone, Role.parent, recipientId = tukiman.id)
        signedInAs(tukimanPhone).apply { acceptInvitation(myInvitations().single().id) }

        sri.saveCheckIn(tukiman, today, CheckInDraft(132, 84, Ate.yes, walked = true, Mood.good, ""))
        deliver()
        assertTrue(alerts(budiPhone).isEmpty())

        sri.saveCheckIn(tukiman, today, CheckInDraft(152, 90, Ate.yes, walked = true, Mood.good, "")) // "Ubah" crosses 140
        sri.saveCheckIn(tukiman, today, CheckInDraft(130, 90, Ate.yes, walked = true, Mood.good, "")) // a typo fixed
        sri.saveCheckIn(tukiman, today, CheckInDraft(150, 90, Ate.yes, walked = true, Mood.good, "")) // high again: no second text
        assertTrue(sri.checkIn(tukiman.id, today)!!.alerted)
        deliver()

        for (phone in listOf(budiPhone, dewiPhone)) {
            assertEquals(1, alerts(phone).size)
            assertTrue(""""text":"152/90"""" in alerts(phone).single().text)
            assertTrue(""""text":"Sri"""" in alerts(phone).single().text)
        }
        assertTrue(alerts(sriPhone).isEmpty())
        assertTrue(alerts(tukimanPhone).isEmpty()) // not the Care Recipient (#24)

        dewi.saveCheckIn(tukiman, LocalDate(2026, 10, 2), CheckInDraft(145, 88, Ate.no, walked = false, Mood.okay, "")) // a new day
        deliver()
        assertEquals(2, alerts(budiPhone).size)
        assertEquals(1, alerts(sriPhone).size)
    }

    @Test
    fun `the Care Recipient's number while they are a Member, else none`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        assertNull(sri.recipientPhone(tukiman.id))

        val phone = newNumber()
        Providers.to(phone)
        sri.invite(circle, "Tukiman", phone, Role.parent, recipientId = tukiman.id)
        signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }
        val budi = signedInSibling(sri, circle)

        assertEquals(phone, budi.recipientPhone(tukiman.id))
        assertNull(signedInNewcomer().recipientPhone(tukiman.id))
    }
}
