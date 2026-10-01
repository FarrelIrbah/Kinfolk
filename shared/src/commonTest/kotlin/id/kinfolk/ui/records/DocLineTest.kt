package id.kinfolk.ui.records

import androidx.compose.ui.graphics.Color
import id.kinfolk.data.DataCategory
import id.kinfolk.data.Role
import id.kinfolk.ui.circle.CircleMember
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocLineTest {
    private val day = LocalDate(2026, 9, 1)
    private fun member(role: Role, vararg hidden: DataCategory, recipient: Boolean = false) =
        CircleMember("", "", Color.Black, role, day, false, DataCategory.entries.toSet() - hidden.toSet(), recipient)

    @Test
    fun `the pill counts Members but the Care Recipient who see it, Semua when all do`() {
        val circle = listOf(
            member(Role.parent, DataCategory.documents, recipient = true),
            member(Role.admin), member(Role.sibling, DataCategory.wishes), member(Role.viewer, DataCategory.documents, DataCategory.wishes),
        )
        assertEquals(2, seenBy(circle, DataCategory.documents))
        assertEquals(1, seenBy(circle, DataCategory.wishes))
        assertNull(seenBy(circle.take(2), DataCategory.documents))
    }

    @Test
    fun `meta reads like 12 Juni · v2 · Rina, with the year when not this one`() {
        assertEquals("12 Jun · v2 · Rina", docMeta(LocalDate(2026, 6, 12), LocalDate(2026, 10, 1), 2, "Rina"))
        assertEquals("3 Mar 2025 · v1 · Sri", docMeta(LocalDate(2025, 3, 3), LocalDate(2026, 10, 1), 1, "Sri"))
    }
}
