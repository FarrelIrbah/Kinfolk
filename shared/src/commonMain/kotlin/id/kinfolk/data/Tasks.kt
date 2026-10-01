package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Task API (see CONTEXT.md): v3's `tasks`. Next Steps become Tasks with their Visit Note (saveVisitNote), refills
// with the minute job (queue_reminders); Members add the rest.

@Serializable
data class Task(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    @SerialName("recipient_id") val recipientId: String,
    val text: String,
    @SerialName("owner_id") val ownerId: String,
    val due: LocalDate,
    val source: Source,
    /** The Appointment a Next Step came from ("Dari kontrol neurologi"). */
    @SerialName("from_title") val fromTitle: String? = null,
    @SerialName("added_by") val addedBy: String,
    val done: Boolean,
    @SerialName("done_by") val doneBy: String? = null,
    @SerialName("done_at") val doneAt: Instant? = null,
    /** "Diingatkan": the owner was sent a reminder, which goes once. */
    @SerialName("reminded_at") val remindedAt: Instant? = null,
) {
    @Suppress("EnumEntryName") enum class Source { step, refill, added }
}

/** Every Task in [circleId] the signed-in Member sees, soonest due first. */
suspend fun SupabaseClient.tasks(circleId: String): List<Task> =
    from("task_list").select {
        filter { eq("circle_id", circleId) }
        order("due", Order.ASCENDING)
        order("created_at", Order.ASCENDING)
    }.decodeList()

/** "+ Tambah tugas". */
suspend fun SupabaseClient.addTask(recipient: CareRecipient, text: String, owner: String, due: LocalDate): Task =
    from("tasks").insert(buildJsonObject {
        put("circle_id", recipient.circleId); put("recipient_id", recipient.id)
        put("text", text); put("owner_id", owner); put("due", due.toString())
    }) { select() }.decodeSingle()

/** The Task form; a Next Step's text changes on its Visit Note too. Silently nothing to a Task the Member can't change. */
suspend fun SupabaseClient.editTask(id: String, text: String, owner: String, due: LocalDate) {
    from("tasks").update(buildJsonObject { put("text", text); put("owner_id", owner); put("due", due.toString()) }) { filter { eq("id", id) } }
}

/** The checkbox, and its "Urungkan". */
suspend fun SupabaseClient.markTaskDone(id: String, done: Boolean) {
    from("tasks").update(buildJsonObject { put("done", done) }) { filter { eq("id", id) } }
}

/** "Ingatkan": WhatsApp to the owner, once; fails on your own Task or one already reminded. */
suspend fun SupabaseClient.remindTask(id: String) {
    postgrest.rpc("remind_task", buildJsonObject { put("task", id) })
}
