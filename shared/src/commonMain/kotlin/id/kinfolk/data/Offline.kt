package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

// Offline cache (#14): read-only, kept on the phone after each load and shown until the next one succeeds; never written back.

/** What the signed-in Member can read without a connection: Home (with its Tasks row), `appt`, `summary`, Obat, Linimasa and Emergency Info. */
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
    val contacts: List<CareContact>,
    val card: EmergencyCard?,
    /** [today]'s Dose Logs: offline the ring still counts them. */
    val doses: List<DoseLog> = emptyList(),
    val today: LocalDate? = null,
    /** [today]'s Check-in, for the evening card. */
    val checkIn: CheckIn? = null,
    /** Home's Tasks row. */
    val tasks: List<Task> = emptyList(),
    /** Skips Data Category (ADR 0003). */
    val emergency: EmergencyInfo? = null,
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
        careContacts(circle.id), recipient?.let { emergencyCard(it.id) },
        doseLogs(circle.id, today), today, recipient?.let { checkIn(it.id, today) }, tasks(circle.id), recipient?.let { emergencyInfo(it.id) },
    )
}
