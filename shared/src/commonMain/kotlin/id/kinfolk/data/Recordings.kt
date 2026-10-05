package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

// Recording and Transcript API (see CONTEXT.md, ADR 0005): transcribed by our own worker, read by the Attendee
// alone until shared, then under Rekaman kunjungan.

@Serializable
data class Recording(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("recorded_by") val recordedBy: String,
    val status: Status,
    @SerialName("result") val transcript: Transcript? = null,
    @SerialName("shared_at") val sharedAt: Instant? = null,
    /** The audio's length. */
    val seconds: Int = 0,
    /** Who the share went to on WhatsApp, in join order. */
    val told: List<String> = emptyList(),
) {
    @Suppress("EnumEntryName")
    enum class Status { processing, ready, failed }
}

@Suppress("EnumEntryName")
enum class Speaker { provider, recipient, attendee }

@Serializable
data class Transcript(
    val segments: List<Segment>,
    val qa: List<Answer>,
    @SerialName("next_steps") val steps: List<SuggestedStep>,
    val medication: MedicationChange? = null,
) {
    /** [t]: seconds from the start. [flagged]: "perlu dicek", the yellow mark. */
    @Serializable
    data class Segment(val t: Double, val speaker: Speaker, val text: String, val flagged: Boolean = false)

    /** [segments]: indexes into [Transcript.segments] the answer comes from. */
    @Serializable
    data class Answer(val question: String, val answer: String, val segments: List<Int> = emptyList())

    /** [ownerId]: the Member named [owner], when exactly one has that name; [segments] as for [Answer]. */
    @Serializable
    data class SuggestedStep(
        val text: String,
        val owner: String? = null,
        @SerialName("owner_id") val ownerId: String? = null,
        val due: LocalDate? = null,
        val segments: List<Int> = emptyList(),
    )

    /** [medicationId]: the Care Recipient's Medication named [name], when there is one. */
    @Serializable
    data class MedicationChange(
        val name: String,
        val change: String,
        val segment: Int? = null,
        @SerialName("medication_id") val medicationId: String? = null,
    )
}

/** The Attendee's recording of [appointmentId], [seconds] long: uploads it and starts transcription (again, after a failure). */
suspend fun SupabaseClient.transcribe(circleId: String, appointmentId: String, audio: ByteArray, seconds: Int = 0) {
    storage.from("recordings").upload("$circleId/$appointmentId", audio) {
        upsert = true // replaces the audio of a failed job
        contentType = ContentType("audio", "mp4")
    }
    functions.invoke("transcribe", buildJsonObject { put("appointment_id", appointmentId); put("seconds", seconds) })
}

/** Null until there is one the signed-in Member may read. */
suspend fun SupabaseClient.recording(appointmentId: String): Recording? =
    from("recordings").select { filter { eq("appointment_id", appointmentId) } }.decodeSingleOrNull()

/**
 * "Bagikan ke lingkaran": the Attendee saves the checked summary as the Visit Note ([answers] and [steps] as
 * [saveVisitNote] takes them; its first save tells the circle on WhatsApp) and shows the Recording to the circle.
 * Returns who was told, in join order.
 */
suspend fun SupabaseClient.shareRecording(appointmentId: String, answers: Map<String, String>, steps: List<NextStepDraft>): List<String> =
    postgrest.rpc("share_recording", buildJsonObject {
        put("appointment", appointmentId)
        putJsonObject("answers") { answers.forEach { (q, a) -> put(q, a) } }
        putJsonArray("steps") {
            steps.forEach { s -> addJsonObject { put("id", s.id); put("text", s.text); put("owner", s.owner); put("due", s.due.toString()) } }
        }
    }).decodeAs()

/** Where "Pindah ke kunjungan berikut" put a Question: that Appointment, and who was told on WhatsApp (null: nobody). */
@Serializable
data class Moved(val title: String, @SerialName("starts_at") val startsAt: Instant, val told: String? = null)

/** The Attendee of [fromAppointmentId] moves its open Question to the Care Recipient's next Appointment. */
suspend fun SupabaseClient.moveQuestion(questionId: String, fromAppointmentId: String): Moved =
    postgrest.rpc("move_question", buildJsonObject { put("question", questionId); put("from_appointment", fromAppointmentId) }).decodeAs()

/**
 * The summary's answer to each of [questions] (blank: not answered at the visit), matched to [Transcript.qa] by text.
 * ponytail: exact text, ignoring case, spaces and "?"; the worker is given the Questions word for word.
 */
fun Transcript.answersTo(questions: List<Question>): Map<String, String> {
    fun key(s: String) = s.trim().trimEnd('?', ' ').lowercase()
    val byText = qa.associateBy { key(it.question) }
    return questions.associate { it.id to byText[key(it.text)]?.answer.orEmpty() }
}

/** The worker's Next Steps as the Attendee starts from: unnamed ones theirs ([attendee]), undated ones due [due]. */
fun Transcript.drafts(attendee: String, due: LocalDate) =
    steps.map { NextStepDraft(it.text, it.ownerId ?: attendee, it.due ?: due) }
