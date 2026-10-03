package id.kinfolk.data

import kotlinx.coroutines.runBlocking
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

class BapakModeTest {
    init { Providers } // start the fake providers before anything is sent

    private fun deliver() = assertEquals(
        200,
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp")).header("authorization", "Bearer local")
                .POST(HttpRequest.BodyPublishers.ofString("""{"at":"${Clock.System.now()}"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode(),
    )

    private fun texts(phone: String, template: String) = Providers.to(phone).filter { template in it.text }

    /** Sri (admin), Budi, Dewi and the Care Recipient Tukiman as a Member; Budi is an emergency contact. */
    private class Circle(
        val sri: io.github.jan.supabase.SupabaseClient, val sriPhone: String, val budi: io.github.jan.supabase.SupabaseClient,
        val budiPhone: String, val dewiPhone: String, val tukimanPhone: String, val circle: String, val tukiman: CareRecipient,
    )

    private suspend fun circle(): Circle {
        val sriPhone = newNumber()
        val sri = signedInAs(sriPhone)
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        val budiPhone = newNumber()
        sri.invite(circle, "Budi", budiPhone)
        val budi = signedInAs(budiPhone).apply { acceptInvitation(myInvitations().single().id) }
        val dewiPhone = newNumber()
        sri.invite(circle, "Dewi", dewiPhone)
        signedInAs(dewiPhone).apply { acceptInvitation(myInvitations().single().id) }
        val tukimanPhone = newNumber()
        sri.invite(circle, "Tukiman", tukimanPhone, Role.parent, recipientId = tukiman.id)
        signedInAs(tukimanPhone).apply { acceptInvitation(myInvitations().single().id) }
        sri.setEmergencyContact(circle, budi.me(), true, "10 menit")
        return Circle(sri, sriPhone, budi, budiPhone, dewiPhone, tukimanPhone, circle, sri.careRecipients(circle).single())
    }

    @Test
    fun `Saya baik on Budi's phone texts every child and goes on the Timeline as the Care Recipient`() = runBlocking<Unit> {
        val c = circle()

        assertEquals(3, c.budi.sayFine(c.tukiman.id)) // Sri, Budi, Dewi
        deliver()

        for (phone in listOf(c.sriPhone, c.budiPhone, c.dewiPhone)) {
            val text = texts(phone, "kinfolk_recipient_ok").single().text
            assertTrue(""""text":"Tukiman"""" in text)
        }
        assertTrue(texts(c.tukimanPhone, "kinfolk_recipient_ok").isEmpty())
        val e = c.sri.timeline(c.circle).single { it.kind == TimelineEntry.Kind.recipient_press }
        assertEquals("Menekan \"Saya baik\" di Mode Tukiman.", e.text)
        assertEquals("Tukiman", e.byName)
        assertEquals(c.budi.me(), e.by) // whose phone
    }

    @Test
    fun `Butuh bantuan alerts every child, naming the organizer and the emergency contacts`() = runBlocking<Unit> {
        val c = circle()

        assertEquals("Sri dan Budi", c.budi.askHelp(c.tukiman.id))
        deliver()

        for (phone in listOf(c.sriPhone, c.budiPhone, c.dewiPhone)) {
            val text = texts(phone, "kinfolk_recipient_help").single().text
            assertTrue(""""text":"Tukiman"""" in text && """"text":"Sri dan Budi"""" in text)
        }
        assertTrue(texts(c.tukimanPhone, "kinfolk_recipient_help").isEmpty())
        assertEquals(
            "Menekan \"Butuh bantuan\". Sri dan Budi ditelepon.",
            c.sri.timeline(c.circle).single { it.kind == TimelineEntry.Kind.recipient_press }.text,
        )
        assertEquals(c.sriPhone, c.budi.organizerPhone(c.tukiman.id)) // "Telepon Sri" and the dialer
    }

    @Test
    fun `viewers and outsiders press nothing and get no number`() = runBlocking<Unit> {
        val c = circle()
        val viewer = signedInSibling(c.sri, c.circle, Role.viewer)
        val rudi = signedInNewcomer()

        assertFails { viewer.sayFine(c.tukiman.id) }
        assertFails { rudi.askHelp(c.tukiman.id) }
        assertNull(rudi.organizerPhone(c.tukiman.id))
        assertTrue(c.sri.timeline(c.circle).none { it.kind == TimelineEntry.Kind.recipient_press })
    }
}
