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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    /** [ownerId]: the Member named [owner], when exactly one has that name. */
    @Serializable
    data class SuggestedStep(
        val text: String,
        val owner: String? = null,
        @SerialName("owner_id") val ownerId: String? = null,
        val due: LocalDate? = null,
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

/** The Attendee's recording of [appointmentId]: uploads it and starts transcription (again, after a failure). */
suspend fun SupabaseClient.transcribe(circleId: String, appointmentId: String, audio: ByteArray) {
    storage.from("recordings").upload("$circleId/$appointmentId", audio) {
        upsert = true // replaces the audio of a failed job
        contentType = ContentType("audio", "mp4")
    }
    functions.invoke("transcribe", buildJsonObject { put("appointment_id", appointmentId) })
}

/** Null until there is one the signed-in Member may read. */
suspend fun SupabaseClient.recording(appointmentId: String): Recording? =
    from("recordings").select { filter { eq("appointment_id", appointmentId) } }.decodeSingleOrNull()

/** "Bagikan": the Attendee shows the ready result to the circle. */
suspend fun SupabaseClient.shareRecording(appointmentId: String) {
    postgrest.rpc("share_recording", buildJsonObject { put("appointment", appointmentId) })
}
