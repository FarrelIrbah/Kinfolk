package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

// Offline cache (#14): read-only, kept on the phone after each load and shown until the next one succeeds; never written back.

/** What the signed-in Member can read without a connection: Home, `appt`, `summary`, Obat, Linimasa and Emergency Info. */
@Serializable
data class Snapshot(
    val circle: CareCircle,
    val recipient: CareRecipient?,
    /** The earliest Appointment since the start of today, with its Questions and Visit Note. */
    val next: Appointment?,
    val questions: List<Question>,
    val note: VisitNote?,
    val medications: List<Medication>,
    val members: List<Member>,
    val timeline: List<TimelineEntry>,
    /** Emergency Info skips Data Category (ADR 0003). */
    val emergencyMedications: List<Medication>,
    val contacts: List<CareContact>,
    val card: EmergencyCard?,
    /** [today]'s Dose Logs: offline the ring still counts them. */
    val doses: List<DoseLog> = emptyList(),
    val today: LocalDate? = null,
    val savedAt: Instant = Clock.System.now(),
)

/** Everything a [Snapshot] holds, as the server lets the signed-in Member read it now; null without a Care Circle. */
suspend fun SupabaseClient.snapshot(since: Instant, today: LocalDate): Snapshot? {
    val circle = myCareCircle() ?: return null
    val recipient = careRecipients(circle.id).firstOrNull()
    val next = nextAppointment(circle.id, since)
    return Snapshot(
        circle, recipient, next, next?.let { questions(it.id) }.orEmpty(), next?.let { visitNote(it.id) },
        medications(circle.id), members(circle.id), timeline(circle.id),
        recipient?.let { emergencyMedications(it.id) }.orEmpty(), careContacts(circle.id), recipient?.let { emergencyCard(it.id) },
        doseLogs(circle.id, today), today,
    )
}
