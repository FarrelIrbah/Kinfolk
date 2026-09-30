package id.kinfolk.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneTest {
    @Test
    fun `typed, pasted and local numbers all become the same number`() {
        listOf("812 3456 7890", "0812-3456-7890", "+62 812 3456 7890", "6281234567890").forEach {
            assertEquals("81234567890", localDigits(it), it)
        }
        assertEquals("+6281234567890", e164("81234567890"))
    }

    @Test
    fun `numbers read the way the design shows them`() {
        assertEquals("+62 812-3456-7890", prettyPhone("81234567890"))
        assertEquals("+62 812-3456-78", prettyPhone("812345678"))
        assertEquals("812 3456 7890", groupDigits("81234567890"))
        assertEquals("812 3", groupDigits("8123"))
    }
}
