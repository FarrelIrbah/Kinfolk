package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Note API (see CONTEXT.md): v3's `notes`, shared with the Care Circle or only for its author.

@Serializable
data class Note(val id: String, val by: String, val at: Instant, val text: String, val private: Boolean)

/** The shared Notes in [circleId] and the signed-in Member's private ones, newest first. */
suspend fun SupabaseClient.notes(circleId: String): List<Note> =
    from("notes").select { filter { eq("circle_id", circleId) }; order("at", Order.DESCENDING) }.decodeList()

/** "Simpan"; [private] is the "Hanya saya" switch. */
suspend fun SupabaseClient.addNote(circleId: String, text: String, private: Boolean) {
    from("notes").insert(buildJsonObject { put("circle_id", circleId); put("text", text.trim()); put("private", private) })
}
