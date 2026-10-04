package id.kinfolk.data

import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.random.Random

// Test seam: the Supabase API of the local stack (`npx supabase start`), used as a specific Member.

const val URL = "http://127.0.0.1:54321"

private val jvmAuth: AuthConfig.() -> Unit = {
    sessionManager = MemorySessionManager()
    codeVerifierCache = MemoryCodeVerifierCache()
    alwaysAutoRefresh = false
    autoLoadFromStorage = false
    enableLifecycleCallbacks = false
}

/** Stands in for Meta and Twilio: the local Edge Functions post here (see supabase/config.toml). */
object Providers {
    /** [id]: Meta's id for a WhatsApp message. */
    data class Message(val channel: String, val phone: String, val text: String, val id: String = "") {
        val code get() = Regex("""\d{6}""").findAll(text).last().value // the phone number comes first
        val link get() = Regex("""http://[^"\s]+""").find(text)!!.value
    }
    val sent = mutableListOf<Message>()
    val notOnWhatsApp = mutableSetOf<String>()
    /** Bodies of the jobs sent to the transcription worker. */
    val jobs = mutableListOf<String>()
    /** What the worker answers, as JSON; null fails the job. */
    @Volatile var transcript: String? = null

    init {
        HttpServer.create(InetSocketAddress(54340), 0).apply {
            createContext("/whatsapp") { ex ->
                val body = ex.requestBody.readBytes().decodeToString()
                val phone = Regex(""""to":"(\d+)"""").find(body)!!.groupValues[1]
                val ok = phone !in notOnWhatsApp
                if (!ok) { ex.sendResponseHeaders(400, -1); ex.close(); return@createContext }
                val id = synchronized(sent) { "wamid.${sent.size + 1}".also { sent += Message("whatsapp", phone, body, it) } }
                val reply = """{"messages":[{"id":"$id"}]}""".toByteArray()
                ex.sendResponseHeaders(200, reply.size.toLong()); ex.responseBody.use { it.write(reply) }
            }
            createContext("/twilio") { ex ->
                val form = ex.requestBody.readBytes().decodeToString().split("&")
                    .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                synchronized(sent) { sent += Message("sms", form.getValue("To").removePrefix("+"), form.getValue("Body")) }
                ex.sendResponseHeaders(201, -1); ex.close()
            }
            // RunPod Serverless: queues the job, then calls its webhook with [transcript] as the worker's output.
            createContext("/runpod") { ex ->
                val body = ex.requestBody.readBytes().decodeToString()
                synchronized(jobs) { jobs += body }
                val reply = """{"id":"job-${jobs.size}","status":"IN_QUEUE"}""".toByteArray()
                ex.sendResponseHeaders(200, reply.size.toLong()); ex.responseBody.use { it.write(reply) }
                val webhook = Regex(""""webhook":"([^"]+)"""").find(body)!!.groupValues[1]
                val done = transcript?.let { """{"status":"COMPLETED","output":$it}""" } ?: """{"status":"FAILED","error":"CUDA out of memory"}"""
                Thread {
                    (java.net.URI(webhook).toURL().openConnection() as java.net.HttpURLConnection).run {
                        requestMethod = "POST"; doOutput = true; setRequestProperty("content-type", "application/json")
                        outputStream.use { it.write(done.toByteArray()) }
                        responseCode
                    }
                }.start()
            }
            start()
        }
    }

    fun to(phone: String) = synchronized(sent) { sent.filter { it.phone == phone.removePrefix("+") } }
}

fun newNumber() = "+628129" + Random.nextLong(10_000_000, 99_999_999)

// ponytail: test users sign up by email (confirmations are off locally), so tests need no secret key;
// the app itself signs in by phone.
/** Someone who has just signed in and belongs to no Care Circle yet. */
suspend fun signedInNewcomer(): SupabaseClient = kinfolkClient(URL, jvmAuth).apply {
    auth.signUpWith(Email) {
        email = "member-${Random.nextLong(1, Long.MAX_VALUE)}@kinfolk.test"
        password = "local-test-password"
    }
}

/** Nobody signed in. */
fun signedOut(): SupabaseClient = kinfolkClient(URL, jvmAuth)

/** Whoever owns [phone], signed in with the code WhatsApp brought them. */
suspend fun signedInAs(phone: String): SupabaseClient = signedOut().apply {
    Providers.to(phone) // start the fake providers before any code is sent
    sendSignInCode(phone, sms = false)
    verifySignInCode(phone, Providers.to(phone).last { "kinfolk_otp" in it.text }.code)
}

fun SupabaseClient.me(): String = auth.currentUserOrNull()!!.id

/** Someone [admin] invited to [circleId] who accepted in the app, signed in. */
suspend fun signedInSibling(admin: SupabaseClient, circleId: String, role: Role = Role.sibling, name: String = "Budi"): SupabaseClient {
    val phone = newNumber()
    Providers.to(phone) // start the fake providers before the invitation is sent
    admin.invite(circleId, name, phone, role)
    return signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }
}

/** Someone [admin] invited to [circleId] who accepted as a sibling. Returns their user id. */
suspend fun joinedMember(admin: SupabaseClient, circleId: String): String = signedInSibling(admin, circleId).me()

/** Next Steps with the signed-in Member as owner. */
fun SupabaseClient.steps(vararg texts: String) = texts.map { NextStepDraft(it, me(), kotlinx.datetime.LocalDate(2026, 10, 8)) }
