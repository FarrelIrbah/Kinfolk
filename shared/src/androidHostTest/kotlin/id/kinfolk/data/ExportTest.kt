package id.kinfolk.data

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ExportTest {
    init { Providers } // start the fake providers before anything is sent

    /** The link as a doctor opens it: no app, no login. */
    private fun open(link: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(link)).build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun pagesIn(pdf: ByteArray) = Regex("""/Type\s*/Page\b""").findAll(pdf.decodeToString()).count()

    /** A real PDF of [n] blank pages, like a scanned discharge summary. */
    private fun pdfOf(n: Int): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [${(0 until n).joinToString(" ") { "${it + 3} 0 R" }}] /Count $n >>",
        ) + List(n) { "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>" }
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, o -> out.length.also { out.append("${i + 1} 0 obj\n$o\nendobj\n") } }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append("${it.toString().padStart(10, '0')} 00000 n \n") }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().encodeToByteArray()
    }

    @Serializable
    private data class ExportRow(val at: Instant, @SerialName("expires_at") val expiresAt: Instant, val token: String)

    /** Ends the 7 days at once, as the database's clock would. */
    private fun expire(token: String) {
        val sql = "update public.exports set expires_at = now() - interval '1 second' where token = '$token'"
        val p = ProcessBuilder("docker", "exec", "supabase_db_Kinfolk", "psql", "-U", "postgres", "-c", sql).redirectErrorStream(true).start()
        check(p.waitFor() == 0) { p.inputStream.readAllBytes().decodeToString() }
    }

    @Test
    fun `the PDF holds the pages the app counted, and the export joins the Timeline`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single()
        sri.saveEmergencyInfo(tukiman.id, EmergencyDraft(null, null, "Penisilin", "", "Stroke iskemik, April 2026."))
        repeat(13) { sri.addMedication(MedicationDraft(circle, tukiman.id, "Obat $it", "5 mg", "pagi", LocalTime(7, 0))) }
        sri.saveCheckIn(tukiman, LocalDate(2026, 9, 30), CheckInDraft(150, 90, Ate.yes, true, Mood.good, ""))
        sri.uploadDocument(circle, tukiman.id, "Ringkasan pulang RS", "PDF", pdfOf(3), legal = false, pages = 3)
        sri.uploadDocument(circle, tukiman.id, "Foto resep", "JPG", jpeg, legal = false)
        val docs = sri.documents(circle).latest()

        val content = ExportContent(
            sri.careRecipients(circle).single().conditions, "Penisilin", 0, sri.medications(circle).size, 1,
            sri.timeline(circle).count { it.kind == TimelineEntry.Kind.visit_note }, docs.map { it.pages },
        )
        assertEquals(mapOf(ExportSection.history to 1, ExportSection.meds to 2, ExportSection.trends to 1, ExportSection.visits to 0, ExportSection.allergies to 1, ExportSection.docs to 4), content.pages())
        val ticked = ExportSection.entries.toSet()

        val link = sri.export(tukiman.id, " Dr. Lestari, kardiologi ", "Disiapkan untuk Dr. Lestari, kardiologi · 3 Okt 2026", ticked, docs.map { it.id })

        val pdf = open(link)
        assertEquals(200, pdf.statusCode())
        assertEquals("application/pdf", pdf.headers().firstValue("content-type").get())
        assertEquals(content.total(ticked), pagesIn(pdf.body()))
        assertEquals(
            "Mengekspor PDF 9 halaman untuk Dr. Lestari, kardiologi. Tautan berlaku 7 hari.",
            sri.timeline(circle).first { it.kind == TimelineEntry.Kind.export }.text,
        )
    }

    @Test
    fun `the link works for 7 days only`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        sri.saveEmergencyInfo(tukiman, EmergencyDraft(null, null, "Penisilin", "", ""))

        val link = sri.export(tukiman, "", "3 Okt 2026", setOf(ExportSection.allergies), emptyList())

        assertEquals(1, pagesIn(open(link).body()))
        val row = sri.from("exports").select().decodeSingle<ExportRow>()
        assertEquals(7.days, row.expiresAt - row.at)
        assertEquals("Mengekspor PDF 1 halaman. Tautan berlaku 7 hari.", sri.timeline(circle).first().text)
        expire(row.token)
        open(link).let {
            assertEquals(404, it.statusCode())
            assertContains(it.body().decodeToString(), "Tautan ini sudah tidak berlaku")
        }
        assertEquals(404, open(link.substringBefore("?") + "?t=not-a-token").statusCode())
    }

    @Test
    fun `an export holds only what its Member may see, and outsiders can't make one`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)
        sri.uploadDocument(circle, tukiman, "Laporan MRI", "PDF", pdfOf(2), legal = false, pages = 2)
        sri.uploadDocument(circle, tukiman, "Surat kuasa", "PDF", pdfOf(1), legal = true)
        sri.saveEmergencyInfo(tukiman, EmergencyDraft(null, null, "Penisilin", "", ""))
        sri.setHidden(tukiman, dewi.me(), DataCategory.documents, hidden = true)
        val ids = sri.documents(circle).map { it.id }

        // Dewi asks for both: the hidden one and the legal one stay out, Alergi still prints.
        assertEquals(1, pagesIn(open(dewi.export(tukiman, "", "3 Okt 2026", setOf(ExportSection.allergies, ExportSection.docs), ids)).body()))
        assertFails { signedInNewcomer().export(tukiman, "", "3 Okt 2026", setOf(ExportSection.allergies), emptyList()) }
    }

    @Test
    fun `Cetak kartu prints the cards page and the fridge sheet, for Members only, without joining the Timeline`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        sri.saveEmergencyInfo(tukiman, EmergencyDraft(LocalDate(1948, 3, 12), 64, "Penisilin", "Tindakan penuh", "Stroke iskemik, April 2026."))
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0), bloodThinner = true))
        val card = sri.emergencyCard(tukiman)

        val pdf = sri.emergencyCardPdf(tukiman, card.url)

        assertEquals("%PDF", pdf.decodeToString(0, 4))
        assertEquals(2, pagesIn(pdf))
        assertEquals(0, sri.timeline(circle).count { it.kind == TimelineEntry.Kind.export })
        assertFails { signedInNewcomer().emergencyCardPdf(tukiman, card.url) }
    }

    private companion object {
        /** A photographed Document. */
        val jpeg = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", it)
        }.toByteArray()
    }
}
