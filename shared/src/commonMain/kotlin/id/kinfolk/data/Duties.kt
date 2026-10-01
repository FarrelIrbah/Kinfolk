package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Duty API (see CONTEXT.md): the database works out who holds each Duty on each day (function duty_week).

/** The Monday a week starts on. */
fun weekOf(day: LocalDate): LocalDate = day - DatePeriod(days = day.dayOfWeek.ordinal)

/** One Duty on one [day]: who holds it, and a swap waiting for [swapTo] to agree, if any. */
@Serializable
data class DutyTurn(
    @SerialName("duty_id") val dutyId: String,
    val name: String, // "Telepon cek malam"
    @SerialName("time_of_day") val timeOfDay: LocalTime,
    val day: LocalDate,
    /** Null only when nobody in [rotation] is still a Member. */
    val holder: String? = null,
    /** The Members it passes between, in order; Former Members drop out. */
    val rotation: List<String> = emptyList(),
    @SerialName("swap_id") val swapId: String? = null,
    @SerialName("swap_to") val swapTo: String? = null,
)

/**
 * Admins only. Creates a Duty, its first in [order] holding [today], or with [id] changes one, keeping whoever holds
 * [today] if still in [order]. Returns its id.
 */
suspend fun SupabaseClient.saveDuty(circleId: String, name: String, timeOfDay: LocalTime, order: List<String>, today: LocalDate, id: String? = null): String =
    postgrest.rpc("save_duty", buildJsonObject {
        put("circle", circleId)
        put("duty", id)
        put("duty_name", name)
        put("at_time", timeOfDay.toString())
        putJsonArray("rotation") { order.forEach { add(it) } }
        put("on_day", today.toString())
    }).decodeAs()

/** Admins only. Its swaps go with it. */
suspend fun SupabaseClient.deleteDuty(id: String) {
    postgrest.rpc("delete_duty", buildJsonObject { put("duty", id) })
}

/** Every Duty in [circleId] each day of the week of [day], oldest Duty first, then Monday first; empty for anyone who isn't a Member. */
suspend fun SupabaseClient.dutyWeek(circleId: String, day: LocalDate): List<DutyTurn> =
    postgrest.rpc("duty_week", buildJsonObject { put("circle", circleId); put("on_week", weekOf(day).toString()) }).decodeList()

/** The holder asks [to] to take their [day]. Returns the swap's id. */
suspend fun SupabaseClient.askSwap(dutyId: String, day: LocalDate, to: String): String =
    postgrest.rpc("ask_swap", buildJsonObject { put("duty", dutyId); put("on_day", day.toString()); put("member", to) }).decodeAs()

/** Only the Member asked can answer, once; accepting makes them the holder that day. */
suspend fun SupabaseClient.answerSwap(swapId: String, accept: Boolean) {
    postgrest.rpc("answer_swap", buildJsonObject { put("swap", swapId); put("accept", accept) })
}

/** A Member volunteers for someone else's [day], settling any swap asked for it (invitee "Saya ambil"). */
suspend fun SupabaseClient.takeTurn(dutyId: String, day: LocalDate) {
    postgrest.rpc("take_turn", buildJsonObject { put("duty", dutyId); put("on_day", day.toString()) })
}
