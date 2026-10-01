package id.kinfolk.data

import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DocumentTest {
    init { Providers } // start the fake providers before anything is sent

    private val pdf = "%PDF-1.4 laporan".encodeToByteArray()

    @Test
    fun `re-uploading the same name keeps the old version and opens to the new one`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)

        sri.uploadDocument(circle, tukiman, " Laporan MRI otak ", "PDF", pdf, legal = false)
        dewi.uploadDocument(circle, tukiman, "laporan mri otak", "JPG", byteArrayOf(1, 2, 3), legal = false)
        sri.uploadDocument(circle, tukiman, "Kartu asuransi", "PNG", byteArrayOf(4), legal = false)
        sri.uploadDocument(circle, tukiman, "USG perut", "PDF", pdf, legal = true)

        val all = dewi.documents(circle).filterNot { it.legal }
        assertEquals(listOf("Kartu asuransi" to 1, "laporan mri otak" to 2, "Laporan MRI otak" to 1), all.map { it.name to it.version })
        assertEquals(listOf("Kartu asuransi", "laporan mri otak"), all.latest().map { it.name })
        assertContentEquals(pdf, dewi.documentFile(all.last()))
        assertContentEquals(byteArrayOf(1, 2, 3), sri.documentFile(all[1]))
        assertEquals(dewi.me(), all[1].by)
        assertEquals(
            // An acronym keeps its case.
            listOf("Mengunggah USG perut.", "Mengunggah kartu asuransi.", "Mengunggah laporan mri otak (versi 2).", "Mengunggah laporan MRI otak."),
            sri.timeline(circle).filter { it.kind == TimelineEntry.Kind.document }.map { it.text },
        )
    }

    @Test
    fun `documents follow Dokumen, legal ones Keinginan & hukum, files and timeline too`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val dewi = signedInSibling(sri, circle)
        sri.uploadDocument(circle, tukiman, "Ringkasan pulang RS", "PDF", pdf, legal = false)
        sri.uploadDocument(circle, tukiman, "Surat kuasa", "PDF", pdf, legal = true)
        val (power, summary) = sri.documents(circle)

        sri.setHidden(tukiman, dewi.me(), DataCategory.wishes, hidden = true)
        assertEquals(listOf(summary), dewi.documents(circle))
        assertFails { dewi.documentFile(power) }
        assertFails { dewi.uploadDocument(circle, tukiman, "Surat wasiat medis", "PDF", pdf, legal = true) }
        assertEquals(1, dewi.timeline(circle).count { it.kind == TimelineEntry.Kind.document })
        // A legal and a non-legal Document with the same name are separate: no hint of the hidden one.
        dewi.uploadDocument(circle, tukiman, "Surat kuasa", "PDF", pdf, legal = false)
        assertEquals(1, dewi.documents(circle).first().version)

        sri.setHidden(tukiman, dewi.me(), DataCategory.documents, hidden = true)
        assertTrue(dewi.documents(circle).isEmpty())
        assertFails { dewi.documentFile(summary) }

        sri.setHidden(tukiman, dewi.me(), DataCategory.wishes, hidden = false)
        assertEquals(listOf(power), dewi.documents(circle))
        assertContentEquals(pdf, dewi.documentFile(power))
    }

    @Test
    fun `strangers and viewers don't upload, strangers don't read, files can't be overwritten`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val tukiman = sri.careRecipients(circle).single().id
        val viewer = signedInSibling(sri, circle, Role.viewer)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }
        sri.uploadDocument(circle, tukiman, "Kartu asuransi", "JPG", pdf, legal = false)
        val card = sri.documents(circle).single()

        assertFails { rudi.uploadDocument(circle, tukiman, "Kartu asuransi", "JPG", pdf, legal = false) }
        assertFails { viewer.uploadDocument(circle, tukiman, "Kartu asuransi", "JPG", pdf, legal = false) }
        assertFails { sri.uploadDocument(circle, tukiman, "  ", "PDF", pdf, legal = false) }
        assertFails { sri.uploadDocument(circle, tukiman, "Foto", "GIF", pdf, legal = false) }
        assertTrue(rudi.documents(circle).isEmpty())
        assertFails { rudi.documentFile(card) }
        assertContentEquals(pdf, viewer.documentFile(card))
        assertFails { sri.storage.from("documents").upload(card.path, byteArrayOf(9)) { upsert = true } }
        assertContentEquals(pdf, sri.documentFile(card))
        assertEquals(1, sri.documents(circle).size)
    }
}
