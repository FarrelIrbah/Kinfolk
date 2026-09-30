package id.kinfolk.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmergencyInfoTest {
    init { Providers } // start the fake providers before anything is sent

    /** The Emergency Info page, as a paramedic's phone opens it from the QR: no app, no login. */
    private fun scan(link: String, userAgent: String = "Mozilla/5.0 (Linux; Android 14) Chrome/130.0 Mobile"): HttpResponse<String> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(link)).header("User-Agent", userAgent).build(), HttpResponse.BodyHandlers.ofString())

    private suspend fun sriWithTukiman() = signedInNewcomer().let { sri ->
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        Triple(sri, circle, sri.careRecipients(circle).single().id)
    }

    @Test
    fun `a paramedic scanning the card sees allergies, active Medications, conditions and emergency contacts only`() = runBlocking {
        val (sri, circle, tukiman) = sriWithTukiman()
        val budi = signedInSibling(sri, circle)
        budi.saveEmergencyInfo(tukiman, allergies = "Penisilin", conditions = "Stroke iskemik, April 2026. Tekanan darah tinggi.")
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi, sesudah makan", LocalTime(7, 0)))
        sri.addMedication(MedicationDraft(circle, tukiman, "Amlodipine", "5 mg", "pagi", LocalTime(7, 0)))
        val stopped = sri.addMedication(MedicationDraft(circle, tukiman, "Simvastatin", "10 mg", "malam", LocalTime(21, 0)))
        sri.editMedication(stopped.id, stopped.draft().copy(active = false))
        sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))
        sri.addCareContact(CareContactDraft(circle, "Bu Siti", "Tetangga", "+6281200001111", ContactGroup.Home))

        assertEquals(CareRecipient(tukiman, circle, "Tukiman", "father", "Penisilin", "Stroke iskemik, April 2026. Tekanan darah tinggi."), sri.careRecipients(circle).single())
        val card = sri.emergencyCard(tukiman)
        assertContains(scan(card.url, userAgent = "WhatsApp/2.24.1 A").body(), "Penisilin") // the link preview after sharing
        assertNull(sri.emergencyCard(tukiman).lastScannedAt)

        val page = scan(card.url).body()
        listOf(
            "Info darurat", "Tukiman", "Alergi", "Penisilin", "Kondisi", "Stroke iskemik, April 2026. Tekanan darah tinggi.",
            "Obat saat ini", "Amlodipine 5 mg · Clopidogrel 75 mg", "Dr. Anand Rao", "Dokter saraf · 0812 3456 7890", "tel:+6281234567890", "Telepon",
        ).forEach { assertContains(page, it) }
        listOf("Simvastatin", "Bu Siti", "pagi, sesudah makan", "Sri").forEach { assertFalse(it in page, "page shows $it") }
        assertNotNull(sri.emergencyCard(tukiman).lastScannedAt)
        Unit
    }

    @Test
    fun `a revoked card link stops working at once, and the new one works`() = runBlocking {
        val (sri, _, tukiman) = sriWithTukiman()
        val old = sri.emergencyCard(tukiman)
        assertEquals(1, old.version)
        assertContains(scan(old.url).body(), "Tukiman")

        val new = sri.reissueEmergencyCard(tukiman)

        assertEquals(2, new.version)
        assertNotEquals(old.url, new.url)
        assertEquals(new, sri.emergencyCard(tukiman))
        scan(old.url).let {
            assertEquals(404, it.statusCode())
            assertContains(it.body(), "Tautan ini sudah tidak berlaku")
            assertFalse("Tukiman" in it.body())
        }
        assertContains(scan(new.url).body(), "Tukiman")
        assertContains(scan(new.url.substringBefore("?") + "?t=not-a-token").body(), "Tautan ini sudah tidak berlaku")
        Unit
    }

    @Test
    fun `every Member shows the same card, only an admin can revoke it, and outsiders get nothing`() = runBlocking {
        val (sri, circle, tukiman) = sriWithTukiman()
        val budi = signedInSibling(sri, circle)
        val stranger = signedInNewcomer()
        val card = sri.emergencyCard(tukiman)

        assertEquals(card, budi.emergencyCard(tukiman))
        assertFails { budi.reissueEmergencyCard(tukiman) }
        assertFails { stranger.emergencyCard(tukiman) }
        assertFails { stranger.reissueEmergencyCard(tukiman) }
        stranger.saveEmergencyInfo(tukiman, "Semua", "")
        assertEquals(card, sri.emergencyCard(tukiman))
        assertEquals("", sri.careRecipients(circle).single().allergies)

        val token = card.url.substringAfter("t=")
        listOf(signedOut(), stranger, budi).forEach { who ->
            assertFails { who.postgrest.rpc("emergency_info", buildJsonObject { put("token", token) }) }
        }
        Unit
    }
}
