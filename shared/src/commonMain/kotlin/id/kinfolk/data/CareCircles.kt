package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import io.github.jan.supabase.exceptions.RestException
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Care Circle API used by the app and by the seam tests (see CONTEXT.md for the terms).

/** "Beliau adalah" choices on onb1, relative to the Member who adds the Care Recipient. */
enum class Relation(val key: String) { Father("father"), Mother("mother"), Grandparent("grandparent"), Spouse("spouse") }

/** "Apa yang paling sering perlu dikoordinasi?" choices on onb1. */
enum class Need(val key: String) { Visits("visits"), Medicines("medicines"), CheckIns("check_ins"), Paperwork("paperwork") }

enum class Role { admin, sibling, parent, viewer }

@Serializable
data class CareCircle(val id: String, val name: String, val memberCount: Int)

@Serializable
data class CareRecipient(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    val name: String,
    val relation: String? = null,
    val allergies: String = "", // Emergency Info: "Penisilin"
    val conditions: String = "", // Emergency Info: "Stroke iskemik, April 2026. …"
    @SerialName("born_on") val bornOn: LocalDate? = null, // Emergency Info
    @SerialName("weight_kg") val weightKg: Int? = null, // Emergency Info: 64
    val wishes: String = "", // Emergency Info: "Tindakan penuh"
    /** The Member who is this Care Recipient (Role parent); they own its Data Category restrictions (ADR 0004). */
    @SerialName("member_id") val memberId: String? = null,
)

@Serializable
private data class CircleRow(val id: String, val name: String, val members: List<Count>) {
    @Serializable data class Count(val count: Int)
}

@Serializable
private data class MemberRow(@SerialName("user_id") val userId: String, val role: Role)

/** Creates a Care Circle for [recipientName]; the signed-in user becomes its admin, named [myName]. Returns the circle id. */
suspend fun SupabaseClient.createCareCircle(recipientName: String, relation: Relation?, needs: Set<Need>, myName: String? = null): String =
    postgrest.rpc("create_care_circle", buildJsonObject {
        put("my_name", myName)
        put("recipient_name", recipientName)
        put("relation", relation?.key)
        putJsonArray("needs") { needs.forEach { add(it.key) } }
    }).decodeAs<String>()

// ponytail: first Care Circle only; a picker comes when a Member can belong to several.
suspend fun SupabaseClient.myCareCircle(): CareCircle? =
    from("care_circles").select(Columns.raw("id,name,members(count)")) {
        filter { exact("members.left_at", null) }
        order("created_at", Order.ASCENDING)
        limit(1)
    }.decodeList<CircleRow>().firstOrNull()?.let { CareCircle(it.id, it.name, it.members.firstOrNull()?.count ?: 0) }

suspend fun SupabaseClient.roleIn(circleId: String): Role? {
    val me = auth.currentUserOrNull()?.id ?: return null
    return from("members").select { filter { eq("circle_id", circleId); eq("user_id", me) } }
        .decodeList<MemberRow>().firstOrNull()?.role
}

suspend fun SupabaseClient.careRecipients(circleId: String): List<CareRecipient> =
    from("care_recipients").select { filter { eq("circle_id", circleId) }; order("created_at", Order.ASCENDING) }
        .decodeList<CareRecipient>()

suspend fun SupabaseClient.addCareRecipient(circleId: String, name: String, relation: Relation?) {
    from("care_recipients").insert(buildJsonObject {
        put("circle_id", circleId)
        put("name", name)
        put("relation", relation?.key)
    })
}

/** Returns how many Care Recipients were renamed (0 when not allowed to see them). */
suspend fun SupabaseClient.renameCareRecipient(recipientId: String, name: String): Int =
    from("care_recipients").update(buildJsonObject { put("name", name) }) {
        select()
        filter { eq("id", recipientId) }
    }.decodeList<CareRecipient>().size

/** A Member, or a Former Member once [leftAt] is set: their name stays on what they wrote. */
@Serializable
data class Member(
    @SerialName("user_id") val userId: String,
    val role: Role,
    val name: String? = null,
    @SerialName("created_at") val joinedAt: Instant,
    @SerialName("left_at") val leftAt: Instant? = null,
    /** An emergency contact on Emergency Info, set by an admin, with "Jarak dari rumah". */
    val emergency: Boolean = false,
    val distance: String = "",
)

/** Everyone who has been a Member of [circleId], in join order; empty for anyone who isn't a Member now. */
suspend fun SupabaseClient.members(circleId: String): List<Member> =
    from("members").select { filter { eq("circle_id", circleId) }; order("created_at", Order.ASCENDING) }.decodeList()

/** Admins only. */
suspend fun SupabaseClient.promoteToAdmin(circleId: String, userId: String) {
    postgrest.rpc("promote_member", buildJsonObject { put("circle", circleId); put("member", userId) })
}

/** Admins only; they lose all access at once. Fails with [isLastAdmin] when that would leave no admin. */
suspend fun SupabaseClient.removeMember(circleId: String, userId: String) {
    postgrest.rpc("remove_member", buildJsonObject { put("circle", circleId); put("member", userId) })
}

/** Fails with [isLastAdmin] for the only admin. */
suspend fun SupabaseClient.leaveCareCircle(circleId: String) = removeMember(circleId, auth.currentUserOrNull()!!.id)

fun Throwable.isLastAdmin() = this is RestException && "last admin" in message.orEmpty()
