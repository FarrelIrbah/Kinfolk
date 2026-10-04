package id.kinfolk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Appointment
import id.kinfolk.data.Expense
import id.kinfolk.data.Document
import id.kinfolk.data.documentFile
import id.kinfolk.data.documents
import id.kinfolk.data.latest
import id.kinfolk.data.uploadDocument
import id.kinfolk.ui.PickedFile
import id.kinfolk.ui.rememberFilePicker
import id.kinfolk.ui.rememberFileViewer
import id.kinfolk.ui.records.DocFormScreen
import id.kinfolk.ui.records.Docs
import id.kinfolk.ui.records.RecTab
import id.kinfolk.ui.records.seenBy
import id.kinfolk.data.addExpense
import id.kinfolk.data.expenses
import id.kinfolk.ui.records.Costs
import kinfolk.shared.generated.resources.expense_missing
import id.kinfolk.data.DutyTurn
import id.kinfolk.data.SwapAsk
import id.kinfolk.data.swapsToMe
import id.kinfolk.ui.inbox.InboxItem
import id.kinfolk.ui.inbox.InboxRow
import id.kinfolk.ui.inbox.InboxScreen
import id.kinfolk.ui.inbox.inbox
import id.kinfolk.ui.inbox.lateSub
import id.kinfolk.ui.inbox.lateTitle
import id.kinfolk.ui.inbox.swapSub
import id.kinfolk.ui.inbox.swapTitle
import id.kinfolk.data.answerSwap
import id.kinfolk.data.appointmentsBetween
import id.kinfolk.data.askSwap
import id.kinfolk.data.goAway
import id.kinfolk.data.isAway
import id.kinfolk.data.monthLoad
import id.kinfolk.data.MonthLoad
import id.kinfolk.data.deleteDuty
import id.kinfolk.data.dutyWeek
import id.kinfolk.data.saveDuty
import id.kinfolk.data.takeTurn
import id.kinfolk.data.weekOf
import id.kinfolk.ui.Sheet
import id.kinfolk.ui.appointment.dayName
import id.kinfolk.ui.appointment.shortDay
import id.kinfolk.ui.home.DutyDay
import id.kinfolk.ui.rota.AwaySheet
import id.kinfolk.ui.rota.DutyFormScreen
import id.kinfolk.ui.rota.RotaScreen
import id.kinfolk.ui.rota.SwapSheet
import id.kinfolk.ui.rota.inSentence
import id.kinfolk.ui.rota.handOff
import id.kinfolk.ui.rota.listing
import id.kinfolk.ui.rota.load
import id.kinfolk.data.AppointmentDraft
import id.kinfolk.data.CareCircle
import id.kinfolk.data.CareContact
import id.kinfolk.data.CareContactDraft
import id.kinfolk.data.CareRecipient
import id.kinfolk.data.EmergencyCard
import id.kinfolk.data.emergencyCard
import id.kinfolk.data.reissueEmergencyCard
import id.kinfolk.data.saveEmergencyInfo
import id.kinfolk.data.EmergencyDraft
import id.kinfolk.data.EmergencyInfo
import id.kinfolk.data.emergencyFallback
import id.kinfolk.data.emergencyInfo
import id.kinfolk.data.emergencyCardPdf
import id.kinfolk.data.setEmergencyContact
import id.kinfolk.ui.rememberPrinter
import id.kinfolk.data.Invitation
import id.kinfolk.data.InvitationToMe
import id.kinfolk.data.Role
import id.kinfolk.data.acceptInvitation
import id.kinfolk.data.cancelInvitation
import id.kinfolk.data.invitations
import id.kinfolk.data.invite
import id.kinfolk.data.myInvitations
import id.kinfolk.data.roleIn
import id.kinfolk.data.DoseLog
import id.kinfolk.data.CheckIn
import id.kinfolk.data.checkIn
import id.kinfolk.data.recipientPhone
import id.kinfolk.data.sayFine
import id.kinfolk.data.askHelp
import id.kinfolk.data.organizerPhone
import id.kinfolk.data.lastDigest
import id.kinfolk.data.recentCheckIns
import id.kinfolk.data.saveCheckIn
import id.kinfolk.ui.checkin.CheckInScreen
import id.kinfolk.data.Task
import id.kinfolk.data.addTask
import id.kinfolk.data.editTask
import id.kinfolk.data.markTaskDone
import id.kinfolk.data.remindTask
import id.kinfolk.data.tasks as taskList
import id.kinfolk.ui.tasks.TaskFormScreen
import id.kinfolk.data.Note
import id.kinfolk.data.addNote
import id.kinfolk.data.notes as noteList
import id.kinfolk.ui.notes.NotesScreen
import id.kinfolk.ui.export.ExportScreen
import id.kinfolk.ui.export.DocsPicker
import id.kinfolk.ui.export.exportDate
import id.kinfolk.ui.export.inSentence
import id.kinfolk.data.ExportContent
import id.kinfolk.data.ExportSection
import id.kinfolk.data.export
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kinfolk.shared.generated.resources.export_done
import kinfolk.shared.generated.resources.export_for_line
import kinfolk.shared.generated.resources.export_none
import kinfolk.shared.generated.resources.trends_attached
import kinfolk.shared.generated.resources.note_saved_private
import kinfolk.shared.generated.resources.note_saved_shared
import id.kinfolk.ui.tasks.TasksScreen
import id.kinfolk.ui.tasks.overdue
import kinfolk.shared.generated.resources.reminded_toast
import kinfolk.shared.generated.resources.bp_sees_all
import kinfolk.shared.generated.resources.bp_sees_part
import kinfolk.shared.generated.resources.bp_calling
import kinfolk.shared.generated.resources.bp_fine_sent
import kinfolk.shared.generated.resources.bp_no_appt
import kinfolk.shared.generated.resources.bp_call
import kinfolk.shared.generated.resources.digest_sub
import kinfolk.shared.generated.resources.task_done_toast
import kinfolk.shared.generated.resources.tasks_row
import kinfolk.shared.generated.resources.tasks_row_late
import kinfolk.shared.generated.resources.tasks_row_ok
import kinfolk.shared.generated.resources.tasks_row_owner
import id.kinfolk.ui.checkin.names
import id.kinfolk.ui.home.Evening
import kinfolk.shared.generated.resources.ci_no_bp
import kinfolk.shared.generated.resources.ci_saved
import kinfolk.shared.generated.resources.ci_saved_high
import androidx.compose.ui.platform.LocalUriHandler
import id.kinfolk.data.Medication
import id.kinfolk.data.giveDoses
import id.kinfolk.data.morning
import id.kinfolk.data.progress
import id.kinfolk.data.takeBackDose
import id.kinfolk.data.MedicationDraft
import id.kinfolk.data.addCareContact
import id.kinfolk.data.addMedication
import id.kinfolk.data.careContacts
import id.kinfolk.data.current
import id.kinfolk.data.editCareContact
import id.kinfolk.data.editMedication
import id.kinfolk.data.medications
import id.kinfolk.data.AccessChange
import id.kinfolk.data.DataCategory
import id.kinfolk.data.accessChanges
import id.kinfolk.data.Hidden
import id.kinfolk.data.hidden
import id.kinfolk.data.hideByDefault
import id.kinfolk.data.setHidden
import id.kinfolk.data.removeCareContact
import id.kinfolk.data.Provider
import id.kinfolk.data.Question
import id.kinfolk.data.VisitNote
import id.kinfolk.data.askQuestion
import id.kinfolk.data.questions
import id.kinfolk.data.saveVisitNote
import id.kinfolk.data.visitNote
import id.kinfolk.data.addProvider
import id.kinfolk.data.appointment
import id.kinfolk.data.TimelineEntry
import id.kinfolk.data.Hit
import id.kinfolk.data.search
import id.kinfolk.ui.search.SearchKind
import id.kinfolk.ui.search.SearchRow
import id.kinfolk.ui.search.SearchScreen
import id.kinfolk.ui.search.medSub
import id.kinfolk.ui.search.suggestions
import id.kinfolk.ui.tasks.dueLabel
import kinfolk.shared.generated.resources.blood_thinner
import id.kinfolk.data.timeline
import id.kinfolk.data.cancelAppointment
import id.kinfolk.data.careRecipients
import id.kinfolk.data.editAppointment
import id.kinfolk.data.nextAppointment
import id.kinfolk.data.providers
import id.kinfolk.data.scheduleAppointment
import id.kinfolk.data.createCareCircle
import id.kinfolk.data.kinfolkClient
import id.kinfolk.data.Snapshot
import id.kinfolk.data.snapshot
import id.kinfolk.ui.rememberKept
import id.kinfolk.ui.appointment.updatedAgo
import kinfolk.shared.generated.resources.offline
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import id.kinfolk.data.sendSignInCode
import id.kinfolk.data.verifySignInCode
import id.kinfolk.ui.Kf
import id.kinfolk.ui.KfEase
import id.kinfolk.ui.KinfolkTheme
import id.kinfolk.ui.SvgPath
import id.kinfolk.ui.Toast
import id.kinfolk.ui.rememberShare
import id.kinfolk.ui.emergency.EmergencyFormScreen
import id.kinfolk.ui.emergency.EmergencyScreen
import id.kinfolk.ui.emergency.QrScreen
import id.kinfolk.ui.appointment.ApptFormScreen
import id.kinfolk.ui.appointment.ApptScreen
import id.kinfolk.ui.appointment.VisitNoteScreen
import id.kinfolk.ui.appointment.ago
import id.kinfolk.ui.appointment.countdown
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.longDate
import id.kinfolk.ui.appointment.whenLabel
import id.kinfolk.ui.appointment.withWhom
import id.kinfolk.ui.bapak.BapakScreen
import id.kinfolk.ui.home.DigestScreen
import id.kinfolk.ui.bapak.Kid
import id.kinfolk.ui.bapak.greeting
import id.kinfolk.ui.bapak.helpLine
import id.kinfolk.ui.bapak.todayPlan
import id.kinfolk.ui.circle.CircleMember
import id.kinfolk.ui.circle.CircleScreen
import id.kinfolk.ui.circle.MemberScreen
import id.kinfolk.ui.circle.HistoryLine
import id.kinfolk.ui.circle.label
import id.kinfolk.ui.appointment.changeMeta
import id.kinfolk.ui.Confirm
import id.kinfolk.ui.ConfirmSheet
import id.kinfolk.ui.onboarding.InviteColors
import id.kinfolk.data.Member
import id.kinfolk.data.isLastAdmin
import id.kinfolk.data.leaveCareCircle
import id.kinfolk.data.members
import id.kinfolk.data.promoteToAdmin
import id.kinfolk.data.removeMember
import id.kinfolk.ui.contacts.ContactFormScreen
import id.kinfolk.ui.contacts.ContactsScreen
import id.kinfolk.ui.home.FeedItem
import id.kinfolk.ui.home.HomeCard
import id.kinfolk.ui.home.HomeScreen
import id.kinfolk.ui.home.Morning
import id.kinfolk.ui.home.MorningDose
import id.kinfolk.ui.home.homeCard
import id.kinfolk.ui.home.NextAppointment
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.onboarding.CodeScreen
import id.kinfolk.ui.onboarding.Onb0
import id.kinfolk.ui.onboarding.Invitee
import id.kinfolk.ui.onboarding.Onb1
import id.kinfolk.ui.onboarding.Onb2
import id.kinfolk.ui.onboarding.Onb3
import id.kinfolk.ui.onboarding.PhoneScreen
import id.kinfolk.ui.onboarding.e164
import id.kinfolk.ui.records.MedFormScreen
import id.kinfolk.ui.records.RecordsScreen
import id.kinfolk.ui.timeline.EntryType
import id.kinfolk.ui.timeline.TimelineRow
import id.kinfolk.ui.timeline.TimelineScreen
import id.kinfolk.ui.timeline.text
import id.kinfolk.ui.timeline.type
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.tab_circle
import kinfolk.shared.generated.resources.tab_home
import kinfolk.shared.generated.resources.tab_records
import kinfolk.shared.generated.resources.tab_rota
import kinfolk.shared.generated.resources.tab_timeline
import kinfolk.shared.generated.resources.no_meds
import kinfolk.shared.generated.resources.dose_marked
import kinfolk.shared.generated.resources.morning_marked
import kinfolk.shared.generated.resources.next_visit
import kinfolk.shared.generated.resources.picks_up
import kinfolk.shared.generated.resources.picks_up_no_time
import kinfolk.shared.generated.resources.never
import kinfolk.shared.generated.resources.qr_revoked
import kinfolk.shared.generated.resources.qr_printed
import kinfolk.shared.generated.resources.you
import kinfolk.shared.generated.resources.cancel
import kinfolk.shared.generated.resources.former_member
import kinfolk.shared.generated.resources.last_admin
import kinfolk.shared.generated.resources.leave_body
import kinfolk.shared.generated.resources.leave_confirm
import kinfolk.shared.generated.resources.leave_title
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.now_admin
import kinfolk.shared.generated.resources.remove_body
import kinfolk.shared.generated.resources.remove_confirm
import kinfolk.shared.generated.resources.remove_title
import kinfolk.shared.generated.resources.legend_other
import kinfolk.shared.generated.resources.legend_you
import kinfolk.shared.generated.resources.away_sent
import kinfolk.shared.generated.resources.and
import kinfolk.shared.generated.resources.swap_declined
import kinfolk.shared.generated.resources.inbox_asked
import kinfolk.shared.generated.resources.inbox_taken
import kinfolk.shared.generated.resources.swap_sent
import kinfolk.shared.generated.resources.swap_taken
import kinfolk.shared.generated.resources.admins_full
import kinfolk.shared.generated.resources.hide_body
import kinfolk.shared.generated.resources.hide_confirm
import kinfolk.shared.generated.resources.hide_title
import kinfolk.shared.generated.resources.now_hidden
import kinfolk.shared.generated.resources.now_shown
import kinfolk.shared.generated.resources.only_recipient
import kinfolk.shared.generated.resources.undo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// Tab icons and fill rule copied from design v3 (tabs 1 and 2 never fill).
enum class Tab(val label: StringResource, val icon: String, val fillsWhenActive: Boolean) {
    Home(Res.string.tab_home, "M3.5 10.5 12 3.5l8.5 7V19.5a1 1 0 0 1-1 1H15v-6H9v6H4.5a1 1 0 0 1-1-1z", true),
    Rota(Res.string.tab_rota, "M4 5.5h16v15H4zM4 10h16M8.5 3v4M15.5 3v4M9 15l2 2 4-4", false),
    Timeline(Res.string.tab_timeline, "M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17zM12 7.5V12l3 2", false),
    Records(Res.string.tab_records, "M8.5 3.5h7v3h-7zM6 5h2.5m7 0H18v15.5H6V5zM9 11.5h6M9 15.5h4", true),
    Circle(Res.string.tab_circle, "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20a6.5 6.5 0 0 1 13 0zM16 4.3a3.5 3.5 0 0 1 0 6.4M18 13.8a6.5 6.5 0 0 1 3.5 6.2", true),
}

