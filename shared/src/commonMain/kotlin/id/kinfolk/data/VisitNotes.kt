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
import kotlinx.serialization.json.add
import kotlin.time.Instant

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

@Serializable
data class VisitNote(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("next_steps") val nextSteps: List<String>,
    val notes: String,
)

suspend fun SupabaseClient.askQuestion(circleId: String, appointmentId: String, text: String) {
    from("questions").insert(buildJsonObject { put("circle_id", circleId); put("appointment_id", appointmentId); put("text", text) })
}

/** The Questions on this Appointment, carried-over ones included, oldest first. */
suspend fun SupabaseClient.questions(appointmentId: String): List<Question> =
    from("appointment_questions").select {
        filter { eq("appointment_id", appointmentId) }
        order("created_at", Order.ASCENDING)
    }.decodeList()

suspend fun SupabaseClient.visitNote(appointmentId: String): VisitNote? =
    from("visit_notes").select { filter { eq("appointment_id", appointmentId) } }.decodeList<VisitNote>().firstOrNull()

/** Only the Attendee may; [answers] maps Question id to answer, blank for not answered. */
suspend fun SupabaseClient.saveVisitNote(appointmentId: String, answers: Map<String, String>, nextSteps: List<String>, notes: String) {
    postgrest.rpc("save_visit_note", buildJsonObject {
        put("appointment", appointmentId)
        putJsonObject("answers") { answers.forEach { (q, a) -> put(q, a) } }
        putJsonArray("next_steps") { nextSteps.forEach { add(it) } }
        put("notes", notes)
    })
}
