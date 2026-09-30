package id.kinfolk.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationTest {
    init { Providers } // start the fake providers before anything is sent

    private suspend fun sriWithCircle() = signedInNewcomer().let { it to it.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri") }

    /** The invitation web page, as a browser without the app opens it. */
    private fun web(link: String, accept: Boolean = false): String = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI(link)).apply { if (accept) POST(HttpRequest.BodyPublishers.noBody()) }.build(),
        HttpResponse.BodyHandlers.ofString(),
    ).body()

    @Test
    fun `an admin invites a sibling, who gets the link on WhatsApp and joins from the app`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        sri.invite(circle, "Budi", phone)

        val message = Providers.to(phone).single()
        assertEquals("whatsapp", message.channel)
        assertContains(message.text, "kinfolk_invite")
        listOf("Sri", "Tukiman").forEach { assertContains(message.text, "\"text\":\"$it\"") }

        val budi = signedInAs(phone)
        val invitation = budi.myInvitations().single()
        assertEquals(InvitationToMe(invitation.id, "Budi", "Sri", "Tukiman"), invitation)
        assertEquals(circle, budi.acceptInvitation(invitation.id))

        assertEquals(CareCircle(circle, "Tukiman", memberCount = 2), budi.myCareCircle())
        assertEquals(Role.sibling, budi.roleIn(circle))
        assertTrue(budi.myInvitations().isEmpty())
        assertEquals(listOf("Budi" to false), sri.invitations(circle).map { it.name to it.pending })
    }

    @Test
    fun `a pending Invitation is not a Member and grants no access`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.careRecipients(circle).single().id
        val phone = newNumber()
        sri.invite(circle, "Budi", phone)
        val budi = signedInAs(phone)

        assertEquals(1, sri.myCareCircle()?.memberCount)
        assertEquals(listOf("Budi" to true), sri.invitations(circle).map { it.name to it.pending })
        assertNull(budi.myCareCircle())
        assertNull(budi.roleIn(circle))
        assertTrue(budi.careRecipients(circle).isEmpty())
        assertTrue(budi.invitations(circle).isEmpty())
        assertFails { budi.addMedication(MedicationDraft(circle, tukiman, "Penyusup", "", "", LocalTime(7, 0))) }
        Unit
    }

    @Test
    fun `accepting makes the person a Member with the Role they were invited as`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val dewi = signedInSibling(sri, circle, Role.viewer)
        assertEquals(Role.viewer, dewi.roleIn(circle))
    }

    @Test
    fun `someone without the app accepts on the web page, and later signs in as a Member`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        sri.invite(circle, "Budi", phone)
        val link = Providers.to(phone).single().link

        val page = web(link)
        assertContains(page, "Selamat datang, Budi")
        assertContains(page, "Sri menambahkan Anda ke lingkaran perawatan Tukiman. Ini keadaan terkini.")
        assertContains(page, "Terima undangan")
        assertEquals(1, sri.myCareCircle()?.memberCount)

        assertContains(web(link, accept = true), "Anda sudah bergabung. Kabar berikutnya dikirim lewat WhatsApp.")
        assertContains(web(link, accept = true), "Anda sudah bergabung.") // a double tap
        assertEquals(2, sri.myCareCircle()?.memberCount)
        assertContains(web(link), "Undangan ini sudah tidak berlaku")

        val budi = signedInAs(phone)
        assertEquals("Tukiman", budi.myCareCircle()?.name)
        assertEquals(Role.sibling, budi.roleIn(circle))
    }

    @Test
    fun `a cancelled Invitation can't be accepted anywhere`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        sri.invite(circle, "Budi", phone)
        val link = Providers.to(phone).single().link
        val budi = signedInAs(phone)
        val id = budi.myInvitations().single().id

        sri.cancelInvitation(sri.invitations(circle).single().id)

        assertTrue(sri.invitations(circle).isEmpty())
        assertTrue(budi.myInvitations().isEmpty())
        assertFails { budi.acceptInvitation(id) }
        assertContains(web(link), "Undangan ini sudah tidak berlaku")
        assertContains(web(link, accept = true), "Undangan ini sudah tidak berlaku")
        assertNull(budi.myCareCircle())
        assertEquals(1, sri.myCareCircle()?.memberCount)
    }

    @Test
    fun `only an admin can invite, see or cancel Invitations, and nobody can read the link`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = signedInSibling(sri, circle)
        val stranger = signedInNewcomer()
        val phone = newNumber()

        assertFails { budi.invite(circle, "Agus", phone) }
        assertFails { stranger.invite(circle, "Agus", phone) }
        assertTrue(Providers.to(phone).isEmpty())

        sri.invite(circle, "Agus", phone)
        val agus = sri.invitations(circle).single { it.pending }
        assertTrue(budi.invitations(circle).isEmpty())
        budi.cancelInvitation(agus.id)
        stranger.cancelInvitation(agus.id)
        assertTrue(sri.invitations(circle).single { it.id == agus.id }.pending)

        assertFails { sri.from("invitations").select(Columns.list("link")) }
        Unit
    }

    @Test
    fun `inviting a pending number again sends the same link`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        sri.invite(circle, "Budi", phone)
        sri.invite(circle, "Budi", phone)

        assertEquals(1, sri.invitations(circle).size)
        assertEquals(1, Providers.to(phone).map { it.link }.distinct().size)
        assertEquals(2, Providers.to(phone).size)
    }

    @Test
    fun `when WhatsApp can't take the invitation, it goes by SMS`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val phone = newNumber()
        Providers.notOnWhatsApp += phone.removePrefix("+")
        sri.invite(circle, "Budi", phone)

        val message = Providers.to(phone).single()
        assertEquals("sms", message.channel)
        assertEquals(
            "Sri mengundang Anda ke lingkaran perawatan Tukiman di Kinfolk. Buka tautan ini untuk bergabung, tanpa perlu pasang app: ${message.link}",
            message.text,
        )
    }
}
