package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
    fun draft() = MedicationDraft(circleId, recipientId, name, dose, schedule, timeOfDay, active)
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

/** The first of these (in time order) due at or after [now], else the first one tomorrow. */
fun List<Medication>.next(now: LocalTime): Medication? = firstOrNull { it.timeOfDay >= now } ?: firstOrNull()

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
