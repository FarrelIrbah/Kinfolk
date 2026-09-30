package id.kinfolk.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignInTest {
    init { Providers } // start the fake providers before any code is sent

    @Test
    fun `a Member gets their sign-in code on WhatsApp and signs in with it`() = runBlocking<Unit> {
        val phone = newNumber()
        val app = signedOut()
        app.sendSignInCode(phone, sms = false)

        val message = Providers.to(phone).single()
        assertEquals("whatsapp", message.channel)
        app.verifySignInCode(phone, message.code)
        assertNotNull(app.auth.currentSessionOrNull())
    }

    @Test
    fun `when WhatsApp can't take the message, the code goes by SMS`() = runBlocking<Unit> {
        val phone = newNumber()
        Providers.notOnWhatsApp += phone.removePrefix("+")
        val app = signedOut()
        app.sendSignInCode(phone, sms = false)

        val message = Providers.to(phone).single()
        assertEquals("sms", message.channel)
        assertEquals("Kode masuk Kinfolk Anda: ${message.code}. Jangan bagikan kode ini ke siapa pun.", message.text)
        app.verifySignInCode(phone, message.code)
        assertNotNull(app.auth.currentSessionOrNull())
    }

    @Test
    fun `a Member who asks for SMS gets the code by SMS`() = runBlocking<Unit> {
        val phone = newNumber()
        val app = signedOut()
        app.sendSignInCode(phone, sms = false)
        Thread.sleep(1100) // Auth allows one code per second per number locally
        app.sendSignInCode(phone, sms = true)

        assertEquals(listOf("whatsapp", "sms"), Providers.to(phone).map { it.channel })
        app.verifySignInCode(phone, Providers.to(phone).last().code)
        assertNotNull(app.auth.currentSessionOrNull())
    }
}
