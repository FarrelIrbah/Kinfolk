package id.kinfolk.ui.contacts

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactPhoneTest {
    @Test
    fun `a typed 062x landline keeps its area code, a pasted +62 number loses its 62`() {
        // Typed one keystroke at a time, as the field sees it.
        assertEquals("622123456", "0622123456".fold("") { acc, c -> typedPhone(acc + c) })
        assertEquals("81234567890", typedPhone("0812-3456-7890"))
        assertEquals("81234567890", typedPhone("+62 812 3456 7890"))
    }
}
