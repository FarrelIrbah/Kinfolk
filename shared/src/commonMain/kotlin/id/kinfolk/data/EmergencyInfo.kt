package id.kinfolk.data

import id.kinfolk.ui.contacts.localPhone
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Emergency Info API used by the app and by the seam tests (see CONTEXT.md and ADR 0003).

@Serializable
private data class CardRow(val token: String, val version: Int, @SerialName("last_scanned_at") val lastScannedAt: Instant? = null)

/** The QR card of a Care Recipient: [url] opens Emergency Info without login until an admin revokes it. */
@Serializable
data class EmergencyCard(val url: String, val version: Int, val lastScannedAt: Instant?)

// Hosted *.supabase.co serves function HTML as text/plain, so production links go through a custom domain (#15).
private val SupabaseClient.emergencyPage
    get() = HOSTED_EMERGENCY_URL.takeIf { it.isNotEmpty() && supabaseHttpUrl.trimEnd('/') == HOSTED_SUPABASE_URL }
        ?: "$supabaseHttpUrl/functions/v1/emergency"

private fun SupabaseClient.card(row: CardRow) = EmergencyCard("$emergencyPage?t=${row.token}", row.version, row.lastScannedAt)

/** Made the first time any Member asks. */
suspend fun SupabaseClient.emergencyCard(recipientId: String): EmergencyCard =
    card(postgrest.rpc("emergency_card", buildJsonObject { put("recipient", recipientId) }).decodeAs<CardRow>())

/** Admins only: the old link stops working and a new one replaces it. */
suspend fun SupabaseClient.reissueEmergencyCard(recipientId: String): EmergencyCard =
    card(postgrest.rpc("reissue_emergency_card", buildJsonObject { put("recipient", recipientId) }).decodeAs<CardRow>())

/** What Form Info darurat edits. */
data class EmergencyDraft(val bornOn: LocalDate?, val weightKg: Int?, val allergies: String, val wishes: String, val conditions: String)

/** Silently does nothing to a Care Recipient the signed-in Member can't see. */
suspend fun SupabaseClient.saveEmergencyInfo(recipientId: String, draft: EmergencyDraft) {
    from("care_recipients").update(buildJsonObject {
        put("born_on", draft.bornOn?.toString())
        put("weight_kg", draft.weightKg)
        put("allergies", draft.allergies)
        put("wishes", draft.wishes)
        put("conditions", draft.conditions)
    }) { filter { eq("id", recipientId) } }
}

/** Admins only: [member] shows on Emergency Info, first, with [distance] ("10 menit"). Not the Care Recipient. */
suspend fun SupabaseClient.setEmergencyContact(circleId: String, member: String, emergency: Boolean, distance: String) {
    postgrest.rpc("set_emergency_contact", buildJsonObject {
        put("circle", circleId); put("member", member); put("emergency", emergency); put("distance", distance)
    })
}

/** A line of Emergency Info's contacts: a flagged Member ("Anak", their distance) or a flagged Care Contact. [phone] is E.164. */
@Serializable
data class EmergencyContact(val name: String, val relationship: String, val distance: String = "", val phone: String)

/**
 * Emergency Info as the QR page shows it (ADR 0003's fixed fields): [medications] are "Amlodipine 5 mg", the active
 * ones; [bloodThinners] the active blood thinners' names. Built by the same query as the page, so the two match.
 */
@Serializable
data class EmergencyInfo(
    val name: String,
    @SerialName("born_on") val bornOn: LocalDate? = null,
    @SerialName("weight_kg") val weightKg: Int? = null,
    val allergies: String = "",
    val wishes: String = "",
    val conditions: String = "",
    @SerialName("blood_thinners") val bloodThinners: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    val contacts: List<EmergencyContact> = emptyList(),
)

/** Any Member; null when they can't see [recipientId]. */
suspend fun SupabaseClient.emergencyInfo(recipientId: String): EmergencyInfo? =
    postgrest.rpc("emergency_info_of", buildJsonObject { put("recipient", recipientId) }).decodeAs<EmergencyInfo?>()

private val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sept", "Okt", "Nov", "Des")

/** "78 · lahir 12 Mar 1948 · 64 kg", leaving out what isn't filled in; blank when neither is. Same as the QR page. */
fun EmergencyInfo.ageLine(today: LocalDate): String = listOfNotNull(
    bornOn?.let { "${it.yearsUntil(today)} · lahir ${it.day} ${months[it.month.ordinal]} ${it.year}" },
    weightKg?.let { "$it kg" },
).joinToString(" · ")

/** "Minum pengencer darah: Clopidogrel, Warfarin"; blank without one. Same as the QR page. */
fun EmergencyInfo.bloodLine(): String = if (bloodThinners.isEmpty()) "" else "Minum pengencer darah: ${bloodThinners.joinToString(", ")}"

/** "Anak · 10 menit · 0812 3456 7890" */
fun EmergencyContact.sub(): String = listOf(relationship, distance, localPhone(phone)).filter { it.isNotBlank() }.joinToString(" · ")


/** Until [emergencyInfo] has loaded (or from an offline copy saved before it existed): the Care Recipient's own fields. */
fun CareRecipient.emergencyFallback() = EmergencyInfo(name, bornOn, weightKg, allergies, wishes, conditions)
