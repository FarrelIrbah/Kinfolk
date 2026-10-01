package id.kinfolk.data

import id.kinfolk.ui.appointment.shortDate
import id.kinfolk.ui.rota.weekRange
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WhatsAppTest {
    init { Providers } // start the fake providers before anything is sent

    private val wib = TimeZone.of("Asia/Jakarta")
    private val now = Clock.System.now()
    private val week = weekOf(Clock.System.todayIn(wib))
    private val sevenPm = LocalTime(19, 0)

    /** Someone signed in by phone, so WhatsApp can reach them. */
    private class Person(val client: SupabaseClient, val phone: String) {
        val digits get() = phone.removePrefix("+")
        fun inbox() = Providers.to(phone)
    }

    private suspend fun sriWithCircle(): Pair<Person, String> {
        val phone = newNumber()
        val sri = Person(signedInAs(phone), phone)
        return sri to sri.client.createCareCircle("Tukiman", Relation.Father, emptySet(), myName = "Sri")
    }

    private suspend fun joins(admin: Person, circle: String, name: String): Person {
        val phone = newNumber()
        Providers.to(phone)
        admin.client.invite(circle, name, phone)
        return Person(signedInAs(phone).apply { acceptInvitation(myInvitations().single().id) }, phone)
    }

    private val http = HttpClient.newHttpClient()
    private fun post(body: String, vararg headers: String) = http.send(
        HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp")).POST(HttpRequest.BodyPublishers.ofString(body)).apply {
            if (headers.isNotEmpty()) headers(*headers)
        }.build(),
        HttpResponse.BodyHandlers.ofString(),
    ).statusCode()

    /** The minute-by-minute job: sends what is waiting, and the reminders due [at]. */
    private fun deliver(at: Instant = Clock.System.now()) =
        assertEquals(200, post("""{"at":"$at"}""", "authorization", "Bearer local"))

    /** Meta calls the webhook with [json] as the value of a change, signed with the app secret. */
    private fun webhook(json: String): Int {
        val body = """{"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":$json}]}]}"""
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("local".toByteArray(), "HmacSHA256")) }
        val signature = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
        return post(body, "x-hub-signature-256", "sha256=$signature")
    }

    /** [from] replies on WhatsApp, as Meta calls the webhook: tapping a quick-reply button of [to], or typing [text]. */
    private fun reply(from: Person, text: String, to: Providers.Message? = null): Int {
        val message = if (to != null) {
            val payload = Regex(""""payload":"(${if (text == "YA") "ya" else "tidak"}:[^"]+)"""").find(to.text)!!.groupValues[1]
            """{"from":"${from.digits}","id":"wamid.${Clock.System.now()}","type":"button","button":{"payload":"$payload","text":"$text"}}"""
        } else """{"from":"${from.digits}","id":"wamid.${Clock.System.now()}","type":"text","text":{"body":"$text"}}"""
        return webhook("""{"messages":[$message]}""")
    }

    private val Providers.Message.template get() = Regex(""""template":\{"name":"(\w+)"""").find(text)?.groupValues?.get(1)
    private val Providers.Message.params get() = Regex(""""type":"text","text":"((?:[^"\\]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.toList()
    /** A free-form reply inside the 24-hour window. */
    private val Providers.Message.reply get() = Regex(""""body":"((?:[^"\\]|\\.)*)"""").find(text)?.groupValues?.get(1)
    private fun Person.last(template: String) = inbox().last { it.template == template }
    private fun Person.replies() = inbox().mapNotNull { it.reply }

    private fun label(at: Instant) = at.toLocalDateTime(wib).let { "${shortDate(it.date)} · ${"%02d.%02d".format(it.hour, it.minute)}" }

    @Test
    fun `a Member asked to swap accepts with the YA button, and the asker is told`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val duty = sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.client.me(), budi.client.me()), week)
        sri.client.askSwap(duty, week, budi.client.me())
        deliver()

        val ask = budi.last("kinfolk_swap_ask")
        assertEquals(listOf("Sri", "telepon cek malam", "${weekRange(week)}, 19.00"), ask.params)
        assertTrue("quick_reply" in ask.text)

        assertEquals(200, reply(budi, "YA", to = ask))
        deliver()

        assertEquals(budi.client.me(), sri.client.dutyWeek(circle, week).single().holder)
        assertEquals("Terima kasih, Budi. Anda pegang telepon cek malam minggu ini. Sri sudah diberi tahu.", budi.replies().last())
        assertEquals(listOf("Budi", "telepon cek malam"), sri.last("kinfolk_swap_yes").params)
    }

    @Test
    fun `typing TIDAK declines the swap, and a second answer finds it no longer open`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val duty = sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.client.me(), budi.client.me()), week)
        sri.client.askSwap(duty, week, budi.client.me())
        deliver()

        reply(budi, "tidak")
        deliver()
        assertEquals(sri.client.me(), sri.client.dutyWeek(circle, week).single().holder)
        assertNull(sri.client.dutyWeek(circle, week).single().swapTo)
        assertEquals("Ditolak. Sri akan bertanya ke yang lain.", budi.replies().last())
        assertEquals(listOf("Budi", "telepon cek malam"), sri.last("kinfolk_swap_no").params)

        reply(budi, "YA")
        assertEquals("Permintaan ini sudah tidak berlaku.", budi.replies().last())
        assertEquals(sri.client.me(), sri.client.dutyWeek(circle, week).single().holder)
    }

    @Test
    fun `a swap answered in the app can't be answered again on WhatsApp`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val duty = sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.client.me(), budi.client.me()), week)
        val swap = sri.client.askSwap(duty, week, budi.client.me())
        deliver()
        budi.client.answerSwap(swap, accept = false)

        reply(budi, "YA", to = budi.last("kinfolk_swap_ask"))
        assertEquals("Permintaan ini sudah tidak berlaku.", budi.replies().last())
        assertEquals(sri.client.me(), sri.client.dutyWeek(circle, week).single().holder)
    }

    @Test
    fun `a typed answer goes to the newest ask still open`() = runBlocking<Unit> {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val duty = sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.client.me(), budi.client.me()), week)
        val tukiman = sri.client.careRecipients(circle).single().id
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.client.scheduleAppointment(
            AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now + 3.days, driverId = budi.client.me()),
        ).id
        val swap = sri.client.askSwap(duty, week, budi.client.me())
        deliver()
        budi.client.answerSwap(swap, accept = false) // the newer ask, answered in the app

        reply(budi, "ya")
        assertNotNull(sri.client.appointment(appt)!!.driverConfirmedAt)
    }

    @Test
    fun `a WhatsApp message Meta can't deliver goes by SMS`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val duty = sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(sri.client.me(), budi.client.me()), week)
        sri.client.askSwap(duty, week, budi.client.me())
        deliver()

        val ask = budi.last("kinfolk_swap_ask")
        webhook("""{"statuses":[{"id":"${ask.id}","status":"failed","recipient_id":"${budi.digits}","errors":[{"code":131026}]}]}""")
        webhook("""{"statuses":[{"id":"${ask.id}","status":"failed","recipient_id":"${budi.digits}","errors":[{"code":131026}]}]}""")
        assertEquals(
            listOf("Sri bertanya: bisa ambil telepon cek malam minggu ${weekRange(week)}, 19.00? Balas YA atau TIDAK."),
            budi.inbox().filter { it.channel == "sms" }.map { it.text },
        )
    }

    @Test
    fun `a Driver confirms on WhatsApp, or declines and is taken off the Appointment`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val tukiman = sri.client.careRecipients(circle).single().id
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val startsAt = now + 3.days
        val draft = AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, startsAt, startsAt - 45.minutes, driverId = budi.client.me())
        val appt = sri.client.scheduleAppointment(draft).id
        deliver()

        val ask = budi.last("kinfolk_drive_ask")
        val departs = (startsAt - 45.minutes).toLocalDateTime(wib).let { "%02d.%02d".format(it.hour, it.minute) }
        assertEquals(listOf("Sri", "Tukiman", "Kontrol neurologi", "${label(startsAt)}, berangkat $departs"), ask.params)
        assertNull(sri.client.appointment(appt)!!.driverConfirmedAt)

        reply(budi, "YA", to = ask)
        deliver()
        assertNotNull(sri.client.appointment(appt)!!.driverConfirmedAt)
        assertEquals("Terima kasih, Budi. Anda mengantar Tukiman ${label(startsAt)}. Sri sudah diberi tahu.", budi.replies().last())
        assertEquals(listOf("Budi", "Tukiman", label(startsAt)), sri.last("kinfolk_drive_yes").params)

        // Nobody fakes a confirmation from the app; assigning again asks again.
        sri.client.editAppointment(appt, draft.copy(driverId = null))
        sri.client.editAppointment(appt, draft)
        assertNull(sri.client.appointment(appt)!!.driverConfirmedAt)
        deliver()
        reply(budi, "TIDAK", to = budi.last("kinfolk_drive_ask"))
        deliver()
        assertNull(sri.client.appointment(appt)!!.driverId)
        assertEquals("Tidak apa-apa. Sri akan bertanya ke yang lain.", budi.replies().last())
        assertEquals(listOf("Budi", "Tukiman", label(startsAt)), sri.last("kinfolk_drive_no").params)
    }

    @Test
    fun `a Driver's YA is a Rota entry on the Timeline for everyone who sees the Appointment`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val dewi = joins(sri, circle, "Dewi")
        val tukiman = sri.client.careRecipients(circle).single().id
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val startsAt = now + 3.days
        val draft = AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, startsAt, startsAt - 45.minutes, driverId = budi.client.me())
        val appt = sri.client.scheduleAppointment(draft).id
        deliver()
        reply(budi, "YA", to = budi.last("kinfolk_drive_ask"))

        val departs = (startsAt - 45.minutes).toLocalDateTime(wib).let { "%02d.%02d".format(it.hour, it.minute) }
        val entry = sri.client.timeline(circle).first()
        assertEquals(TimelineEntry.Kind.drive_confirmed to "Budi", entry.kind to entry.byName)
        assertEquals("Konfirmasi via WhatsApp: mengantar ke kontrol neurologi jam $departs.", entry.text)
        assertEquals(appt to budi.client.me(), entry.appointmentId to entry.by)
        assertEquals(entry, budi.client.timeline(circle).first())

        // Hidden with the Appointment's Data Category, and gone with a cancelled Appointment.
        sri.client.setHidden(tukiman, dewi.client.me(), DataCategory.appointments, hidden = true)
        assertTrue(dewi.client.timeline(circle).all { it.kind == TimelineEntry.Kind.access_change })
        assertTrue(signedInNewcomer().timeline(circle).isEmpty())
        sri.client.cancelAppointment(appt)
        assertTrue(sri.client.timeline(circle).all { it.kind == TimelineEntry.Kind.access_change })
    }

    @Test
    fun `assigning yourself asks nothing`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val tukiman = sri.client.careRecipients(circle).single().id
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        sri.client.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now + 3.days, driverId = sri.client.me()))
        deliver()
        assertTrue(sri.inbox().none { it.template == "kinfolk_drive_ask" })
    }

    @Test
    fun `the Driver is reminded two hours before leaving, once`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val tukiman = sri.client.careRecipients(circle).single().id
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val startsAt = now + 5.hours
        val departs = startsAt - 45.minutes
        sri.client.scheduleAppointment(
            AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, startsAt, departs, driverId = budi.client.me(), bring = "KTP, kartu BPJS."),
        )

        deliver(departs - 2.hours - 1.minutes)
        assertTrue(budi.inbox().none { it.template == "kinfolk_drive_reminder" })
        deliver(departs - 2.hours + 1.minutes)
        deliver(departs - 1.hours)
        val hm = { at: Instant -> at.toLocalDateTime(wib).let { "%02d.%02d".format(it.hour, it.minute) } }
        assertEquals(
            listOf(listOf("Tukiman", "Kontrol neurologi", "${hm(startsAt)}, berangkat ${hm(departs)}. Bawa: KTP, kartu BPJS")),
            budi.inbox().filter { it.template == "kinfolk_drive_reminder" }.map { it.params },
        )
    }

    @Test
    fun `the Duty holder is reminded an hour before, each day of their week`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        Providers.notOnWhatsApp += budi.digits // reads the SMS fallback, as the template is filled in
        sri.client.saveDuty(circle, "Telepon cek malam", sevenPm, listOf(budi.client.me(), sri.client.me()), week)
        val today = Clock.System.todayIn(wib)
        fun at(h: Int, m: Int) = LocalDateTime(today, LocalTime(h, m)).toInstant(wib)

        deliver(at(17, 59))
        deliver(at(18, 0))
        deliver(at(18, 30))
        assertEquals(listOf("Hari ini: telepon cek malam jam 19.00."), budi.inbox().filter { it.channel == "sms" }.map { it.text })
        assertTrue(sri.inbox().none { it.template == "kinfolk_duty_reminder" })
    }

    @Test
    fun `saving a Visit Note tells the Members who may read it, once`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val dewi = joins(sri, circle, "Dewi")
        val tukiman = sri.client.careRecipients(circle).single().id
        sri.client.setHidden(tukiman, dewi.client.me(), DataCategory.visit_notes, hidden = true)
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val appt = sri.client.scheduleAppointment(
            AppointmentDraft(circle, tukiman, provider, "Kontrol neurologi", null, now - 1.hours, attendeeId = sri.client.me()),
        ).id

        sri.client.saveVisitNote(appt, emptyMap(), listOf("fisioterapi 2x/minggu", "MRI ulang 3 bulan lagi."), "")
        sri.client.saveVisitNote(appt, emptyMap(), listOf("fisioterapi 2x/minggu", "MRI ulang 3 bulan lagi."), "Tidur membaik")
        deliver()

        assertEquals(
            listOf(listOf("Sri", "Tukiman", "Kontrol neurologi: fisioterapi 2x/minggu, MRI ulang 3 bulan lagi")),
            budi.inbox().filter { it.template == "kinfolk_visit_note" }.map { it.params },
        )
        assertTrue(dewi.inbox().none { it.template == "kinfolk_visit_note" })
        assertTrue(sri.inbox().none { it.template == "kinfolk_visit_note" })
    }

    @Test
    fun `a Driver who can't see Appointments is neither asked nor reminded`() = runBlocking {
        val (sri, circle) = sriWithCircle()
        val budi = joins(sri, circle, "Budi")
        val tukiman = sri.client.careRecipients(circle).single().id
        sri.client.setHidden(tukiman, budi.client.me(), DataCategory.appointments, hidden = true)
        val provider = sri.client.addProvider(circle, "Dr. Anand Rao").id
        val startsAt = now + 90.minutes
        sri.client.scheduleAppointment(AppointmentDraft(circle, tukiman, provider, "Kontrol psikiatri", null, startsAt, driverId = budi.client.me()))
        deliver()
        assertTrue(budi.inbox().none { it.template?.startsWith("kinfolk_drive") == true })
    }

    @Test
    fun `the webhook ignores calls Meta didn't sign, and answers Meta's check`() = runBlocking {
        assertEquals(401, post("""{"entry":[]}""", "x-hub-signature-256", "sha256=00"))
        assertEquals(401, post("""{"at":"$now"}""", "authorization", "Bearer wrong"))
        val check = http.send(
            HttpRequest.newBuilder(URI("$URL/functions/v1/whatsapp?hub.mode=subscribe&hub.verify_token=local&hub.challenge=42")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200 to "42", check.statusCode() to check.body())
    }
}
