package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant

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

/** A swap waiting for my answer (v3 `inbox`): [from] asks me to take [day]. */
@Serializable
data class SwapAsk(
    @SerialName("swap_id") val swapId: String,
    @SerialName("duty_id") val dutyId: String,
    val name: String,
    @SerialName("time_of_day") val timeOfDay: LocalTime,
    val day: LocalDate,
    @SerialName("from_id") val from: String,
    @SerialName("asked_at") val askedAt: Instant,
)

/** Swaps still open that ask me to take a day from [today] on, soonest first. */
suspend fun SupabaseClient.swapsToMe(circleId: String, today: LocalDate): List<SwapAsk> =
    postgrest.rpc("swaps_to_me", buildJsonObject { put("circle", circleId); put("from_day", today.toString()) }).decodeList()

/** Only the Member asked can answer, once; accepting makes them the holder that day. */
suspend fun SupabaseClient.answerSwap(swapId: String, accept: Boolean) {
    postgrest.rpc("answer_swap", buildJsonObject { put("swap", swapId); put("accept", accept) })
}

/** A Member volunteers for someone else's [day], settling any swap asked for it (invitee "Saya ambil"). */
suspend fun SupabaseClient.takeTurn(dutyId: String, day: LocalDate) {
    postgrest.rpc("take_turn", buildJsonObject { put("duty", dutyId); put("on_day", day.toString()) })
}

/** v3 away reasons: Sakit, Bepergian, Pekerjaan, Lainnya. */
enum class AwayReason { sick, travelling, work, other }

/**
 * "Kirim N permintaan": marks me Away for the week of [today] and asks each Member for the day paired with them
 * (each gets a WhatsApp YA/TIDAK), all or none.
 */
suspend fun SupabaseClient.goAway(circleId: String, today: LocalDate, reason: AwayReason, asks: List<Pair<DutyTurn, String>>) {
    postgrest.rpc("go_away", buildJsonObject {
        put("circle", circleId)
        put("on_week", weekOf(today).toString())
        put("why", reason.name)
        putJsonArray("asks") { asks.forEach { (t, to) -> addJsonObject { put("duty", t.dutyId); put("day", t.day.toString()); put("member", to) } } }
    })
}

/** Whether I am Away the week of [day] (v3's yellow banner). */
suspend fun SupabaseClient.isAway(circleId: String, day: LocalDate): Boolean =
    from("aways").select {
        filter { eq("circle_id", circleId); eq("user_id", auth.currentUserOrNull()!!.id); eq("week", weekOf(day).toString()) }
    }.decodeList<JsonObject>().isNotEmpty()

/** One Member's month in v3's month view: drives, Duty days ("telepon") and doses, Documents and Expenses ("lainnya"). */
@Serializable
data class MonthLoad(@SerialName("member_id") val memberId: String, val drives: Int, val calls: Int, val other: Int)

/** Every current Member's load in the month of [day], as far as I may see. */
suspend fun SupabaseClient.monthLoad(circleId: String, day: LocalDate): List<MonthLoad> =
    postgrest.rpc("month_load", buildJsonObject { put("circle", circleId); put("on_day", day.toString()) }).decodeList()
