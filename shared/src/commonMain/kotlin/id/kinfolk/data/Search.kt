package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * A `search` hit: what [id] is. A Timeline hit is its entry's [entry] kind, [at], [text] and Appointment [id] (null for
 * most kinds); doses marked together share [at].
 */
@Serializable
data class Hit(
    val kind: Kind, val id: String?, val entry: TimelineEntry.Kind? = null, val at: Instant? = null, val text: String? = null,
    /** A transcript line: its index in the Recording of Appointment [id], and "00:41 · Dr. Anand Rao · Kontrol neurologi". */
    val segment: Int? = null, val label: String? = null,
) {
    @Suppress("EnumEntryName") enum class Kind { medication, transcript, document, timeline, contact, task }
}

/** Full-text, each word a prefix, over what the signed-in Member can read; v3's order of kinds, at most 14. */
suspend fun SupabaseClient.search(circleId: String, query: String): List<Hit> =
    postgrest.rpc("search", buildJsonObject { put("circle", circleId); put("query", query) }).decodeList()
