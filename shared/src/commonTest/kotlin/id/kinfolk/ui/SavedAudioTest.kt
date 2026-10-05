package id.kinfolk.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedAudioTest {
    /** One ADTS frame of [len] bytes at 16 kHz (sampling index 8), one raw block: 1024 samples, 0.064 s. */
    private fun frame(len: Int, fill: Int = 0) = ByteArray(len) { fill.toByte() }.also {
        it[0] = 0xFF.toByte(); it[1] = 0xF1.toByte(); it[2] = (1 shl 6 or (8 shl 2)).toByte(); it[3] = (1 shl 6 or (len shr 11)).toByte()
        it[4] = (len shr 3 and 0xFF).toByte(); it[5] = (len and 7 shl 5 or 0x1F).toByte(); it[6] = 0xFC.toByte()
    }

    @Test
    fun `parts join whole, a crash's half-written frame is dropped, and the length is counted from the frames`() {
        val second = ByteArray(0) + List(16) { frame(20) }.reduce(ByteArray::plus) // 16 frames = 1.024 s
        val crashed = second + second + frame(30).copyOf(12) // the app died mid-frame
        val resumed = List(15) { frame(25, 1) }.reduce(ByteArray::plus)

        val saved = savedAudio(listOf(crashed, resumed), stopped = false)!!
        assertContentEquals(second + second + resumed, saved.audio)
        assertEquals(3, saved.seconds) // 47 frames = 3.008 s
        assertEquals(false, saved.stopped)
    }

    @Test
    fun `nothing whole is nothing saved`() {
        assertNull(savedAudio(emptyList(), stopped = true))
        assertNull(savedAudio(listOf(frame(30).copyOf(5)), stopped = false))
    }
}
