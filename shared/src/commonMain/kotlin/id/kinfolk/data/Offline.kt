package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Offline cache (#14): kept on the phone after each load and shown until the next one succeeds.
// Offline write queue (#44): these few writes wait on the phone and are sent in order once reachable; every one is
// idempotent (upserts, or inserts under a client id), so a write sent again after a lost reply changes nothing.

/** What the signed-in Member can read without a connection: Home (with its Tasks row), `appt`, `summary`, Obat, Linimasa and Emergency Info. */
@Serializable
data class Snapshot(
    val circle: CareCircle,
    val recipient: CareRecipient?,
    /** The earliest Appointment since the start of today, with its Questions and Visit Note. */
    val next: Appointment?,
    val questions: List<Question>,
    val note: VisitNote?,
    val medications: List<Medication>,
    val members: List<Member>,
    val timeline: List<TimelineEntry>,
    val contacts: List<CareContact>,
    val card: EmergencyCard?,
    /** [today]'s Dose Logs: offline the ring still counts them. */
    val doses: List<DoseLog> = emptyList(),
    val today: LocalDate? = null,
    /** [today]'s Check-in, for the evening card. */
    val checkIn: CheckIn? = null,
    /** Home's Tasks row. */
    val tasks: List<Task> = emptyList(),
    /** Skips Data Category (ADR 0003). */
    val emergency: EmergencyInfo? = null,
    val savedAt: Instant = Clock.System.now(),
)

/** Everything a [Snapshot] holds, as the server lets the signed-in Member read it now; null without a Care Circle. */
suspend fun SupabaseClient.snapshot(since: Instant, today: LocalDate): Snapshot? {
    val circle = myCareCircle() ?: return null
    val recipient = careRecipients(circle.id).firstOrNull()
    val next = nextAppointment(circle.id, since)
    return Snapshot(
        circle, recipient, next, next?.let { questions(it.id) }.orEmpty(), next?.let { visitNote(it.id) },
        medications(circle.id), members(circle.id), timeline(circle.id),
        careContacts(circle.id), recipient?.let { emergencyCard(it.id) },
        doseLogs(circle.id, today), today, recipient?.let { checkIn(it.id, today) }, tasks(circle.id), recipient?.let { emergencyInfo(it.id) },
    )
}

/** A write that waits on the phone while offline. */
@Serializable
sealed interface Write {
    /** "Tandai" / "Diberikan ✓" and "Tandai semua diberikan"; [give] false takes the one dose back. */
    @Serializable @SerialName("doses")
    data class Doses(val meds: List<Medication>, val day: LocalDate, val give: Boolean) : Write

    @Serializable @SerialName("check_in")
    data class SaveCheckIn(val recipient: CareRecipient, val day: LocalDate, val draft: CheckInDraft) : Write

    @OptIn(ExperimentalUuidApi::class)
    @Serializable @SerialName("note")
    data class AddNote(val circleId: String, val text: String, val private: Boolean, val id: String = Uuid.random().toString()) : Write

    @Serializable @SerialName("task_done")
    data class TaskDone(val taskId: String, val done: Boolean) : Write

    @OptIn(ExperimentalUuidApi::class)
    @Serializable @SerialName("question")
    data class Ask(val circleId: String, val appointmentId: String, val text: String, val id: String = Uuid.random().toString()) : Write
}

/**
 * Sends [w]; throws when it should be tried again (unreachable, signed-in session expired, server down), so it stays
 * queued. A write the server refuses (a Former Member's, a deleted Task's) is dropped: sending it again would never
 * succeed and would hold up the rest.
 */
suspend fun SupabaseClient.send(w: Write) {
    try {
        when (w) {
            is Write.Doses -> if (w.give) giveDoses(w.meds, w.day) else takeBackDose(w.meds.single(), w.day)
            is Write.SaveCheckIn -> saveCheckIn(w.recipient, w.day, w.draft)
            is Write.AddNote -> addNote(w.circleId, w.text, w.private, w.id)
            is Write.TaskDone -> markTaskDone(w.taskId, w.done)
            is Write.Ask -> askQuestion(w.circleId, w.appointmentId, w.text, w.id)
        }
    } catch (e: RestException) {
        if (e.statusCode !in 400..499 || e.statusCode in setOf(401, 408, 429)) throw e
        // ponytail: dropped without a word; the next load shows what the server kept.
    }
}

/** [pending] as if already sent, by [me] at [at]: what the phone shows until it is. Showing it again changes nothing. */
fun Snapshot.with(pending: List<Write>, me: String, at: Instant): Snapshot = pending.fold(this) { k, w ->
    when (w) {
        is Write.Doses -> if (w.day != k.today) k else {
            val ids = w.meds.map { it.id }.toSet()
            k.copy(doses = k.doses.filterNot { it.medicationId in ids } + if (w.give) w.meds.map { DoseLog(it.id, w.day, me, at) } else emptyList())
        }
        is Write.SaveCheckIn -> if (w.day != k.today || w.recipient.id != k.recipient?.id) k
            else k.copy(checkIn = w.shown(me, at, alerted = k.checkIn?.alerted == true))
        is Write.TaskDone -> k.copy(tasks = k.tasks.map { if (it.id == w.taskId && it.done != w.done) it.copy(done = w.done, doneBy = me.takeIf { w.done }, doneAt = at.takeIf { w.done }) else it })
        is Write.Ask -> if (w.appointmentId != k.next?.id || k.questions.any { it.id == w.id }) k
            else k.copy(questions = k.questions + w.shown(k.next, me))
        is Write.AddNote -> k // Notes aren't kept; `notes` shows it in place
    }
}

/** The Check-in as it will read once sent; the server decides [CheckIn.alerted], so it keeps what it was. */
fun Write.SaveCheckIn.shown(me: String, at: Instant, alerted: Boolean) =
    with(draft) { CheckIn(day, me, at, sys, dia, ate, walked, mood, note.trim(), alerted) }

/** The Question as it will read once sent, asked for [asked]. */
fun Write.Ask.shown(asked: Appointment, me: String) = Question(id, appointmentId, appointmentId, asked.startsAt, me, text = text)
