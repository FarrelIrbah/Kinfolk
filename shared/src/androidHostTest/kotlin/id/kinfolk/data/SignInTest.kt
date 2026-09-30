package id.kinfolk.data

import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Stands in for Meta and Twilio: the local send-otp function posts here (see supabase/config.toml). */
private object Providers {
    data class Message(val channel: String, val phone: String, val text: String) {
        val code get() = Regex("""\d{6}""").findAll(text).last().value // the phone number comes first
    }
    val sent = mutableListOf<Message>()
    val notOnWhatsApp = mutableSetOf<String>()

    init {
        HttpServer.create(InetSocketAddress(54399), 0).apply {
            createContext("/whatsapp") { ex ->
                val body = ex.requestBody.readBytes().decodeToString()
                val phone = Regex(""""to":"(\d+)"""").find(body)!!.groupValues[1]
                val ok = phone !in notOnWhatsApp
                if (ok) synchronized(sent) { sent += Message("whatsapp", phone, body) }
                ex.sendResponseHeaders(if (ok) 200 else 400, -1); ex.close()
            }
            createContext("/twilio") { ex ->
                val form = ex.requestBody.readBytes().decodeToString().split("&")
                    .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                synchronized(sent) { sent += Message("sms", form.getValue("To").removePrefix("+"), form.getValue("Body")) }
                ex.sendResponseHeaders(201, -1); ex.close()
            }
            start()
        }
    }

    fun to(phone: String) = synchronized(sent) { sent.filter { it.phone == phone.removePrefix("+") } }
}

class SignInTest {
    init { Providers } // start the fake providers before any code is sent
    private fun newNumber() = "+628129" + Random.nextLong(10_000_000, 99_999_999)

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
