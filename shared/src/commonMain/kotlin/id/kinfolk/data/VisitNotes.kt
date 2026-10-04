package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Question, Visit Note and carry-over API (see CONTEXT.md). Carry-over itself lives in the database
// (view appointment_questions).

@Serializable
data class Question(
    val id: String,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("asked_in") val askedIn: String,
    @SerialName("asked_for") val askedFor: Instant,
    @SerialName("asked_by") val askedBy: String,
    @SerialName("asked_by_name") val askedByName: String? = null,
    val text: String,
    /** Null before the Visit Note; blank when the visit left it unanswered. */
    val answer: String? = null,
) {
    /** Came over from an earlier Appointment with the same Provider. */
    val carried get() = askedIn != appointmentId
}

/** A Next Step as its Task: [owner] does it by [due]. */
@Serializable
data class NextStep(
    val id: String,
    val text: String,
    @SerialName("owner_id") val owner: String,
    val due: LocalDate,
    val position: Int,
)

/** What the Attendee writes for a Next Step; [id] of its Task, null for a new one. */
data class NextStepDraft(val text: String, val owner: String, val due: LocalDate, val id: String? = null)

@Serializable
data class VisitNote(
    @SerialName("appointment_id") val appointmentId: String,
    val notes: String,
    /** In order. */
    val steps: List<NextStep> = emptyList(),
)

@OptIn(ExperimentalUuidApi::class)
suspend fun SupabaseClient.askQuestion(circleId: String, appointmentId: String, text: String, id: String = Uuid.random().toString()) {
    from("questions").upsert(buildJsonObject { put("id", id); put("circle_id", circleId); put("appointment_id", appointmentId); put("text", text) }) {
        ignoreDuplicates = true // asked again under the same [id]: changes nothing
    }
}

/** The Questions on this Appointment, carried-over ones included, oldest first. */
suspend fun SupabaseClient.questions(appointmentId: String): List<Question> =
    from("appointment_questions").select {
        filter { eq("appointment_id", appointmentId) }
        order("created_at", Order.ASCENDING)
    }.decodeList()

suspend fun SupabaseClient.visitNote(appointmentId: String): VisitNote? =
    from("visit_notes").select(Columns.raw("appointment_id,notes,steps:tasks(id,text,owner_id,due,position)")) {
        filter { eq("appointment_id", appointmentId) }
    }.decodeList<VisitNote>().firstOrNull()?.let { it.copy(steps = it.steps.sortedBy(NextStep::position)) }

/**
 * Only the Attendee may; [answers] maps Question id to answer, blank for not answered. Each of [steps] is a Task;
 * a Next Step left out goes with its Task.
 */
suspend fun SupabaseClient.saveVisitNote(appointmentId: String, answers: Map<String, String>, steps: List<NextStepDraft>, notes: String) {
    postgrest.rpc("save_visit_note", buildJsonObject {
        put("appointment", appointmentId)
        putJsonObject("answers") { answers.forEach { (q, a) -> put(q, a) } }
        putJsonArray("steps") {
            steps.forEach { s -> addJsonObject { put("id", s.id); put("text", s.text); put("owner", s.owner); put("due", s.due.toString()) } }
        }
        put("notes", notes)
    })
}
