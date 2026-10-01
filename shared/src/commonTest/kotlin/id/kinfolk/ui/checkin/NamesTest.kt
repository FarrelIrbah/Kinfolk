package id.kinfolk.ui.checkin

import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {
    @Test
    fun `names join as v3's "Budi dan Dewi", with commas before the last from three`() {
        assertEquals("Budi", names(listOf("Budi")))
        assertEquals("Budi dan Dewi", names(listOf("Budi", "Dewi")))
        assertEquals("Budi, Dewi, dan Agus", names(listOf("Budi", "Dewi", "Agus")))
    }
}
