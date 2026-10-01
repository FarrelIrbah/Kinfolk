package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Check-in API (see CONTEXT.md): v3's `checkin`, one per Care Recipient per day, readable by every Member.

/** "Sudah makan malam?" Ya / Sedikit / Tidak. */
@Suppress("EnumEntryName") enum class Ate { yes, some, no }

/** "Suasana hati" Baik / Biasa / Murung. */
@Suppress("EnumEntryName") enum class Mood { good, okay, low }

/** What `checkin` asks. Blood pressure in mmHg. */
@Serializable
data class CheckInDraft(val sys: Int, val dia: Int, val ate: Ate, val walked: Boolean, val mood: Mood, val note: String) {
    /** v3: below 70/40 the reading is missing ("Isi tekanan darah."). */
    val hasBloodPressure get() = sys >= 70 && dia >= 40
    /** 140 or more tells the other Members (v3 looks at the systolic only). */
    val high get() = sys >= 140
}

/** [by] logged it (or changed it last) at [at]. */
@Serializable
data class CheckIn(
    val day: LocalDate,
    val by: String,
    val at: Instant,
    val sys: Int,
    val dia: Int,
    val ate: Ate,
    val walked: Boolean,
    val mood: Mood,
    val note: String = "",
    /** The other Members were told of 140 or more; they aren't told again for this one. */
    val alerted: Boolean = false,
) {
    val draft get() = CheckInDraft(sys, dia, ate, walked, mood, note)
}

/** Logs [recipient]'s Check-in for [day], or changes it ("Ubah"). */
suspend fun SupabaseClient.saveCheckIn(recipient: CareRecipient, day: LocalDate, c: CheckInDraft) {
    from("check_ins").upsert(buildJsonObject {
        put("circle_id", recipient.circleId); put("recipient_id", recipient.id); put("day", day.toString())
        put("sys", c.sys); put("dia", c.dia); put("ate", c.ate.name); put("walked", c.walked); put("mood", c.mood.name)
        put("note", c.note.trim())
    }) { onConflict = "recipient_id,day" }
}

suspend fun SupabaseClient.checkIn(recipientId: String, day: LocalDate): CheckIn? =
    from("check_ins").select { filter { eq("recipient_id", recipientId); eq("day", day.toString()) } }.decodeSingleOrNull()

/** The Care Recipient's number, "+62…", while they are a Member; null otherwise. */
suspend fun SupabaseClient.recipientPhone(recipientId: String): String? =
    postgrest.rpc("recipient_phone", buildJsonObject { put("recipient", recipientId) }).decodeAs<String?>()
