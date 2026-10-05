package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Timeline API (see CONTEXT.md): view timeline in the database, over Appointments, Visit Notes and timeline_events.

/**
 * An Appointment being scheduled, its Visit Note being saved, an event about it (a Driver confirming), or an access
 * change, a given dose, a Check-in, a Document or an Export, by [by] at [at]. Title, provider and start are the Appointment's, null for the last two.
 */
@Serializable
data class TimelineEntry(
    val kind: Kind,
    @SerialName("appointment_id") val appointmentId: String?,
    val by: String,
    /** Stays after they become a Former Member. */
    @SerialName("by_name") val byName: String? = null,
    val at: Instant,
    val title: String? = null,
    val provider: String? = null,
    @SerialName("starts_at") val startsAt: Instant? = null,
    @SerialName("next_steps") val nextSteps: List<String> = emptyList(),
    val notes: String = "",
    /** An event's text, fixed when it happened. */
    val text: String = "",
) {
    @Suppress("EnumEntryName") enum class Kind { appointment, visit_note, drive_confirmed, access_change, dose_given, check_in, document, export, recipient_press, dose_change }
}

// ponytail: the whole history in one read; page it when a Care Circle's gets long.
suspend fun SupabaseClient.timeline(circleId: String): List<TimelineEntry> =
    from("timeline").select {
        filter { eq("circle_id", circleId) }
        order("at", Order.DESCENDING)
    }.decodeList()
