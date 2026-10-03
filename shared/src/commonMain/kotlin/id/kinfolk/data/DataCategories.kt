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
import kotlinx.serialization.json.add
import kotlin.time.Instant

// Data Category restrictions (see CONTEXT.md, ADR 0004) used by the app and by the seam tests.

/**
 * v3's six (#20), in the `member` order: "Janji dokter" (with its Questions), "Rekaman kunjungan" (Recordings,
 * Transcripts, Visit Notes), "Obat", "Dokumen", "Keinginan & hukum", "Tagihan & uang".
 */
@Suppress("EnumEntryName") enum class DataCategory { appointments, visit_notes, medications, documents, wishes, money }

/** [category] of [recipientId]'s data is hidden from the Member [memberId]. */
@Serializable
data class Hidden(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("member_id") val memberId: String,
    val category: DataCategory,
)

/**
 * [by] hid or shared [category] of [recipientId]'s data with [memberId] at [at], for `member`'s "Riwayat perubahan".
 * [onBehalfOf]: the Care Recipient, when the restrictions were theirs (ADR 0004).
 */
@Serializable
data class AccessChange(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("member_id") val memberId: String,
    val category: DataCategory,
    val hidden: Boolean,
    val by: String,
    @SerialName("on_behalf_of") val onBehalfOf: String? = null,
    val at: Instant,
)

/** Every change in [circleId], newest first. Taking one back right away ("Urungkan") erases it instead. */
suspend fun SupabaseClient.accessChanges(circleId: String): List<AccessChange> =
    from("access_changes").select {
        filter { eq("circle_id", circleId) }
        order("at", Order.DESCENDING)
    }.decodeList()

/** Every restriction in [circleId], for `circle` and `member`. */
suspend fun SupabaseClient.hidden(circleId: String): List<Hidden> =
    from("hidden_categories").select { filter { eq("circle_id", circleId) } }.decodeList()

/**
 * Hides or shares [category] of [recipientId]'s data with [memberId]. Only the Care Recipient changes this when they
 * are a Member; otherwise only admins, who themselves always see everything.
 */
suspend fun SupabaseClient.setHidden(recipientId: String, memberId: String, category: DataCategory, hidden: Boolean) {
    postgrest.rpc("set_hidden", buildJsonObject {
        put("recipient", recipientId)
        put("member", memberId)
        put("category", category.name)
        put("hidden", hidden)
    })
}

/** Admins only, from onb3: what Members who join later start without. */
suspend fun SupabaseClient.hideByDefault(circleId: String, categories: Set<DataCategory>) {
    postgrest.rpc("hide_by_default", buildJsonObject {
        put("circle", circleId)
        putJsonArray("categories") { categories.forEach { add(it.name) } }
    })
}
