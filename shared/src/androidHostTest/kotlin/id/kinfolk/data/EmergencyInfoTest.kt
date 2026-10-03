package id.kinfolk.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import io.github.jan.supabase.auth.auth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import id.kinfolk.ui.contacts.localPhone
import kotlin.time.Clock
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
import kotlin.test.assertTrue

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
    fun `a paramedic scanning the card sees what the app shows, and only ADR 0003's fields`() = runBlocking {
        val (sri, circle, tukiman) = sriWithTukiman()
        val budi = signedInSibling(sri, circle)
        val dewi = signedInSibling(sri, circle)
        budi.saveEmergencyInfo(tukiman, EmergencyDraft(LocalDate(1948, 3, 12), 64, "Penisilin", "Tindakan penuh", "Stroke iskemik, April 2026. Tekanan darah tinggi."))
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi, sesudah makan", LocalTime(7, 0), bloodThinner = true))
        sri.addMedication(MedicationDraft(circle, tukiman, "Amlodipine", "5 mg", "pagi", LocalTime(7, 0)))
        val stopped = sri.addMedication(MedicationDraft(circle, tukiman, "Simvastatin", "10 mg", "malam", LocalTime(21, 0), bloodThinner = true))
        sri.editMedication(stopped.id, stopped.draft().copy(active = false))
        sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))
        sri.addCareContact(CareContactDraft(circle, "Bu Siti", "Tetangga", "+6281200001111", ContactGroup.Home))
        sri.setEmergencyContact(circle, dewi.me(), true, "")
        sri.setEmergencyContact(circle, budi.me(), true, " 10 menit ")
        sri.setEmergencyContact(circle, dewi.me(), false, "")
        assertFails { budi.setEmergencyContact(circle, budi.me(), false, "") } // admins only

        val budiPhone = "+" + budi.auth.currentUserOrNull()!!.phone
        val info = dewi.emergencyInfo(tukiman)
        assertEquals(
            EmergencyInfo(
                "Tukiman", LocalDate(1948, 3, 12), 64, "Penisilin", "Tindakan penuh", "Stroke iskemik, April 2026. Tekanan darah tinggi.",
                listOf("Clopidogrel"), listOf("Amlodipine 5 mg", "Clopidogrel 75 mg"),
                listOf(EmergencyContact("Budi", "Anak", "10 menit", budiPhone), EmergencyContact("Dr. Anand Rao", "Dokter saraf", "", "+6281234567890")),
            ),
            info,
        )
        assertEquals(true to "10 menit", sri.members(circle).single { it.userId == budi.me() }.let { it.emergency to it.distance })
        val today = Clock.System.todayIn(TimeZone.of("Asia/Jakarta"))
        assertEquals("78 · lahir 12 Mar 1948 · 64 kg", info!!.ageLine(LocalDate(2026, 10, 3)))
        assertEquals("77 · lahir 12 Mar 1948 · 64 kg", info.ageLine(LocalDate(2026, 3, 11)))
        assertEquals("Minum pengencer darah: Clopidogrel", info.bloodLine())
        assertEquals("Anak · 10 menit · ${localPhone(budiPhone)}", info.contacts.first().sub())

        val card = sri.emergencyCard(tukiman)
        assertContains(scan(card.url, userAgent = "WhatsApp/2.24.1 A").body(), "Penisilin") // the link preview after sharing
        assertNull(sri.emergencyCard(tukiman).lastScannedAt)

        // Every field the app shows, in the app's words, on the page; Budi before the doctor.
        val page = scan(card.url).body()
        (listOf("Info darurat", info.name, info.ageLine(today), info.bloodLine(), "Alergi", info.allergies, "Keinginan", info.wishes, "Kondisi", info.conditions,
            "Obat saat ini", info.medications.joinToString(" · "), "Telepon") +
            info.contacts.flatMap { listOf(it.name, it.sub(), "tel:${it.phone}") }).forEach { assertContains(page, it) }
        assertTrue(page.indexOf("Budi") < page.indexOf("Dr. Anand Rao"))
        listOf("Simvastatin", "Bu Siti", "pagi, sesudah makan", "Sri", "Dewi").forEach { assertFalse(it in page, "page shows $it") }
        assertNotNull(sri.emergencyCard(tukiman).lastScannedAt)
        Unit
    }

    @Test
    fun `empty Emergency Info shows the name only, and outsiders can't read it`() = runBlocking {
        val (sri, _, tukiman) = sriWithTukiman()
        val info = sri.emergencyInfo(tukiman)!!
        assertEquals(EmergencyInfo("Tukiman"), info)
        assertEquals("", info.ageLine(LocalDate(2026, 10, 3)))
        assertEquals("", info.bloodLine())
        val page = scan(sri.emergencyCard(tukiman).url).body()
        listOf("Alergi", "Keinginan", "Kondisi", "Obat saat ini", "Telepon", "pengencer", "lahir").forEach { assertFalse(it in page, "page shows $it") }
        assertNull(signedInNewcomer().emergencyInfo(tukiman))
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
        stranger.saveEmergencyInfo(tukiman, EmergencyDraft(null, null, "Semua", "", ""))
        assertEquals(card, sri.emergencyCard(tukiman))
        assertEquals("", sri.careRecipients(circle).single().allergies)

        val token = card.url.substringAfter("t=")
        listOf(signedOut(), stranger, budi).forEach { who ->
            assertFails { who.postgrest.rpc("emergency_info", buildJsonObject { put("token", token) }) }
        }
        Unit
    }
}
