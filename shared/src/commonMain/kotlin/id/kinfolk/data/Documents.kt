package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Document API (see CONTEXT.md): v3's Records › Dokumen, under Dokumen or, when legal, Keinginan & hukum.

/** One version of a Document; the file is at [path] in the `documents` bucket. */
@Serializable
data class Document(
    val id: String,
    val name: String,
    val ext: String, // "PDF", "JPG" or "PNG"
    val legal: Boolean,
    val version: Int,
    val path: String,
    val by: String,
    val at: Instant,
) {
    val category get() = if (legal) DataCategory.wishes else DataCategory.documents
}

/** Every version in [circleId] the signed-in Member may see, newest first. */
suspend fun SupabaseClient.documents(circleId: String): List<Document> =
    from("documents").select {
        filter { eq("circle_id", circleId) }
        order("at", Order.DESCENDING)
    }.decodeList()

/** The newest version of each Document, newest first. */
fun List<Document>.latest(): List<Document> = sortedByDescending { it.at }.distinctBy { it.name.lowercase() to it.legal }

/** "Simpan" after picking a file: the next version when [name] is taken (ignoring case), else version 1. */
@OptIn(ExperimentalUuidApi::class)
suspend fun SupabaseClient.uploadDocument(circleId: String, recipientId: String, name: String, ext: String, bytes: ByteArray, legal: Boolean) {
    val id = Uuid.random().toString()
    storage.from("documents").upload("$circleId/$id.${ext.lowercase()}", bytes) {
        upsert = false
        contentType = if (ext == "PDF") ContentType.Application.Pdf else if (ext == "PNG") ContentType.Image.PNG else ContentType.Image.JPEG
    }
    from("documents").insert(buildJsonObject {
        put("id", id); put("circle_id", circleId); put("recipient_id", recipientId)
        put("name", name.trim()); put("ext", ext); put("legal", legal)
    })
}

/** The file of [document], for the system viewer. */
suspend fun SupabaseClient.documentFile(document: Document): ByteArray =
    storage.from("documents").downloadAuthenticated(document.path)
