package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

// Invitation API used by the app and by the seam tests (see CONTEXT.md for the terms).

/** An Invitation as its Care Circle's admins see it. */
@Serializable
data class Invitation(
    val id: String,
    val name: String,
    val phone: String, // E.164
    val role: Role,
    @SerialName("accepted_at") val acceptedAt: Instant? = null,
) {
    val pending get() = acceptedAt == null
}

/** An Invitation to the signed-in person's own number, as `invitee` shows it. */
@Serializable
data class InvitationToMe(
    val id: String,
    val name: String,
    val inviter: String? = null,
    val circle: String,
    /** The Care Circle's first Duty and who holds it next week, for "Satu hal kecil, jika bisa" (#10). */
    @SerialName("duty_id") val dutyId: String? = null,
    val duty: String? = null,
    @SerialName("duty_holder") val dutyHolder: String? = null,
    /** What they will start without, for "Bapak membagikan kepada Anda" (#9). */
    val hidden: List<DataCategory> = emptyList(),
)

/**
 * Admins only: saves the Invitation and sends its link to [phone] (E.164) on WhatsApp, or SMS when that fails.
 * Role parent needs [recipientId], the Care Recipient they are.
 */
suspend fun SupabaseClient.invite(circleId: String, name: String, phone: String, role: Role = Role.sibling, recipientId: String? = null) {
    functions.invoke("invite", buildJsonObject {
        put("circle_id", circleId)
        put("name", name)
        put("phone", phone)
        put("role", role.name)
        put("recipient_id", recipientId)
    })
}

/** Every Invitation in [circleId] that wasn't cancelled, oldest first; empty unless the signed-in Member is an admin. */
suspend fun SupabaseClient.invitations(circleId: String): List<Invitation> =
    from("invitations").select(Columns.list("id", "name", "phone", "role", "accepted_at")) {
        filter { eq("circle_id", circleId); exact("cancelled_at", null) }
        order("created_at", Order.ASCENDING)
    }.decodeList()

/** Silently does nothing unless the signed-in Member is an admin and the Invitation is still pending. */
suspend fun SupabaseClient.cancelInvitation(id: String) {
    from("invitations").update(buildJsonObject { put("cancelled_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
}

suspend fun SupabaseClient.myInvitations(): List<InvitationToMe> =
    postgrest.rpc("my_invitations").decodeList()

/** Joins the Care Circle of one of [myInvitations]. Returns its id. */
suspend fun SupabaseClient.acceptInvitation(id: String): String =
    postgrest.rpc("accept_invitation", buildJsonObject { put("invitation", id) }).decodeAs()
