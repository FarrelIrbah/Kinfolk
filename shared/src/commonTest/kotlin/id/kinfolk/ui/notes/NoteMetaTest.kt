package id.kinfolk.ui.notes

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NoteMetaTest {
    private val wib = TimeZone.of("Asia/Jakarta")
    private val now = Instant.parse("2026-10-01T08:00:00Z")

    @Test
    fun `v3's meta line`() {
        assertEquals("Sri · Baru saja · hanya Anda", noteMeta("Sri", now - 59.minutes, true, now, wib))
        assertEquals("Dewi · 24 Sept", noteMeta("Dewi", Instant.parse("2026-09-24T03:00:00Z"), false, now, wib))
        assertEquals("Budi · 1 Okt", noteMeta("Budi", now - 60.minutes, false, now, wib))
    }
}