private enum class Screen { Onb0, Phone, Code, Onb1, Onb2, Onb3, Invitee, Home, Appt, ApptForm, VisitNote, MedForm, Contacts, ContactForm, Emergency, Qr, EmergencyForm, Member, DutyForm, CheckIn, Tasks, TaskForm, Notes, DocForm, Inbox, Search, Export, Bapak, Digest }

/** How the prototype animates the incoming screen: push slides from the right, back from the left, tab rises. */
private enum class Nav { Push, Back, Tab }

/** An Appointment with its Questions and Visit Note, as `appt` and `summary` show it. */
private class Visit(val appointment: Appointment, val questions: List<Question>, val note: VisitNote?)

@Composable
@Preview
fun App() {
    KinfolkTheme {
        val supabase = remember { kinfolkClient() }
        val scope = rememberCoroutineScope()
        var screen by rememberSaveable { mutableStateOf<Screen?>(null) } // null while checking the saved session
        var nav by remember { mutableStateOf(Nav.Tab) }
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }
        var recTab by rememberSaveable { mutableStateOf(RecTab.Meds) }
        var phone by rememberSaveable { mutableStateOf("") }
        var circle by remember { mutableStateOf<CareCircle?>(null) }
        var recipient by remember { mutableStateOf<CareRecipient?>(null) }
        var next by remember { mutableStateOf<Appointment?>(null) }
        var questions by remember { mutableStateOf(emptyList<Question>()) } // on next
        var note by remember { mutableStateOf<VisitNote?>(null) } // of next
        var opened by remember { mutableStateOf<Visit?>(null) } // from the Timeline; `appt` and `summary` show next while null
        var timeline by remember { mutableStateOf(emptyList<TimelineEntry>()) }
        var timelineFilter by remember { mutableStateOf<EntryType?>(null) } // kept across tabs, like the prototype
        var opening by remember { mutableStateOf<Job?>(null) }
        var editing by remember { mutableStateOf<Appointment?>(null) }
        var providers by remember { mutableStateOf(emptyList<Provider>()) }
        var meds by remember { mutableStateOf(emptyList<Medication>()) }
        var editingMed by remember { mutableStateOf<Medication?>(null) }
        var doses by remember { mutableStateOf(emptyList<DoseLog>()) } // of the day they were read
        val doseWrites = remember { Mutex() }
        val taskWrites = remember { Mutex() } // a tick and its "Urungkan" land in tap order
        var markedMorning by remember { mutableStateOf<LocalDate?>(null) } // keeps "Semua diberikan ✓" up that day
        var checkIn by remember { mutableStateOf<CheckIn?>(null) } // of the day it was read
        var checkIns by remember { mutableStateOf(emptyList<CheckIn>()) } // Kondisi, the last 30
        var expenses by remember { mutableStateOf(emptyList<Expense>()) } // Biaya
        var documents by remember { mutableStateOf(emptyList<Document>()) } // Dokumen, every version
        var uploading by remember { mutableStateOf<PickedFile?>(null) } // the file DocForm saves
        var checkingIn by rememberSaveable { mutableStateOf<String?>(null) } // "2026-10-01 19:00": `checkin`'s day and Duty time, fixed when opened
        var tasks by remember { mutableStateOf(emptyList<Task>()) }
        var editingTask by remember { mutableStateOf<Task?>(null) }
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var contacts by remember { mutableStateOf(emptyList<CareContact>()) }
        var editingContact by remember { mutableStateOf<CareContact?>(null) }
        var invitation by remember { mutableStateOf<InvitationToMe?>(null) }
        var sent by remember { mutableStateOf<List<Invitation>?>(null) } // null unless admin
        var card by remember { mutableStateOf<EmergencyCard?>(null) }
        var stack by rememberSaveable { mutableStateOf(emptyList<Screen>()) } // where back returns to, like v3's `stack`
        var emergencyLoad by remember { mutableStateOf<Job?>(null) }
        var toast by remember { mutableStateOf<String?>(null) }
        var undo by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) } // "Urungkan" while its toast shows
        var members by remember { mutableStateOf(emptyList<Member>()) } // Former Members too, for their names
        var hidden by remember { mutableStateOf(emptyList<Hidden>()) }
        var changes by remember { mutableStateOf(emptyList<AccessChange>()) } // "Riwayat perubahan", newest first
        var emergency by remember { mutableStateOf<EmergencyInfo?>(null) } // skips Data Category (ADR 0003)
        var onboarding by rememberSaveable { mutableStateOf(false) } // onb2 goes on to onb3 right after onb1
        var viewing by rememberSaveable { mutableStateOf<String?>(null) } // on `member`
        var memberError by remember { mutableStateOf<String?>(null) }
        var bapakMsg by remember { mutableStateOf<String?>(null) } // Mode Bapak's green box after a press
        var digest by remember { mutableStateOf<String?>(null) } // my last weekly digest (#40), null before the first
        var organizerPhone by remember { mutableStateOf("") } // "Telepon Sri", read when Mode Bapak opens
        var pressing by remember { mutableStateOf(false) } // a press on its way: a second tap sends nothing
        var confirm by remember { mutableStateOf<Confirm?>(null) }
        var turns by remember { mutableStateOf(emptyList<DutyTurn>()) } // each day this week
        var drives by remember { mutableStateOf(emptyList<Appointment>()) } // this week, with a Driver
        var swapping by remember { mutableStateOf<DutyTurn?>(null) }
        var swapsToMe by remember { mutableStateOf(emptyList<SwapAsk>()) } // `inbox`, from today on
        var rotaMonth by rememberSaveable { mutableStateOf(false) }
        var monthLoads by remember { mutableStateOf(emptyList<MonthLoad>()) }
        var away by remember { mutableStateOf(false) } // this week
        var awayOpen by remember { mutableStateOf(false) }
        var editingDuty by remember { mutableStateOf<DutyTurn?>(null) }
        var offline by remember { mutableStateOf(false) } // the last read failed
        var query by rememberSaveable { mutableStateOf("") }
        var hits by remember { mutableStateOf<List<Hit>?>(null) } // of query, null until the first answer
        var savedAt by remember { mutableStateOf<Instant?>(null) } // of what's on screen
        val kept = rememberKept("snapshot")
        LaunchedEffect(toast) { if (toast != null) { delay(if (undo?.first == toast) 5000 else 2600); toast = null } }
        val share = rememberShare()
        val printPdf = rememberPrinter()
        val revoked = stringResource(Res.string.qr_revoked)
        val printed = stringResource(Res.string.qr_printed)
        val tz = remember { TimeZone.currentSystemDefault() }
        val now by produceState(Clock.System.now()) { while (true) { delay(30_000); value = Clock.System.now() } }
        // "Anda" in Budi's avatar color from the prototype, since Sri's green vanishes on the green Home card.
        val you = Person(stringResource(Res.string.you), Color(0xFFB0643A))
        val former = stringResource(Res.string.former_member) // "%1$s · Mantan anggota"
        fun me() = supabase.auth.currentUserOrNull()?.id
        // Reads only: retries until reachable, showing the offline banner meanwhile.
        suspend fun <T> retrying(block: suspend () -> T): T {
            while (true) {
                try {
                    return block().also { offline = false }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    offline = true
                    delay(3000)
                }
            }
        }
        // Approved in #5: yourself in Sri's green, the others in join order in Budi's, Dewi's, Agus's and Rina's, Former Members muted.
        fun colorOf(id: String): Color {
            val m = members.firstOrNull { it.userId == id }
            return when {
                id == me() -> Kf.Green
                m?.leftAt != null -> Kf.Muted
                id == recipient?.memberId -> Kf.Ink // v3 `col('pak')`
                else -> InviteColors[members.filter { it.userId != me() && it.userId != recipient?.memberId && it.leftAt == null }.indexOf(m).coerceAtLeast(0) % 4]
            }
        }
        fun person(id: String?): Person? {
            if (id == null) return null
            if (id == me()) return you
            val m = members.firstOrNull { it.userId == id } ?: return null
            val name = m.name.orEmpty()
            return Person(if (m.leftAt != null) former.replace("%1\$s", name) else name, colorOf(id))
        }
        // On the Timeline yourself too by name, in Sri's green (#8, like the prototype).
        fun named(id: String, fallback: String? = null): Person {
            val m = members.firstOrNull { it.userId == id }
            val name = m?.name ?: fallback.orEmpty()
            return Person(if (m?.leftAt != null) former.replace("%1\$s", name) else name, colorOf(id))
        }
        // Mode Bapak presses show the Care Recipient, in v3's `col('pak')` (#37).
        fun author(e: TimelineEntry) = if (e.kind == TimelineEntry.Kind.recipient_press) Person(e.byName.orEmpty(), Kf.Ink) else named(e.by, e.byName)
        fun visit() = opened ?: next?.let { Visit(it, questions, note) }
        // The Care Recipient while they are a Member: they set restrictions, else the admins (ADR 0004).
        fun owner() = recipient?.memberId?.takeIf { id -> members.any { it.userId == id && it.leftAt == null } }
        fun sees(m: Member): Set<DataCategory> =
            if (m.userId == owner() || (owner() == null && m.role == Role.admin)) DataCategory.entries.toSet()
            else DataCategory.entries.toSet() - hidden.filter { it.recipientId == recipient?.id && it.memberId == m.userId }.map { it.category }.toSet()
        fun circleMembers() = members.filter { it.leftAt == null }.sortedWith(compareBy({ it.userId != owner() }, { it.userId != me() })).map {
            CircleMember(it.userId, it.name.orEmpty(), colorOf(it.userId), it.role, it.joinedAt.toLocalDateTime(tz).date, it.userId == me(), sees(it), it.userId == owner(), it.emergency, it.distance, it.inApp)
        }
        // Like v3: push remembers where you came from, back returns there (Home when empty), tabs clear it.
        fun go(to: Screen) { nav = Nav.Push; stack = stack + listOfNotNull(screen); screen = to }
        fun back() { nav = Nav.Back; screen = stack.lastOrNull() ?: Screen.Home; stack = stack.dropLast(1) }
        fun reset(to: Screen, how: Nav = Nav.Tab) { nav = how; stack = emptyList(); screen = to }
        fun pick(t: Tab) { reset(Screen.Home); tab = t }
        fun openNotes() { go(Screen.Notes); scope.launch { circle?.let { c -> notes = retrying { supabase.noteList(c.id) } } } }
        val today = now.toLocalDateTime(tz).date
        val week = weekOf(today)
        val given = doses.filter { it.day == today }.map { it.medicationId }.toSet()
        val progress = meds.current().progress(doses.filter { it.day == today })
        val noConnection = stringResource(Res.string.no_connection)
        val uri = LocalUriHandler.current
        @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current // ponytail: LocalClipboard needs a ClipEntry per platform
        val forLine = stringResource(Res.string.export_for_line) // "Disiapkan untuk %1$s · %2$s"
        fun exportLine(forWhom: String) = forLine.replace("%1\$s", forWhom).replace("%2\$s", exportDate(today))
        var exporting by remember { mutableStateOf(false) }
        var exportDocs by rememberSaveable { mutableStateOf(emptyList<String>()) } // picked on `export`
        var pickingDocs by remember { mutableStateOf(false) }
        // Not retried: every export is logged. The link goes to the clipboard, as the toast says.
        suspend fun exportPdf(preparedFor: String, line: String, sections: Set<ExportSection>, ids: List<String>, done: String) {
            val r = recipient ?: return
            if (exporting) return
            exporting = true
            // Reset even when the screen that asked is left mid-way.
            val url = try { attempt { supabase.export(r.id, preparedFor, line, sections, ids) } } finally { exporting = false }
            if (url == null) { toast = noConnection; return }
            clipboard.setText(AnnotatedString(url))
            toast = done
            timeline = retrying { supabase.timeline(r.circleId) }
        }
        val pickFile = rememberFilePicker { f -> uploading = f; go(Screen.DocForm) }
        val viewFile = rememberFileViewer()
        // The Duty I hold tonight: its time heads the evening card and `checkin`.
        val tonight = turns.firstOrNull { it.day == today && it.holder == me() }
        // Shown at once like the prototype; only these doses are taken back if they can't be saved.
        fun markDoses(ms: List<Medication>, give: Boolean) {
            val ids = ms.map { it.id }.toSet()
            fun flip(on: Boolean) {
                doses = doses.filterNot { it.medicationId in ids && it.day == today } +
                    if (on) ms.map { DoseLog(it.id, today, me().orEmpty(), Clock.System.now()) } else emptyList()
            }
            flip(give)
            // Not retried: offline, the tap is undone rather than sent later. One write at a time, in
            // tap order, so a quick "Tandai" then undo lands in that order.
            scope.launch {
                doseWrites.withLock {
                    val ok = attempt { if (give) supabase.giveDoses(ms, today) else supabase.takeBackDose(ms.single(), today) } != null
                    if (ok) retrying { timeline = supabase.timeline(circle!!.id) } else { flip(!give); toast = noConnection }
                }
            }
        }
        // The rota and "Minggu ini" show everyone by their own name, yourself first in Sri's green (like `circle`).
        fun rotaPeople() = circleMembers().associate { it.id to Person(it.name, it.color) }
        // Who takes Duty days: like v3's SIBS, not the Care Recipient (Role parent) nor viewers.
        fun dutyPeople() = circleMembers().filter { it.role != Role.parent && it.role != Role.viewer }.associate { it.id to Person(it.name, it.color) }
        suspend fun loadRota() {
            val c = circle ?: return
            turns = retrying { supabase.dutyWeek(c.id, week) }
            drives = retrying { supabase.appointmentsBetween(c.id, week.atStartOfDayIn(tz), (week + DatePeriod(days = 7)).atStartOfDayIn(tz)) }
                .filter { it.driverId != null }
            monthLoads = retrying { supabase.monthLoad(c.id, today) }
            away = retrying { supabase.isAway(c.id, today) }
            swapsToMe = retrying { supabase.swapsToMe(c.id, today) }
        }
        fun inboxItems() = inbox(swapsToMe, questions, note != null, tasks, me().orEmpty(), today)
        // Refill Tasks come from the minute job, so the list refreshes on the way in.
        fun openTasks() { go(Screen.Tasks); scope.launch { attempt { supabase.taskList(circle!!.id) }?.let { tasks = it } } }
        // Not retried: offline, the tap does nothing rather than jumping there later. The latest tap wins.
        fun openFromTimeline(e: TimelineEntry) {
            if (e.kind == TimelineEntry.Kind.document || e.kind == TimelineEntry.Kind.export) { pick(Tab.Records); recTab = RecTab.Docs; return } // like v3
            val id = e.appointmentId ?: return // an access change opens nothing
            opening?.cancel()
            opening = scope.launch {
                val v = attempt { listOfNotNull(supabase.appointment(id)).map { a -> Visit(a, supabase.questions(a.id), supabase.visitNote(a.id)) } }
                    ?.firstOrNull() ?: return@launch // unreachable, or cancelled meanwhile
                opened = v
                go(if (e.kind == TimelineEntry.Kind.visit_note) Screen.VisitNote else Screen.Appt)
            }
        }
        // What stays readable offline (#14); the rest (Invitations, restrictions, rota) waits for a connection.
        fun restore(k: Snapshot) {
            circle = k.circle; recipient = k.recipient; next = k.next; questions = k.questions; note = k.note
            meds = k.medications; members = k.members; timeline = k.timeline; emergency = k.emergency
            contacts = k.contacts; card = k.card; savedAt = k.savedAt; doses = k.doses; checkIn = k.checkIn; tasks = k.tasks
        }
        suspend fun loadHome() {
            // The card stays until the day ends: its "Tulis catatan" button is for after the visit.
            val day = Clock.System.now().toLocalDateTime(tz).date
            val k = retrying { supabase.snapshot(since = day.atStartOfDayIn(tz), today = day) }
            // Without a Care Circle (left, removed) nothing stays on the phone.
            kept.write(k?.let { Json.encodeToString(it) })
            if (k == null) { circle = null; return }
            restore(k)
            opened?.appointment?.let { a -> opened = Visit(a, retrying { supabase.questions(a.id) }, retrying { supabase.visitNote(a.id) }) }
            sent = retrying { if (supabase.roleIn(k.circle.id) == Role.admin) supabase.invitations(k.circle.id) else null }
            hidden = retrying { supabase.hidden(k.circle.id) }
            changes = retrying { supabase.accessChanges(k.circle.id) }
            checkIns = k.recipient?.let { r -> retrying { supabase.recentCheckIns(r.id) } }.orEmpty()
            expenses = retrying { supabase.expenses(k.circle.id) }
            documents = retrying { supabase.documents(k.circle.id) }
            digest = retrying { supabase.lastDigest(k.circle.id) }
            loadRota()
        }
        suspend fun land(how: Nav) {
            loadHome()
            // Whoever signs in without a Care Circle but with a pending Invitation to their number sees `invitee`.
            invitation = if (circle == null) retrying { supabase.myInvitations() }.firstOrNull() else null
            when {
                circle != null -> reset(Screen.Home)
                invitation != null -> reset(Screen.Invitee, how)
                else -> reset(Screen.Onb1, how)
            }
        }
        fun openForm(a: Appointment?) = scope.launch {
            editing = a
            providers = circle?.let { c -> retrying { supabase.providers(c.id) } }.orEmpty()
            go(Screen.ApptForm)
        }
        suspend fun loadContacts() {
            contacts = circle?.let { c -> retrying { supabase.careContacts(c.id) } }.orEmpty()
        }
        fun openExport() { exportDocs = emptyList(); go(Screen.Export); scope.launch { loadContacts() } } // emergency contacts for its count
        // SOS opens at once, even offline, with what Home already loaded; the latest info and the card follow when reachable.
        fun openEmergency() {
            go(Screen.Emergency)
            emergencyLoad?.cancel()
            emergencyLoad = scope.launch {
                recipient?.let { r -> emergency = retrying { supabase.emergencyInfo(r.id) } }
                card = recipient?.let { r -> retrying { supabase.emergencyCard(r.id) } }
            }
        }
        suspend fun sendCode(sms: Boolean): Boolean = attempt {
            supabase.sendSignInCode(e164(phone), sms)
        } != null
        LaunchedEffect(Unit) {
            supabase.auth.awaitInitialization()
            // After rotation the sign-in screens stay; the rest reload their Care Circle from Home.
            if (screen in listOf(Screen.Onb0, Screen.Phone, Screen.Code, Screen.Onb1)) return@LaunchedEffect
            if (supabase.auth.currentSessionOrNull() == null) return@LaunchedEffect reset(Screen.Onb0)
            // Offline, Home opens with what the last load kept and refreshes in place once reachable,
            // so whoever moved on meanwhile stays where they are unless the Care Circle is gone.
            val k = kept.read()?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<Snapshot>(it) }.getOrNull() }
                ?: return@LaunchedEffect land(Nav.Tab)
            restore(k)
            reset(Screen.Home)
            loadHome()
            if (circle == null) land(Nav.Tab)
        }

        val slide = with(LocalDensity.current) { 36.dp.roundToPx() }
        val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
        Box(Modifier.fillMaxSize().background(Kf.Paper)) {
            AnimatedContent(
                screen to tab,
                transitionSpec = {
                    val spec = tween<IntOffset>(300, easing = KfEase)
                    val move = when (nav) {
                        Nav.Push -> slideInHorizontally(spec) { slide }
                        Nav.Back -> slideInHorizontally(spec) { -slide }
                        Nav.Tab -> slideInVertically(spec) { rise }
                    }
                    (fadeIn(tween(300, easing = KfEase)) + move) togetherWith ExitTransition.None
                },
            ) { (s, t) ->
                // design: scroll container padding 60px top (under iOS status bar), 96px bottom when tab bar shows, else 30px
                Column(
                    Modifier.fillMaxSize().background(when (s) { Screen.Emergency -> Kf.Night; Screen.Digest -> Color.White; else -> Kf.Paper }).verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
                        .padding(bottom = if (s == Screen.Home) 96.dp else 30.dp),
                ) {
                    when (s) {
                        null -> {}
                        Screen.Onb0 -> Onb0(
                            onCreate = { go(Screen.Phone) },
                            // Signing in finds the Invitation to this number (see land).
                            onInvited = { go(Screen.Phone) },
                            onSignIn = { go(Screen.Phone) },
                        )
                        Screen.Phone -> PhoneScreen(phone, { phone = it }, onBack = ::back) {
                            sendCode(sms = false).also { if (it && screen == Screen.Phone) go(Screen.Code) }
                        }
                        Screen.Code -> CodeScreen(
                            phone, onBack = ::back, send = { sendCode(it) },
                            verify = { code ->
                                try {
                                    supabase.verifySignInCode(e164(phone), code)
                                    land(Nav.Push)
                                    true
                                } catch (e: RestException) {
                                    false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null // couldn't reach the server
                                }
                            },
                        )
                        Screen.Onb1 -> Onb1 { myName, name, relation, needs ->
                            // Not retried: creating is not idempotent.
                            (attempt { supabase.createCareCircle(name, relation, needs, myName) } != null).also {
                                if (it) { loadHome(); onboarding = true; go(Screen.Onb2) }
                            }
                        }
                        Screen.Onb2 -> Onb2(
                            sent.orEmpty().filter { it.pending },
                            cancel = { inv -> attempt { supabase.cancelInvitation(inv.id) } != null },
                            send = { name, phone -> attempt { supabase.invite(circle!!.id, name, phone) } != null },
                            onDone = { scope.launch { loadHome(); if (onboarding) go(Screen.Onb3) else reset(Screen.Home) } },
                        )
                        Screen.Onb3 -> Onb3(recipient?.name.orEmpty()) { perPerson ->
                            attempt {
                                supabase.hideByDefault(circle!!.id, if (perPerson) setOf(DataCategory.visit_notes) else emptySet())
                                onboarding = false
                                reset(Screen.Home)
                            } != null
                        }
                        Screen.Invitee -> invitation?.let { inv ->
                            Invitee(inv) { take ->
                                attempt { supabase.acceptInvitation(inv.id) }?.let {
                                    // Joined already, so a turn that can't be taken now must not keep them out.
                                    // ponytail: dropped silently offline; they can still swap on the rota.
                                    if (take) inv.dutyId?.let { d -> attempt { supabase.takeTurn(d, inv.dutyDay!!) } }
                                    land(Nav.Tab)
                                } != null
                            }
                        }
                        Screen.Home -> when (t) {
                            // ponytail: header, Appointment, Medications and Terbaru are real; the rest is prototype sample data until its tickets land.
                            Tab.Home -> {
                                val morningMeds = meds.current().morning()
                                val card = homeCard(
                                    now.toLocalDateTime(tz), next?.startsAt?.toLocalDateTime(tz)?.date, note != null,
                                    holdsTonight = tonight != null,
                                    morningDue = morningMeds.any { it.id !in given } || (morningMeds.isNotEmpty() && markedMorning == today),
                                )
                                val morningMarked = stringResource(Res.string.morning_marked, members.firstOrNull { it.userId == me() }?.name.orEmpty())
                                HomeScreen(
                                    s = SampleData.home.copy(
                                        todayLabel = longDate(now.toLocalDateTime(tz).date),
                                        circleName = circle?.name.orEmpty(), memberCount = circle?.memberCount ?: 0,
                                        next = next?.let { a ->
                                            NextAppointment(
                                                whenLabel(a.startsAt, now, tz), countdown(a.startsAt, now, tz), a.title,
                                                a.withWhom(),
                                                person(a.driverId), a.departsAt?.let { hm(it.toLocalDateTime(tz).time) },
                                                questionCount = questions.size, noteReady = note != null,
                                            )
                                        },
                                        invites = sent?.let { all -> all.count { !it.pending } to all.size },
                                        medsGiven = progress.given, medsToday = progress.total,
                                        nextMed = progress.next
                                            ?.let { m -> listOf(m.name, m.schedule).filter { it.isNotBlank() }.joinToString(" · ") }
                                            ?: stringResource(Res.string.no_meds),
                                        week = turns.filter { it.dutyId == turns.first().dutyId }.mapNotNull { t ->
                                            rotaPeople()[t.holder]?.let { p -> DutyDay(shortDay(t.day), t.day.day, p, t.day == today) }
                                        },
                                        dutyLegend = turns.firstOrNull { it.day == today }?.let { t ->
                                            if (t.holder == me()) stringResource(Res.string.legend_you, t.name, hm(t.timeOfDay))
                                            else stringResource(Res.string.legend_other, t.name, hm(t.timeOfDay), rotaPeople()[t.holder]?.name.orEmpty())
                                        }.orEmpty(),
                                        feed = timeline.take(3).map { FeedItem(author(it), text(it, tz), ago(it.at, now, tz)) },
                                        tasksTitle = stringResource(Res.string.tasks_row, tasks.count { !it.done }),
                                        // Approved in #26: "2 terlambat · Perpanjang izin parkir disabilitas (Budi)".
                                        tasksSub = tasks.overdue(today).let { late ->
                                            late.firstOrNull()?.let { t -> stringResource(Res.string.tasks_row_late, late.size, t.text) }
                                                ?: stringResource(Res.string.tasks_row_ok)
                                        },
                                        tasksSubOwner = tasks.overdue(today).firstOrNull()
                                            ?.let { t -> stringResource(Res.string.tasks_row_owner, person(t.ownerId)?.name.orEmpty()) }.orEmpty(),
                                        tasksLate = tasks.overdue(today).isNotEmpty(),
                                        inboxCount = inboxItems().size,
                                        digestSub = digest?.let { stringResource(Res.string.digest_sub, circleMembers().count { !it.isRecipient }) },
                                        evening = if (card != HomeCard.Evening) null else Evening(
                                            hm(tonight!!.timeOfDay), recipient?.name.orEmpty(), checkIn?.takeIf { it.day == today }?.let { it.sys to it.dia },
                                        ),
                                        morning = if (card != HomeCard.Morning) null else Morning(
                                            hm(morningMeds.first().timeOfDay), recipient?.name.orEmpty(),
                                            morningMeds.map { MorningDose("${it.name} ${it.dose}".trim(), it.id in given) },
                                            // Approved in #22: "Kontrol neurologi, Besok · 09.00 · Budi menjemput 08.15".
                                            next?.let { a ->
                                                val pickup = person(a.driverId)?.let { p ->
                                                    a.departsAt?.let { stringResource(Res.string.picks_up, p.name, hm(it.toLocalDateTime(tz).time)) }
                                                        ?: stringResource(Res.string.picks_up_no_time, p.name)
                                                }
                                                listOfNotNull(stringResource(Res.string.next_visit, a.title, whenLabel(a.startsAt, now, tz)), pickup).joinToString(" · ")
                                            },
                                        ),
                                    ),
                                    onSos = { openEmergency() }, onOpenAppointment = { opened = null; go(Screen.Appt) }, onAddAppointment = { openForm(null) },
                                    // Only the Attendee writes the Visit Note; the others add Questions until it's ready.
                                    onWriteNote = { opened = null; go(if (note != null || next?.attendeeId == me()) Screen.VisitNote else Screen.Appt) },
                                    onRota = { pick(Tab.Rota) }, onRecords = { pick(Tab.Records) },
                                    onTimeline = { pick(Tab.Timeline) },
                                    onInvite = { onboarding = false; go(Screen.Onb2) },
                                    onFillEmergency = { go(Screen.EmergencyForm) },
                                    onMarkMorning = {
                                        markDoses(morningMeds.filter { it.id !in given }, give = true)
                                        markedMorning = today
                                        toast = morningMarked
                                    },
                                    // Approved in #24: the Care Recipient's own number while they are a Member, else an empty dialer.
                                    onCall = {
                                        scope.launch {
                                            val phone = recipient?.let { r -> attempt { supabase.recipientPhone(r.id).orEmpty() } }
                                            runCatching { uri.openUri("tel:${phone.orEmpty()}") }
                                        }
                                    },
                                    onCheckIn = { checkingIn = "$today ${tonight!!.timeOfDay}"; go(Screen.CheckIn) },
                                    onTasks = ::openTasks,
                                    onInbox = { go(Screen.Inbox); scope.launch { loadRota(); attempt { supabase.taskList(circle!!.id) }?.let { tasks = it } } },
                                    onSearch = { query = ""; hits = null; go(Screen.Search) },
                                    onDigest = { go(Screen.Digest) },
                                )
                            }
                            Tab.Rota -> {
                                val taken = turns.map { stringResource(Res.string.swap_taken, it.inSentence(), dayName(it.day)) }
                                val declined = turns.map { stringResource(Res.string.swap_declined, rotaPeople()[it.holder]?.name.orEmpty()) }
                                val offline = stringResource(Res.string.no_connection)
                                RotaScreen(
                                    week, today, tz, turns, drives,
                                    // Anyone already holding a day stays visible, even if added before parents were left out.
                                    rotaPeople().filterKeys { id -> id in dutyPeople() || turns.any { it.holder == id } }, me().orEmpty(), admin = sent != null,
                                    onSwap = { swapping = it },
                                    onAnswer = { t, yes ->
                                        val i = turns.indexOf(t)
                                        // Not retried: offline, the answer waits for another tap.
                                        scope.launch {
                                            toast = if (attempt { supabase.answerSwap(t.swapId!!, yes); loadRota() } != null) (if (yes) taken else declined)[i] else offline
                                        }
                                    },
                                    onAdd = { editingDuty = null; go(Screen.DutyForm) },
                                    onEdit = { editingDuty = it; go(Screen.DutyForm) },
                                    rotaMonth, { rotaMonth = it }, monthLoads, away, onAway = { awayOpen = true }.takeIf { me() in dutyPeople() },
                                )
                            }
                            Tab.Timeline -> TimelineScreen(
                                timeline.map { e -> TimelineRow(author(e), type(e.kind), ago(e.at, now, tz), text(e, tz)) { openFromTimeline(e) } },
                                timelineFilter, { timelineFilter = it }, ::openNotes,
                            )
                            Tab.Records -> {
                                val marked = meds.associate { m ->
                                    m.id to stringResource(Res.string.dose_marked, m.name, members.firstOrNull { it.userId == me() }?.name.orEmpty())
                                }
                                val missing = stringResource(Res.string.expense_missing)
                                val c = circle
                                val r = recipient
                                // Gone while Tagihan & uang is hidden from me (#28).
                                val costs = if (c == null || r == null || circleMembers().firstOrNull { it.isMe }?.sees?.contains(DataCategory.money) != true) null
                                else Costs(expenses, dutyPeople(), me().orEmpty(), { named(it) }) { what, amount, by ->
                                    if (what.isBlank() || amount == null) { toast = missing; return@Costs false }
                                    // Not retried: adding is not idempotent.
                                    (attempt { supabase.addExpense(c.id, r.id, what, amount, by, today) } != null).also { ok ->
                                        if (ok) expenses = retrying { supabase.expenses(c.id) } else toast = noConnection
                                    }
                                }
                                val mine = circleMembers().firstOrNull { it.isMe }?.sees.orEmpty()
                                // Gone while both Dokumen and Keinginan & hukum are hidden from me (#29).
                                val docs = if (c == null || r == null || DataCategory.documents !in mine && DataCategory.wishes !in mine) null
                                else Docs(
                                    documents.latest(), { seenBy(circleMembers(), it) }, { named(it) }, { it.toLocalDateTime(tz).date },
                                    open = { d -> scope.launch { attempt { supabase.documentFile(d) }?.let { viewFile(d.name, d.ext, it) } ?: run { toast = noConnection } } },
                                    upload = pickFile, export = ::openExport,
                                )
                                // Approved in #33: a 1-page trends export for the next Appointment's Provider.
                                val attached = next?.let { a -> stringResource(Res.string.trends_attached, inSentence(a.title), a.provider.name) }
                                val bring = next?.takeIf { checkIns.isNotEmpty() && r != null }?.let { a ->
                                    a.provider.name to {
                                        scope.launch { exportPdf(a.provider.name, exportLine(a.provider.name), setOf(ExportSection.trends), emptyList(), attached!!) }
                                        Unit
                                    }
                                }
                                RecordsScreen(
                                    meds, given, checkIns, docs, costs, today, recTab, { recTab = it }, { id -> rotaPeople()[id]?.name }, bring,
                                    onToggle = { m ->
                                        val on = m.id in given
                                        markDoses(listOf(m), !on)
                                        if (!on) toast = marked[m.id]
                                    },
                                ) { editingMed = it; go(Screen.MedForm) }
                            }
                            Tab.Circle -> CircleScreen(
                                circle?.name.orEmpty(), recipient?.name.orEmpty(), circleMembers(), onSos = { openEmergency() },
                                onMember = { viewing = it.id; memberError = null; go(Screen.Member) },
                                onContacts = { scope.launch { loadContacts(); go(Screen.Contacts) } }, onNotes = ::openNotes, onExport = ::openExport,
                                onReplay = { onboarding = true; tab = Tab.Home; reset(Screen.Onb2, Nav.Push) }.takeIf { circleMembers().any { it.isMe && it.role == Role.admin } },
                                onBapak = {
                                    bapakMsg = null
                                    organizerPhone = ""
                                    go(Screen.Bapak)
                                    scope.launch { recipient?.let { r -> attempt { supabase.organizerPhone(r.id).orEmpty() } }?.let { organizerPhone = it } }
                                },
                                onDigest = { go(Screen.Digest) }.takeIf { digest != null },
                            )
                        }
                        Screen.Appt -> visit()?.let { v ->
                            val a = v.appointment
                            ApptScreen(
                                a, v.questions, v.note != null, now, tz, ::person, ::colorOf, a.attendeeId == me(), recipient?.name.orEmpty(),
                                onBack = ::back, onEdit = { openForm(a) },
                                // Not retried: adding is not idempotent.
                                ask = { text -> attempt { supabase.askQuestion(a.circleId, a.id, text); loadHome() } != null },
                                onNote = { go(Screen.VisitNote) },
                            )
                        }
                        Screen.VisitNote -> visit()?.let { v ->
                            val a = v.appointment
                            VisitNoteScreen(
                                a, v.questions, v.note, editable = a.attendeeId == me(), recipient?.name.orEmpty(), now, tz, ::colorOf,
                                owners = dutyPeople().keys.toList(), person = { id -> rotaPeople()[id] ?: person(id) }, me = me().orEmpty(),
                                onHome = { pick(Tab.Home) }, // v3 `goHome`
                                save = { answers, steps, notes ->
                                    attempt { supabase.saveVisitNote(a.id, answers, steps, notes); loadHome() }
                                        .also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                                },
                            )
                        }
                        Screen.ApptForm -> ApptFormScreen(
                            editing, providers, supabase.auth.currentUserOrNull()?.id.orEmpty(), tz,
                            onBack = ::back,
                            // ponytail: not retried (not idempotent); a failed save after adding a new Provider leaves that Provider behind.
                            save = { f ->
                                attempt {
                                    val c = circle!!
                                    val draft = AppointmentDraft(
                                        c.id, editing?.recipientId ?: recipient!!.id, f.providerId ?: supabase.addProvider(c.id, f.newProvider).id,
                                        f.title, f.location, f.startsAt, f.departsAt, f.driverId, f.attendeeId, f.bring,
                                    )
                                    editing?.let { supabase.editAppointment(it.id, draft) } ?: supabase.scheduleAppointment(draft)
                                    loadHome()
                                }.also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                            },
                            cancel = {
                                attempt { editing?.let { supabase.cancelAppointment(it.id) }; loadHome() }
                                    .also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                            },
                        )
                        Screen.MedForm -> MedFormScreen(editingMed, rotaPeople(), onBack = ::back) { f ->
                            // Not retried: adding is not idempotent.
                            attempt {
                                val c = circle!!
                                val draft = MedicationDraft(
                                    c.id, editingMed?.recipientId ?: recipient!!.id, f.name, f.dose, f.schedule, f.timeOfDay, f.active,
                                    f.note, f.bloodThinner, f.refillOn, f.refillBy,
                                )
                                editingMed?.let { supabase.editMedication(it.id, draft) } ?: supabase.addMedication(draft)
                                loadHome()
                            }.also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                        }
                        Screen.DutyForm -> DutyFormScreen(
                            editingDuty, dutyPeople(), onBack = ::back,
                            // Not retried: adding is not idempotent.
                            save = { f ->
                                attempt { supabase.saveDuty(circle!!.id, f.name, f.timeOfDay, f.order, today, editingDuty?.dutyId); loadRota() }
                                    .also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                            },
                            delete = {
                                attempt { editingDuty?.let { supabase.deleteDuty(it.dutyId) }; loadRota() }.also { if (it != null) reset(Screen.Home, Nav.Back) } != null
                            },
                        )
                        Screen.CheckIn -> recipient?.let { r ->
                            val (day, at) = checkingIn?.split(' ')?.let { LocalDate.parse(it[0]) to LocalTime.parse(it[1]) } ?: return@let
                            val noBp = stringResource(Res.string.ci_no_bp)
                            val saved = stringResource(Res.string.ci_saved)
                            // v3: "Tersimpan. Tensi 140 ke atas; Budi dan Dewi diberi tahu via SMS.", WhatsApp to every other Member but the Care Recipient.
                            val others = circleMembers().filter { !it.isMe && !it.isRecipient }.map { it.name }
                            val savedHigh = stringResource(Res.string.ci_saved_high, names(others))
                            val before = checkIn?.takeIf { it.day == day }
                            CheckInScreen(hm(at), r.name, before?.draft, onBack = ::back) { d ->
                                if (d == null) { toast = noBp; return@CheckInScreen }
                                // Not retried: offline, the form stays filled for another tap.
                                if (attempt { supabase.saveCheckIn(r, day, d) } == null) { toast = noConnection; return@CheckInScreen }
                                // The server tells the others once per Check-in, when it first reaches 140.
                                toast = if (d.high && before?.alerted != true && others.isNotEmpty()) savedHigh else saved
                                reset(Screen.Home, Nav.Back)
                                scope.launch {
                                    checkIn = retrying { supabase.checkIn(r.id, day) }
                                    checkIns = retrying { supabase.recentCheckIns(r.id) }
                                    timeline = retrying { supabase.timeline(r.circleId) }
                                }
                            }
                        }
                        Screen.Tasks -> {
                            val doneToast = stringResource(Res.string.task_done_toast)
                            val remindedToast = tasks.associate { t -> t.id to stringResource(Res.string.reminded_toast, rotaPeople()[t.ownerId]?.name.orEmpty()) }
                            // Shown at once like the prototype; put back with "Tidak tersambung. Coba lagi." if it can't be saved.
                            fun swap(t: Task, to: Task) { tasks = tasks.map { if (it.id == t.id) to else it } }
                            fun setDone(t: Task, done: Boolean) {
                                swap(t, t.copy(done = done, doneAt = if (done) Clock.System.now() else null))
                                scope.launch {
                                    taskWrites.withLock {
                                        if (attempt { supabase.markTaskDone(t.id, done) } == null) { swap(t, t); toast = noConnection }
                                        else retrying { tasks = supabase.taskList(t.circleId) }
                                    }
                                }
                            }
                            TasksScreen(
                                tasks, today, me().orEmpty(), { id -> rotaPeople()[id] ?: person(id) },
                                // Former Members get no reminder (remind_task sends them nothing).
                                canRemind = { t -> members.any { it.userId == t.ownerId && it.leftAt == null } }, onBack = ::back,
                                onToggle = { t ->
                                    setDone(t, !t.done)
                                    if (!t.done) { undo = doneToast to { setDone(t.copy(done = true), false) }; toast = doneToast }
                                },
                                onRemind = { t ->
                                    swap(t, t.copy(remindedAt = Clock.System.now()))
                                    toast = remindedToast[t.id]
                                    scope.launch { if (attempt { supabase.remindTask(t.id) } == null) { swap(t, t); toast = noConnection } }
                                },
                            ) { editingTask = it; go(Screen.TaskForm) }
                        }
                        Screen.Inbox -> {
                            val taken = swapsToMe.associate { a ->
                                a.swapId to stringResource(Res.string.inbox_taken, a.name.replaceFirstChar { it.lowercase() }, dayName(a.day), person(a.from)?.name.orEmpty())
                            }
                            val declined = swapsToMe.associate { a -> a.swapId to stringResource(Res.string.swap_declined, person(a.from)?.name.orEmpty()) }
                            val askedFor = next?.provider?.name.orEmpty()
                            val asked = questions.associate { q -> q.id to stringResource(Res.string.inbox_asked, (person(q.askedBy)?.name ?: q.askedByName).orEmpty(), askedFor) }
                            InboxScreen(
                                inboxItems().map { item ->
                                    when (item) {
                                        is InboxItem.Swap -> {
                                            val a = item.ask
                                            // Not retried: offline, the answer waits for another tap.
                                            fun answer(yes: Boolean) = scope.launch {
                                                toast = if (attempt { supabase.answerSwap(a.swapId, yes); loadRota() } != null) (if (yes) taken else declined)[a.swapId] else noConnection
                                            }
                                            val who = person(a.from) ?: Person("", Kf.Muted)
                                            InboxRow(who, swapTitle(who.name, a), swapSub(a, now), onOpen = {}, onAccept = { answer(true) }, onDecline = { answer(false) })
                                        }
                                        is InboxItem.Asked -> {
                                            val q = item.question
                                            InboxRow(person(q.askedBy) ?: named(q.askedBy, q.askedByName), asked[q.id].orEmpty(), "\"${q.text}\"", onOpen = { opened = null; go(Screen.Appt) })
                                        }
                                        is InboxItem.Late -> {
                                            val t = item.task
                                            val owner = person(t.ownerId)?.name.orEmpty()
                                            val canRemind = members.any { it.userId == t.ownerId && it.leftAt == null }
                                            InboxRow(rotaPeople()[t.ownerId] ?: named(t.ownerId), lateTitle(t, today), lateSub(t, me().orEmpty(), owner, canRemind), onOpen = ::openTasks)
                                        }
                                    }
                                },
                                onBack = ::back,
                            )
                        }
                        Screen.Search -> {
                            LaunchedEffect(query) {
                                hits = null // never an earlier query's rows
                                val q = query.trim()
                                if (q.length < 2) return@LaunchedEffect
                                delay(200) // typing on cancels this
                                // Not retried: the next keystroke asks again.
                                attempt { supabase.search(circle!!.id, q) }?.let { hits = it } ?: run { toast = noConnection }
                            }
                            val bloodThinner = stringResource(Res.string.blood_thinner)
                            val records = stringResource(Res.string.tab_records)
                            // ponytail: hits are drawn from what Home last read; one changed since then is left out.
                            val rows = hits?.mapNotNull { h ->
                                when (h.kind) {
                                    Hit.Kind.medication -> meds.firstOrNull { it.id == h.id }?.let { m ->
                                        SearchRow(SearchKind.Medicine, "${m.name} ${m.dose}".trim(), medSub(m, bloodThinner)) { pick(Tab.Records); recTab = RecTab.Meds }
                                    }
                                    Hit.Kind.document -> documents.firstOrNull { it.id == h.id }?.let { d ->
                                        SearchRow(SearchKind.Document, d.name, records) { pick(Tab.Records); recTab = RecTab.Docs }
                                    }
                                    Hit.Kind.timeline -> timeline.firstOrNull { it.kind == h.entry && it.appointmentId == h.id && it.at == h.at && it.text == h.text }?.let { e ->
                                        SearchRow(SearchKind.Timeline, text(e, tz), "${author(e).name} · ${ago(e.at, now, tz)}") { pick(Tab.Timeline) }
                                    }
                                    Hit.Kind.contact -> contacts.firstOrNull { it.id == h.id }?.let { c ->
                                        SearchRow(SearchKind.Contact, c.name, c.relationship) { go(Screen.Contacts) }
                                    }
                                    Hit.Kind.task -> tasks.firstOrNull { it.id == h.id }?.let { t ->
                                        SearchRow(SearchKind.Task, t.text, "${named(t.ownerId).name} · ${dueLabel(t.due, today)}", ::openTasks)
                                    }
                                }
                            }
                            val others = circleMembers().filterNot { it.isMe }.map { it.name }
                            SearchScreen(query, { query = it }, suggestions(meds, documents.latest(), others, contacts), rows, onCancel = ::back)
                        }
                        Screen.TaskForm -> TaskFormScreen(editingTask, dutyPeople(), me().orEmpty(), today + DatePeriod(days = 7), onBack = ::back) { f ->
                            // Not retried: adding is not idempotent.
                            attempt {
                                editingTask?.let { supabase.editTask(it.id, f.text, f.owner, f.due) } ?: supabase.addTask(recipient!!, f.text, f.owner, f.due)
                                // Before leaving, so the form stays busy and can't add twice; also the Visit Note a Next Step's edit changed.
                                loadHome()
                            }.also { if (it != null) back() } != null
                        }
                        Screen.Notes -> {
                            val savedPrivate = stringResource(Res.string.note_saved_private)
                            val savedShared = stringResource(Res.string.note_saved_shared)
                            NotesScreen(notes, recipient?.name.orEmpty(), now, tz, { named(it) }, onBack = ::back) { text, private ->
                                val c = circle ?: return@NotesScreen false
                                (attempt { supabase.addNote(c.id, text, private) } != null).also { ok ->
                                    toast = if (!ok) noConnection else if (private) savedPrivate else savedShared
                                    if (ok) notes = retrying { supabase.noteList(c.id) }
                                }
                            }
                        }
                        Screen.Export -> recipient?.let { r ->
                            val none = stringResource(Res.string.export_none)
                            val done = stringResource(Res.string.export_done)
                            val content = ExportContent(
                                r.conditions, r.allergies, contacts.count { it.emergency }, meds.size, checkIns.size,
                                timeline.count { it.kind == TimelineEntry.Kind.visit_note }, emptyList(),
                            )
                            ExportScreen(r.name, content, documents.latest().filterNot { it.legal }, today, exportDocs, { pickingDocs = true }, onBack = ::back) { preparedFor, line, sections, ids ->
                                if (sections.isEmpty()) toast = none else exportPdf(preparedFor, line, sections, ids, done)
                            }
                        }
                        Screen.DocForm -> uploading?.let { f ->
                            val mine = circleMembers().firstOrNull { it.isMe }?.sees.orEmpty()
                            DocFormScreen(
                                f.name, DataCategory.documents in mine && DataCategory.wishes in mine, DataCategory.documents !in mine, onBack = ::back,
                            ) { form ->
                                val c = circle ?: return@DocFormScreen false
                                val r = recipient ?: return@DocFormScreen false
                                // Not retried: adding is not idempotent. Reloads apart, so a failed one can't invite a second upload.
                                (attempt { supabase.uploadDocument(c.id, r.id, form.name, f.ext, f.bytes, form.legal, f.pages) } != null).also { ok ->
                                    if (ok) { back(); documents = retrying { supabase.documents(c.id) }; timeline = retrying { supabase.timeline(c.id) } }
                                }
                            }
                        } ?: LaunchedEffect(Unit) { back() } // the picked file doesn't survive recreation
                        Screen.Contacts -> ContactsScreen(contacts, onBack = ::back) { editingContact = it; go(Screen.ContactForm) }
                        Screen.ContactForm -> ContactFormScreen(
                            editingContact, onBack = ::back,
                            save = { f ->
                                attempt {
                                    val draft = CareContactDraft(circle!!.id, f.name, f.relationship, f.phone, f.group, f.emergency, f.note)
                                    editingContact?.let { supabase.editCareContact(it.id, draft) } ?: supabase.addCareContact(draft)
                                    loadHome() // before leaving, so the form stays busy and can't add twice
                                }.also { if (it != null) back() } != null
                            },
                            remove = {
                                attempt { editingContact?.let { supabase.removeCareContact(it.id) }; loadHome() }.also { if (it != null) back() } != null
                            },
                        )
                        Screen.Member -> circleMembers().firstOrNull { it.id == viewing }?.let { m ->
                            val c = circle ?: return@let // gone while leaving, before land() moves on
                            val offline = stringResource(Res.string.no_connection)
                            val lastAdmin = stringResource(Res.string.last_admin)
                            val nowAdmin = stringResource(Res.string.now_admin, m.name)
                            val remove = Triple(stringResource(Res.string.remove_title, m.name), stringResource(Res.string.remove_body, m.name), stringResource(Res.string.remove_confirm))
                            val leave = Triple(stringResource(Res.string.leave_title), stringResource(Res.string.leave_body), stringResource(Res.string.leave_confirm))
                            val category = DataCategory.entries.associateWith { stringResource(it.label).lowercase() }
                            val hideTitle = category.mapValues { (_, name) -> stringResource(Res.string.hide_title, name, m.name) }
                            val nowHidden = category.mapValues { (_, name) -> stringResource(Res.string.now_hidden, m.name, name) }
                            val nowShown = category.mapValues { (_, name) -> stringResource(Res.string.now_shown, m.name, name) }
                            val hideBody = stringResource(Res.string.hide_body)
                            val hideConfirm = stringResource(Res.string.hide_confirm)
                            val adminsFull = stringResource(Res.string.admins_full)
                            val onlyRecipient = stringResource(Res.string.only_recipient, recipient?.name.orEmpty())
                            // Not retried: offline, the switch stays put and says so.
                            fun setHidden(cat: DataCategory, hide: Boolean, then: () -> Unit) = scope.launch {
                                val r = recipient ?: return@launch
                                if (attempt { supabase.setHidden(r.id, m.id, cat, hide) } == null) { toast = offline; return@launch }
                                then()
                                hidden = retrying { supabase.hidden(c.id) }
                                changes = retrying { supabase.accessChanges(c.id) }
                                timeline = retrying { supabase.timeline(c.id) }
                            }
                            fun change(cat: DataCategory, hide: Boolean) = setHidden(cat, hide) {
                                val msg = (if (hide) nowHidden else nowShown).getValue(cat)
                                undo = msg to { setHidden(cat, !hide) {} }
                                toast = msg
                            }
                            // Hiding asks first (prototype confirm sheet); sharing happens at once.
                            val toggle: (DataCategory, Boolean) -> Unit = { cat, on ->
                                if (on) confirm = Confirm(hideTitle.getValue(cat), hideBody, hideConfirm) { change(cat, true) } else change(cat, false)
                            }
                            val onToggle: ((DataCategory, Boolean) -> Unit)? = when {
                                m.isRecipient || recipient == null -> null
                                owner() == me() -> toggle
                                sent == null -> null // neither admin nor the Care Recipient
                                owner() != null -> { _, _ -> toast = onlyRecipient }
                                m.role == Role.admin -> { _, _ -> toast = adminsFull }
                                else -> toggle
                            }
                            MemberScreen(
                                m, iAmAdmin = sent != null, memberError, onBack = ::back,
                                onToggle = onToggle,
                                onEmergency = if (sent == null || m.isRecipient) null else { on, distance ->
                                    scope.launch {
                                        if (attempt { supabase.setEmergencyContact(c.id, m.id, on, distance) } == null) toast = offline
                                        members = retrying { supabase.members(c.id) }
                                    }
                                },
                                history = changes.filter { it.recipientId == recipient?.id && it.memberId == m.id }.map {
                                    val by = members.firstOrNull { x -> x.userId == it.by }?.name.orEmpty()
                                    HistoryLine(it.category, it.hidden, changeMeta(it.at, now, tz, by, recipient?.name.takeIf { _ -> it.onBehalfOf != null }))
                                },
                                onPromote = {
                                    memberError = null
                                    scope.launch {
                                        if (attempt { supabase.promoteToAdmin(c.id, m.id); loadHome() } != null) toast = nowAdmin else memberError = offline
                                    }
                                },
                                onRemove = {
                                    memberError = null
                                    confirm = Confirm(remove.first, remove.second, remove.third) {
                                        scope.launch {
                                            if (attempt { supabase.removeMember(c.id, m.id); loadHome() } != null) back()
                                            else memberError = offline
                                        }
                                    }
                                },
                                onLeave = {
                                    memberError = null
                                    // The server refuses too; checking first spares the sheet.
                                    if (m.role == Role.admin && members.none { it.leftAt == null && it.role == Role.admin && it.userId != m.id }) memberError = lastAdmin
                                    else confirm = Confirm(leave.first, leave.second, leave.third) {
                                        scope.launch {
                                            try {
                                                supabase.leaveCareCircle(c.id)
                                                tab = Tab.Home
                                                land(Nav.Tab)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                memberError = if (e.isLastAdmin()) lastAdmin else offline
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        Screen.Bapak -> recipient?.let { r ->
                            val kids = circleMembers().filter { !it.isRecipient }
                            // The organizer: the first admin to join (as organizer() on the server).
                            val organizer = members.firstOrNull { it.leftAt == null && it.role == Role.admin && it.userId != r.memberId }?.name.orEmpty()
                            val seesAll = stringResource(Res.string.bp_sees_all)
                            val seesPart = kids.associate { k -> k.id to stringResource(Res.string.bp_sees_part, k.sees.size, DataCategory.entries.size) }
                            val calling = stringResource(Res.string.bp_calling, organizer)
                            val fineSent = stringResource(Res.string.bp_fine_sent) // "Terkirim ke %1$d anak: "%2$s baik-baik saja.""
                            // Read now if the read on opening hasn't landed yet.
                            fun dial() = scope.launch {
                                if (organizerPhone.isEmpty()) attempt { supabase.organizerPhone(r.id).orEmpty() }?.let { organizerPhone = it }
                                runCatching { uri.openUri("tel:$organizerPhone") }
                            }
                            fun press(block: suspend () -> Unit) {
                                if (pressing) return
                                pressing = true
                                scope.launch { try { block() } finally { pressing = false } }
                            }
                            val appt = next?.takeIf { it.startsAt.toLocalDateTime(tz).date == today }
                            BapakScreen(
                                r.name, greeting(now.toLocalDateTime(tz).hour, r.relation, r.name),
                                appt?.let { a ->
                                    todayPlan(a.provider.name, a.startsAt.toLocalDateTime(tz).time, a.driverId?.let { person(it)?.name },
                                        a.departsAt?.toLocalDateTime(tz)?.time)
                                } ?: stringResource(Res.string.bp_no_appt),
                                bapakMsg, stringResource(Res.string.bp_call, organizer),
                                kids.map { k -> Kid(k.id, k.name, k.color, if (k.sees.size == DataCategory.entries.size) seesAll else seesPart.getValue(k.id)) },
                                onExit = ::back,
                                onFine = {
                                    press {
                                        attempt { supabase.sayFine(r.id) }?.let { n ->
                                            bapakMsg = fineSent.replace("%1\$d", "$n").replace("%2\$s", r.name)
                                            timeline = retrying { supabase.timeline(circle!!.id) }
                                        } ?: run { toast = noConnection }
                                    }
                                },
                                // The dialer opens at once; the alert follows when it gets through.
                                onHelp = {
                                    if (!pressing) dial()
                                    press {
                                        attempt { supabase.askHelp(r.id) }?.let { who ->
                                            val near = kids.firstOrNull { it.emergency && it.distance.isNotBlank() }?.let { it.name to it.distance }
                                            bapakMsg = helpLine(who, near)
                                            timeline = retrying { supabase.timeline(circle!!.id) }
                                        } ?: run { toast = noConnection }
                                    }
                                },
                                onCall = { bapakMsg = calling; dial() },
                                onKid = { k -> viewing = k.id; memberError = null; go(Screen.Member) },
                            )
                        }
                        Screen.Digest -> digest?.let { DigestScreen(it, ::back) }
                        Screen.Emergency -> recipient?.let { r ->
                            EmergencyScreen(
                                emergency ?: r.emergencyFallback(), today, card, savedAt?.let { updatedAgo(it, now) },
                                onClose = ::back, onEdit = { go(Screen.EmergencyForm) },
                                // Refreshes "terakhir dipindai" when reachable; the card on screen already works.
                                onQr = { go(Screen.Qr); scope.launch { attempt { supabase.emergencyCard(r.id) }?.let { card = it } } },
                            )
                        }
                        Screen.Qr -> recipient?.let { r ->
                            card?.let { c ->
                                QrScreen(
                                    emergency ?: r.emergencyFallback(), today, c, c.lastScannedAt?.let { whenLabel(it, now, tz) } ?: stringResource(Res.string.never),
                                    admin = sent != null, onBack = ::back,
                                    print = { attempt { supabase.emergencyCardPdf(r.id, c.url) }?.also { printPdf("Info darurat ${r.name}", it) { toast = printed } } != null },
                                    revoke = { attempt { card = supabase.reissueEmergencyCard(r.id); toast = revoked; scope.launch { loadHome() } } != null },
                                )
                            }
                        }
                        Screen.EmergencyForm -> recipient?.let { r ->
                            EmergencyFormScreen(EmergencyDraft(r.bornOn, r.weightKg, r.allergies, r.wishes, r.conditions), onBack = ::back) { d ->
                                attempt {
                                    supabase.saveEmergencyInfo(r.id, d)
                                    recipient = r.copy(bornOn = d.bornOn, weightKg = d.weightKg, allergies = d.allergies, wishes = d.wishes, conditions = d.conditions)
                                    emergency = emergency?.copy(bornOn = d.bornOn, weightKg = d.weightKg, allergies = d.allergies, wishes = d.wishes, conditions = d.conditions)
                                    scope.launch { loadHome() } // keeps the offline copy and Emergency Info current without holding the form
                                }.also { if (it != null) back() } != null
                            }
                        }
                    }
                }
            }
            ConfirmSheet(confirm, stringResource(Res.string.cancel)) { confirm = null }
            if (pickingDocs && screen == Screen.Export) Sheet("docs", { pickingDocs = false }) {
                DocsPicker(documents.latest().filterNot { it.legal }, exportDocs) { exportDocs = it }
            }
            if (offline && screen != null) OfflineBanner(stringResource(Res.string.offline), Modifier.align(Alignment.TopCenter))
            if (screen == Screen.Home) TabBar(tab, ::pick, Modifier.align(Alignment.BottomCenter))
            Toast(
                toast, Modifier.align(Alignment.BottomCenter), overTabs = screen == Screen.Home,
                action = stringResource(Res.string.undo).takeIf { toast != null && undo?.first == toast },
                onAction = { undo?.second?.invoke(); undo = null; toast = null },
            )
            if (awayOpen) {
                val offline = stringResource(Res.string.no_connection)
                val others = dutyPeople().filterKeys { it != me() }
                val asks = handOff(turns, me().orEmpty(), today, others.keys.toList()) { load(it, turns, drives) }
                // v3's toast, "SMS" → "WhatsApp": WhatsApp dikirim ke Budi dan Dewi. Balasan lewat WhatsApp.
                val sent = stringResource(Res.string.away_sent, listing(asks.map { others[it.second]?.name.orEmpty() }.distinct(), stringResource(Res.string.and)))
                Sheet("away", { awayOpen = false }) {
                    AwaySheet(asks, others) { why ->
                        awayOpen = false
                        // Approved in #30: with nothing to hand off, only the banner, no toast.
                        scope.launch { toast = if (attempt { supabase.goAway(circle!!.id, today, why, asks); loadRota() } == null) offline else sent.takeIf { asks.isNotEmpty() } }
                    }
                }
            }
            swapping?.let { t ->
                val offline = stringResource(Res.string.no_connection)
                val others = dutyPeople().filterKeys { it != me() }
                // v3's toast, "SMS" → "WhatsApp": WhatsApp ke Budi: "Bisa gantikan telepon cek malam Tukiman hari Min? Balas YA."
                val sent = others.mapValues { (_, p) -> stringResource(Res.string.swap_sent, p.name, t.inSentence(), circle?.name.orEmpty(), shortDay(t.day)) }
                Sheet(t, { swapping = null }) {
                    SwapSheet(t, others, { load(it, turns, drives) }) { to ->
                        swapping = null
                        scope.launch { toast = if (attempt { supabase.askSwap(t.dutyId, t.day, to); loadRota() } == null) offline else sent[to] }
                    }
                }
            }
        }
    }
}


private suspend fun <T : Any> attempt(block: suspend () -> T): T? =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

/** Prototype offline pill: top 54px, #22261F, 12px 500, padding 7 14, gap 6, 7px amber dot; copy approved in #14. */
@Composable
private fun OfflineBanner(text: String, modifier: Modifier) {
    // ponytail: sits right under the status bar; the prototype's 54px is 6px above its 60px content inset.
    Row(
        modifier.statusBarsPadding()
            .dropShadow(CircleShape, Shadow(12.dp, Color(0x26000000), offset = DpOffset(0.dp, 4.dp)))
            .background(Kf.Ink, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(Color(0xFFC9A77C), CircleShape))
        Text(text, color = Kf.Paper, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TabBar(active: Tab, onPick: (Tab) -> Unit, modifier: Modifier) {
    // ponytail: backdrop-filter blur(14px) skipped; Compose has no backdrop blur and the bar is 94% opaque.
    Column(modifier.fillMaxWidth().background(Color(0xF0FBF8F2))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.TabBorder))
        Row(Modifier.navigationBarsPadding().padding(8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == active
                val fg = if (on) Kf.Green else Kf.Muted
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).scale(if (pressed) .94f else 1f)
                        .clickable(source, indication = null) { onPick(t) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier.width(56.dp).height(30.dp).background(if (on) Kf.GreenTint else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SvgPath(t.icon, 22.dp, fg, fill = if (on && t.fillsWhenActive) Color(0x2E2F5D4A) else null)
                    }
                    Text(stringResource(t.label), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
