package id.kinfolk.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class HighContrastTest {
    /** The matrix applied to one RGB colour in 0..255, unclamped. */
    private fun apply(r: Float, g: Float, b: Float): List<Float> {
        val m = HighContrast.values
        return (0..2).map { i -> m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b + m[i * 5 + 3] * 255f + m[i * 5 + 4] }
    }

    private fun near(want: Float, got: Float) = assertTrue(abs(want - got) < .01f, "want $want, got $got")

    @Test
    fun `contrast(1·22) saturate(1·08) like CSS`() {
        // Mid grey stays; grey is untouched by saturation.
        apply(127.5f, 127.5f, 127.5f).forEach { near(127.5f, it) }
        // #22261F: contrast first, then saturate (W3C filter-effects matrices).
        val c = listOf(0x22, 0x26, 0x1F).map { 1.22f * it + 127.5f * (1 - 1.22f) }
        val s = 1.08f
        val want = listOf(
            (.213f + .787f * s) * c[0] + (.715f - .715f * s) * c[1] + (.072f - .072f * s) * c[2],
            (.213f - .213f * s) * c[0] + (.715f + .285f * s) * c[1] + (.072f - .072f * s) * c[2],
            (.213f - .213f * s) * c[0] + (.715f - .715f * s) * c[1] + (.072f + .928f * s) * c[2],
        )
        want.zip(apply(34f, 38f, 31f)).forEach { (w, g) -> near(w, g) }
        // Alpha passes through.
        assertTrue(HighContrast.values.slice(15..19) == listOf(0f, 0f, 0f, 1f, 0f))
    }
}
