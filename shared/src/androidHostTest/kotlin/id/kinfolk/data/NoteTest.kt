package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class NoteTest {
    init { Providers } // start the fake providers before anything is sent

    @Test
    fun `shared Notes are read by every Member, newest first`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val budi = signedInSibling(sri, circle)

        budi.addNote(circle, " Bapak minta radio lamanya. ", private = false)
        sri.addNote(circle, "Fisioterapis bilang pegangan tangga di sisi kiri, bukan kanan.", private = false)

        val notes = budi.notes(circle)
        assertEquals(listOf("Fisioterapis bilang pegangan tangga di sisi kiri, bukan kanan.", "Bapak minta radio lamanya."), notes.map { it.text })
        assertEquals(listOf(sri.me(), budi.me()), notes.map { it.by })
        assertTrue(notes.none { it.private })
    }

    @Test
    fun `a private Note is read only by its author, admins included`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri") // Sri is the admin
        val budi = signedInSibling(sri, circle)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        budi.addNote(circle, "Khawatir Bapak menyembunyikan rasa lelahnya.", private = true)
        sri.addNote(circle, "Cek tagihan apotek.", private = true)

        assertEquals(listOf("Khawatir Bapak menyembunyikan rasa lelahnya."), budi.notes(circle).map { it.text })
        assertEquals(listOf("Cek tagihan apotek."), sri.notes(circle).map { it.text })
        assertTrue(rudi.notes(circle).isEmpty())
    }

    @Test
    fun `nobody writes a Note outside their Care Circle, viewers only read, and never a blank one`() = runBlocking<Unit> {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet(), myName = "Sri")
        val viewer = signedInSibling(sri, circle, Role.viewer)
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }
        sri.addNote(circle, "Kunci cadangan di tetangga.", private = false)

        assertFails { rudi.addNote(circle, "Halo", private = false) }
        assertFails { viewer.addNote(circle, "Halo", private = false) }
        assertFails { sri.addNote(circle, "   ", private = false) }
        assertEquals(listOf("Kunci cadangan di tetangga."), viewer.notes(circle).map { it.text })
    }
}
