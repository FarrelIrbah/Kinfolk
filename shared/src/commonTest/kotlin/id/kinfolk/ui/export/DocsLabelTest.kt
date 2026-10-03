package id.kinfolk.ui.export

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocsLabelTest {
    @Test
    fun `the picked Documents read like v3's row`() {
        assertNull(docsLabel(emptyList()))
        assertEquals("Laporan MRI", docsLabel(listOf("Laporan MRI")))
        assertEquals("Ringkasan pulang RS & laporan MRI", docsLabel(listOf("Ringkasan pulang RS", "Laporan MRI")))
        assertEquals("Ringkasan pulang RS, MRI otak & kartu asuransi", docsLabel(listOf("Ringkasan pulang RS", "MRI otak", "Kartu asuransi")))
    }

    @Test
    fun `the date as on the preview`() = assertEquals("29 Sept 2026", exportDate(LocalDate(2026, 9, 29)))
}
