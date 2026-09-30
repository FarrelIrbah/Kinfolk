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

// Care Circle API used by the app and by the seam tests (see CONTEXT.md for the terms).

/** "Beliau adalah" choices on onb1, relative to the Member who adds the Care Recipient. */
enum class Relation(val key: String) { Father("father"), Mother("mother"), Grandparent("grandparent"), Spouse("spouse") }

/** "Apa yang paling sering perlu dikoordinasi?" choices on onb1. */
enum class Need(val key: String) { Visits("visits"), Medicines("medicines"), CheckIns("check_ins"), Paperwork("paperwork") }

enum class Role { admin, sibling, parent, viewer }

data class CareCircle(val id: String, val name: String, val memberCount: Int)

@Serializable
data class CareRecipient(val id: String, @SerialName("circle_id") val circleId: String, val name: String, val relation: String? = null)

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
