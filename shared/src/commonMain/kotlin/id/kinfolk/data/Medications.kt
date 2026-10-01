package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Medication and Care Contact API used by the app and by the seam tests (see CONTEXT.md for the terms).

@Serializable
data class MedicationDraft(
    @SerialName("circle_id") val circleId: String,
    @SerialName("recipient_id") val recipientId: String,
    val name: String,
    val dose: String,
    val schedule: String, // "pagi, sesudah makan"
    @SerialName("time_of_day") val timeOfDay: LocalTime,
    val active: Boolean = true,
    val note: String = "",
    @SerialName("blood_thinner") val bloodThinner: Boolean = false,
    @SerialName("refill_on") val refillOn: LocalDate? = null,
    /** The Member who refills it. */
    @SerialName("refill_by") val refillBy: String? = null,
)

@Serializable
data class Medication(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    @SerialName("recipient_id") val recipientId: String,
    val name: String,
    val dose: String,
    val schedule: String,
    @SerialName("time_of_day") val timeOfDay: LocalTime,
    val active: Boolean,
    val note: String = "",
    @SerialName("blood_thinner") val bloodThinner: Boolean = false,
    @SerialName("refill_on") val refillOn: LocalDate? = null,
    @SerialName("refill_by") val refillBy: String? = null,
) {
    fun draft() = MedicationDraft(circleId, recipientId, name, dose, schedule, timeOfDay, active, note, bloodThinner, refillOn, refillBy)
}

suspend fun SupabaseClient.addMedication(draft: MedicationDraft): Medication =
    from("medications").insert(draft) { select() }.decodeSingle()

/** Silently does nothing to a Medication the signed-in Member can't see. */
suspend fun SupabaseClient.editMedication(id: String, draft: MedicationDraft) {
    from("medications").update(draft) { filter { eq("id", id) } }
}

/** Every Medication in [circleId], active or not, by time of day. */
suspend fun SupabaseClient.medications(circleId: String): List<Medication> =
    from("medications").select {
        filter { eq("circle_id", circleId) }
        order("time_of_day", Order.ASCENDING)
        order("name", Order.ASCENDING)
    }.decodeList()

/** The Medications Tukiman takes now; the ones he stopped don't count. */
fun List<Medication>.current() = filter { it.active }

/** [medicationId] was given on [day] by the Member [givenBy] ("Diberikan"). */
@Serializable
data class DoseLog(
    @SerialName("medication_id") val medicationId: String,
    val day: LocalDate,
    @SerialName("given_by") val givenBy: String,
    val at: Instant,
)

/** Marks [med] given on [day] by the signed-in Member; marking it again changes nothing. */
suspend fun SupabaseClient.giveDose(med: Medication, day: LocalDate) = giveDoses(listOf(med), day)

/** [giveDose] for all of [meds] in one write, so "Tandai semua diberikan" saves all or none. */
suspend fun SupabaseClient.giveDoses(meds: List<Medication>, day: LocalDate) {
    from("dose_logs").upsert(meds.map { med ->
        buildJsonObject { put("circle_id", med.circleId); put("medication_id", med.id); put("day", day.toString()) }
    }) { onConflict = "medication_id,day"; ignoreDuplicates = true }
}

/** The morning doses (before 12.00) of these current Medications, for Home's morning card. */
fun List<Medication>.morning() = filter { it.timeOfDay < LocalTime(12, 0) }

/** Untoggles "Diberikan": the Dose Log and its Timeline entry go. */
suspend fun SupabaseClient.takeBackDose(med: Medication, day: LocalDate) {
    from("dose_logs").delete { filter { eq("medication_id", med.id); eq("day", day.toString()) } }
}

/** [circleId]'s Dose Logs on [day], of the Medications the signed-in Member sees. */
suspend fun SupabaseClient.doseLogs(circleId: String, day: LocalDate): List<DoseLog> =
    from("dose_logs").select { filter { eq("circle_id", circleId); eq("day", day.toString()) } }.decodeList()

/** Home's ring: [given] of [total] of today's doses, and the [next] one not yet given (else the first tomorrow). */
data class DoseProgress(val given: Int, val total: Int, val next: Medication?)

/** Of these current Medications (in time order), how today's [logs] stand. */
fun List<Medication>.progress(logs: List<DoseLog>): DoseProgress {
    val given = logs.map { it.medicationId }.toSet()
    return DoseProgress(count { it.id in given }, size, firstOrNull { it.id !in given } ?: firstOrNull())
}

/** Prototype groups on `contacts`: "Medis", "Rumah & tetangga", "Darurat". */
@Serializable
enum class ContactGroup { @SerialName("medical") Medical, @SerialName("home") Home, @SerialName("emergency") Emergency }

@Serializable
data class CareContactDraft(
    @SerialName("circle_id") val circleId: String,
    val name: String,
    val relationship: String,
    val phone: String, // E.164
    @SerialName("grp") val group: ContactGroup,
    val emergency: Boolean = false,
)

@Serializable
data class CareContact(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    val name: String,
    val relationship: String,
    val phone: String,
    @SerialName("grp") val group: ContactGroup,
    val emergency: Boolean,
) {
    fun draft() = CareContactDraft(circleId, name, relationship, phone, group, emergency)
}

suspend fun SupabaseClient.addCareContact(draft: CareContactDraft): CareContact =
    from("care_contacts").insert(draft) { select() }.decodeSingle()

/** Silently does nothing to a Care Contact the signed-in Member can't see. */
suspend fun SupabaseClient.editCareContact(id: String, draft: CareContactDraft) {
    from("care_contacts").update(draft) { filter { eq("id", id) } }
}

/** Silently does nothing to a Care Contact the signed-in Member can't see. */
suspend fun SupabaseClient.removeCareContact(id: String) {
    from("care_contacts").delete { filter { eq("id", id) } }
}

suspend fun SupabaseClient.careContacts(circleId: String): List<CareContact> =
    from("care_contacts").select { filter { eq("circle_id", circleId) }; order("name", Order.ASCENDING) }.decodeList()
