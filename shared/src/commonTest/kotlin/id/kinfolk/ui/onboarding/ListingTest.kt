package id.kinfolk.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class ListingTest {
    @Test
    fun `lists read like the prototype's invitee text`() {
        assertEquals("obat", listing(listOf("obat")))
        assertEquals("janji dokter dan obat", listing(listOf("janji dokter", "obat")))
        assertEquals("rekaman kunjungan, dokumen, dan uang", listing(listOf("rekaman kunjungan", "dokumen", "uang")))
    }
}
