package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

// Appointment and Provider API used by the app and by the seam tests (see CONTEXT.md for the terms).

@Serializable
data class Provider(val id: String, @SerialName("circle_id") val circleId: String, val name: String)

/** What a Member fills in when scheduling or editing an Appointment. */
@Serializable
data class AppointmentDraft(
    @SerialName("circle_id") val circleId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("provider_id") val providerId: String,
    val title: String,
    val location: String?,
    @SerialName("starts_at") val startsAt: Instant,
    @SerialName("departs_at") val departsAt: Instant? = null,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("attendee_id") val attendeeId: String? = null,
    val bring: String = "",
)

@Serializable
data class Appointment(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    @SerialName("recipient_id") val recipientId: String,
    val provider: Provider,
    val title: String,
    val location: String? = null,
    @SerialName("starts_at") val startsAt: Instant,
    @SerialName("departs_at") val departsAt: Instant? = null,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("attendee_id") val attendeeId: String? = null,
    val bring: String = "",
) {
    fun draft() = AppointmentDraft(circleId, recipientId, provider.id, title, location, startsAt, departsAt, driverId, attendeeId, bring)
}

private val withProvider = Columns.raw("*,provider:providers(*)")

suspend fun SupabaseClient.addProvider(circleId: String, name: String): Provider =
    from("providers").insert(buildJsonObject { put("circle_id", circleId); put("name", name) }) { select() }.decodeSingle()

suspend fun SupabaseClient.providers(circleId: String): List<Provider> =
    from("providers").select { filter { eq("circle_id", circleId) }; order("name", Order.ASCENDING) }.decodeList()

suspend fun SupabaseClient.scheduleAppointment(draft: AppointmentDraft): Appointment =
    from("appointments").insert(draft) { select(withProvider) }.decodeSingle()

/** Silently does nothing to an Appointment the signed-in Member can't see. */
suspend fun SupabaseClient.editAppointment(id: String, draft: AppointmentDraft) {
    from("appointments").update(draft) { filter { eq("id", id) } }
}

/** Silently does nothing to an Appointment the signed-in Member can't see. */
suspend fun SupabaseClient.cancelAppointment(id: String) {
    from("appointments").update(buildJsonObject { put("cancelled_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
}

/** The earliest Appointment in [circleId] starting at or after [since] that isn't cancelled. */
suspend fun SupabaseClient.nextAppointment(circleId: String, since: Instant): Appointment? =
    from("appointments").select(withProvider) {
        filter { eq("circle_id", circleId); gte("starts_at", since.toString()); exact("cancelled_at", null) }
        order("starts_at", Order.ASCENDING)
        limit(1)
    }.decodeList<Appointment>().firstOrNull()

/** Every Appointment with this Provider that wasn't cancelled, wherever it took place, latest first. */
suspend fun SupabaseClient.appointmentsWith(providerId: String): List<Appointment> =
    from("appointments").select(withProvider) {
        filter { eq("provider_id", providerId); exact("cancelled_at", null) }
        order("starts_at", Order.DESCENDING)
    }.decodeList()
