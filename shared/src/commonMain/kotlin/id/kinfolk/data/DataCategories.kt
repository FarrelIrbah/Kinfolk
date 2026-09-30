package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

// Data Category restrictions (see CONTEXT.md, ADR 0004) used by the app and by the seam tests.

/** v1's three, approved in #9: "Janji dokter" (with its Questions), "Catatan kunjungan", "Obat". */
enum class DataCategory { appointments, visit_notes, medications }

/** [category] of [recipientId]'s data is hidden from the Member [memberId]. */
@Serializable
data class Hidden(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("member_id") val memberId: String,
    val category: DataCategory,
)

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

/** Emergency Info's "Obat saat ini" for any Member: skips Data Category like the QR page (ADR 0003). */
suspend fun SupabaseClient.emergencyMedications(recipientId: String): List<Medication> =
    postgrest.rpc("emergency_medications", buildJsonObject { put("recipient", recipientId) }).decodeList()
