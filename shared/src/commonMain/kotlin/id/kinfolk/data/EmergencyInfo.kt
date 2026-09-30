package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Emergency Info API used by the app and by the seam tests (see CONTEXT.md and ADR 0003).

@Serializable
private data class CardRow(val token: String, val version: Int, @SerialName("last_scanned_at") val lastScannedAt: Instant? = null)

/** The QR card of a Care Recipient: [url] opens Emergency Info without login until an admin revokes it. */
data class EmergencyCard(val url: String, val version: Int, val lastScannedAt: Instant?)

private fun SupabaseClient.card(row: CardRow) = EmergencyCard("$supabaseHttpUrl/functions/v1/emergency?t=${row.token}", row.version, row.lastScannedAt)

/** Made the first time any Member asks. */
suspend fun SupabaseClient.emergencyCard(recipientId: String): EmergencyCard =
    card(postgrest.rpc("emergency_card", buildJsonObject { put("recipient", recipientId) }).decodeAs<CardRow>())

/** Admins only: the old link stops working and a new one replaces it. */
suspend fun SupabaseClient.reissueEmergencyCard(recipientId: String): EmergencyCard =
    card(postgrest.rpc("reissue_emergency_card", buildJsonObject { put("recipient", recipientId) }).decodeAs<CardRow>())

/** Silently does nothing to a Care Recipient the signed-in Member can't see. */
suspend fun SupabaseClient.saveEmergencyInfo(recipientId: String, allergies: String, conditions: String) {
    from("care_recipients").update(buildJsonObject { put("allergies", allergies); put("conditions", conditions) }) {
        filter { eq("id", recipientId) }
    }
}
