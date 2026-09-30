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
import id.kinfolk.data.DutyTurn
import id.kinfolk.data.answerSwap
import id.kinfolk.data.appointmentsBetween
import id.kinfolk.data.askSwap
import id.kinfolk.data.deleteDuty
import id.kinfolk.data.dutyWeek
import id.kinfolk.data.saveDuty
import id.kinfolk.data.takeTurn
import id.kinfolk.data.weekOf
import id.kinfolk.ui.Sheet
import id.kinfolk.ui.appointment.shortDay
import id.kinfolk.ui.home.DutyDay
import id.kinfolk.ui.rota.DutyFormScreen
import id.kinfolk.ui.rota.RotaScreen
import id.kinfolk.ui.rota.SwapSheet
import id.kinfolk.ui.rota.inSentence
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
import id.kinfolk.data.Invitation
import id.kinfolk.data.InvitationToMe
import id.kinfolk.data.Role
import id.kinfolk.data.acceptInvitation
import id.kinfolk.data.cancelInvitation
import id.kinfolk.data.invitations
import id.kinfolk.data.invite
import id.kinfolk.data.myInvitations
import id.kinfolk.data.roleIn
import id.kinfolk.data.Medication
import id.kinfolk.data.MedicationDraft
import id.kinfolk.data.addCareContact
import id.kinfolk.data.addMedication
import id.kinfolk.data.careContacts
import id.kinfolk.data.current
import id.kinfolk.data.editCareContact
import id.kinfolk.data.editMedication
import id.kinfolk.data.medications
import id.kinfolk.data.DataCategory
import id.kinfolk.data.Hidden
import id.kinfolk.data.emergencyMedications
import id.kinfolk.data.hidden
import id.kinfolk.data.hideByDefault
import id.kinfolk.data.setHidden
import id.kinfolk.data.next
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
import id.kinfolk.data.timeline
import id.kinfolk.data.cancelAppointment
import id.kinfolk.data.careRecipients
import id.kinfolk.data.editAppointment
import id.kinfolk.data.nextAppointment
import id.kinfolk.data.providers
import id.kinfolk.data.scheduleAppointment
import id.kinfolk.data.createCareCircle
import id.kinfolk.data.kinfolkClient
import id.kinfolk.data.myCareCircle
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
import id.kinfolk.ui.circle.CircleMember
import id.kinfolk.ui.circle.CircleScreen
import id.kinfolk.ui.circle.MemberScreen
import id.kinfolk.ui.circle.label
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
import id.kinfolk.ui.home.HomeScreen
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
import id.kinfolk.ui.timeline.TimelineRow
import id.kinfolk.ui.timeline.TimelineScreen
import id.kinfolk.ui.timeline.text
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.tab_circle
import kinfolk.shared.generated.resources.tab_home
import kinfolk.shared.generated.resources.tab_records
import kinfolk.shared.generated.resources.tab_rota
import kinfolk.shared.generated.resources.tab_timeline
import kinfolk.shared.generated.resources.no_meds
import kinfolk.shared.generated.resources.never
import kinfolk.shared.generated.resources.qr_revoked
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
import kinfolk.shared.generated.resources.swap_declined
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
import kotlinx.datetime.DatePeriod
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

private enum class Screen { Onb0, Phone, Code, Onb1, Onb2, Onb3, Invitee, Home, Appt, ApptForm, VisitNote, MedForm, Contacts, ContactForm, Emergency, Qr, EmergencyForm, Member, DutyForm }

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
        var phone by rememberSaveable { mutableStateOf("") }
        var circle by remember { mutableStateOf<CareCircle?>(null) }
        var recipient by remember { mutableStateOf<CareRecipient?>(null) }
        var next by remember { mutableStateOf<Appointment?>(null) }
        var questions by remember { mutableStateOf(emptyList<Question>()) } // on next
        var note by remember { mutableStateOf<VisitNote?>(null) } // of next
        var opened by remember { mutableStateOf<Visit?>(null) } // from the Timeline; `appt` and `summary` show next while null
        var timeline by remember { mutableStateOf(emptyList<TimelineEntry>()) }
        var opening by remember { mutableStateOf<Job?>(null) }
        var editing by remember { mutableStateOf<Appointment?>(null) }
        var providers by remember { mutableStateOf(emptyList<Provider>()) }
        var meds by remember { mutableStateOf(emptyList<Medication>()) }
        var editingMed by remember { mutableStateOf<Medication?>(null) }
        var contacts by remember { mutableStateOf(emptyList<CareContact>()) }
        var editingContact by remember { mutableStateOf<CareContact?>(null) }
        var invitation by remember { mutableStateOf<InvitationToMe?>(null) }
        var sent by remember { mutableStateOf<List<Invitation>?>(null) } // null unless admin
        var card by remember { mutableStateOf<EmergencyCard?>(null) }
        var formBack by remember { mutableStateOf(Screen.Emergency) } // where the Emergency Info form returns to
        var emergencyLoad by remember { mutableStateOf<Job?>(null) }
        var toast by remember { mutableStateOf<String?>(null) }
        var undo by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) } // "Urungkan" while its toast shows
        var members by remember { mutableStateOf(emptyList<Member>()) } // Former Members too, for their names
        var hidden by remember { mutableStateOf(emptyList<Hidden>()) }
        var sosMeds by remember { mutableStateOf(emptyList<Medication>()) } // whatever is hidden (ADR 0003)
        var onboarding by rememberSaveable { mutableStateOf(false) } // onb2 goes on to onb3 right after onb1
        var viewing by rememberSaveable { mutableStateOf<String?>(null) } // on `member`
        var memberError by remember { mutableStateOf<String?>(null) }
        var confirm by remember { mutableStateOf<Confirm?>(null) }
        var turns by remember { mutableStateOf(emptyList<DutyTurn>()) } // this week
        var drives by remember { mutableStateOf(emptyList<Appointment>()) } // this week, with a Driver
        var swapping by remember { mutableStateOf<DutyTurn?>(null) }
        var editingDuty by remember { mutableStateOf<DutyTurn?>(null) }
        LaunchedEffect(toast) { if (toast != null) { delay(if (undo?.first == toast) 5000 else 2600); toast = null } }
        val share = rememberShare()
        val revoked = stringResource(Res.string.qr_revoked)
        val tz = remember { TimeZone.currentSystemDefault() }
        val now by produceState(Clock.System.now()) { while (true) { delay(30_000); value = Clock.System.now() } }
        // "Anda" in Budi's avatar color from the prototype, since Sri's green vanishes on the green Home card.
        val you = Person(stringResource(Res.string.you), Color(0xFFB0643A))
        val former = stringResource(Res.string.former_member) // "%1$s · Mantan anggota"
        fun me() = supabase.auth.currentUserOrNull()?.id
        // Approved in #5: yourself in Sri's green, the others in join order in Budi's, Dewi's, Agus's and Rina's, Former Members muted.
        fun colorOf(id: String): Color {
            val m = members.firstOrNull { it.userId == id }
            return when {
                id == me() -> Kf.Green
                m?.leftAt != null -> Kf.Muted
                else -> InviteColors[members.filter { it.userId != me() && it.leftAt == null }.indexOf(m).coerceAtLeast(0) % 4]
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
        fun author(e: TimelineEntry): Person {
            val m = members.firstOrNull { it.userId == e.by }
            val name = m?.name ?: e.byName.orEmpty()
            return Person(if (m?.leftAt != null) former.replace("%1\$s", name) else name, colorOf(e.by))
        }
        fun visit() = opened ?: next?.let { Visit(it, questions, note) }
        // The Care Recipient while they are a Member: they set restrictions, else the admins (ADR 0004).
        fun owner() = recipient?.memberId?.takeIf { id -> members.any { it.userId == id && it.leftAt == null } }
        fun sees(m: Member): Set<DataCategory> =
            if (m.userId == owner() || (owner() == null && m.role == Role.admin)) DataCategory.entries.toSet()
            else DataCategory.entries.toSet() - hidden.filter { it.recipientId == recipient?.id && it.memberId == m.userId }.map { it.category }.toSet()
        fun circleMembers() = members.filter { it.leftAt == null }.sortedWith(compareBy({ it.userId != owner() }, { it.userId != me() })).map {
            CircleMember(it.userId, it.name.orEmpty(), colorOf(it.userId), it.role, it.joinedAt.toLocalDateTime(tz).date, it.userId == me(), sees(it), it.userId == owner())
        }
        fun go(to: Screen, how: Nav = Nav.Push) { nav = how; screen = to }
        val today = now.toLocalDateTime(tz).date
        val week = weekOf(today)
        // The rota and "Minggu ini" show everyone by their own name, yourself first in Sri's green (like `circle`).
        fun rotaPeople() = circleMembers().associate { it.id to Person(it.name, it.color) }
        suspend fun loadRota() {
            val c = circle ?: return
            turns = retrying { supabase.dutyWeek(c.id, week) }
            drives = retrying { supabase.appointmentsBetween(c.id, week.atStartOfDayIn(tz), (week + DatePeriod(days = 7)).atStartOfDayIn(tz)) }
                .filter { it.driverId != null }
        }
        suspend fun loadVisit() {
            questions = next?.let { a -> retrying { supabase.questions(a.id) } }.orEmpty()
            note = next?.let { a -> retrying { supabase.visitNote(a.id) } }
            opened?.appointment?.let { a -> opened = Visit(a, retrying { supabase.questions(a.id) }, retrying { supabase.visitNote(a.id) }) }
        }
        // Not retried: offline, the tap does nothing rather than jumping there later. The latest tap wins.
        fun openFromTimeline(e: TimelineEntry) {
            opening?.cancel()
            opening = scope.launch {
                val v = attempt { listOfNotNull(supabase.appointment(e.appointmentId)).map { a -> Visit(a, supabase.questions(a.id), supabase.visitNote(a.id)) } }
                    ?.firstOrNull() ?: return@launch // unreachable, or cancelled meanwhile
                opened = v
                go(if (e.kind == TimelineEntry.Kind.visit_note) Screen.VisitNote else Screen.Appt)
            }
        }
        suspend fun loadHome() {
            val c = circle ?: return
            // The card stays until the day ends: its "Tulis catatan" button is for after the visit.
            val today = Clock.System.now().toLocalDateTime(tz).date.atStartOfDayIn(tz)
            recipient = retrying { supabase.careRecipients(c.id).firstOrNull() }
            next = retrying { supabase.nextAppointment(c.id, since = today) }
            loadVisit()
            meds = retrying { supabase.medications(c.id) }
            sent = retrying { if (supabase.roleIn(c.id) == Role.admin) supabase.invitations(c.id) else null }
            members = retrying { supabase.members(c.id) }
            hidden = retrying { supabase.hidden(c.id) }
            sosMeds = recipient?.let { r -> retrying { supabase.emergencyMedications(r.id) } }.orEmpty()
            timeline = retrying { supabase.timeline(c.id) }
            loadRota()
        }
        suspend fun land(how: Nav) {
            circle = retrying { supabase.myCareCircle() }
            loadHome()
            // Whoever signs in without a Care Circle but with a pending Invitation to their number sees `invitee`.
            invitation = if (circle == null) retrying { supabase.myInvitations() }.firstOrNull() else null
            when {
                circle != null -> go(Screen.Home, Nav.Tab)
                invitation != null -> go(Screen.Invitee, how)
                else -> go(Screen.Onb1, how)
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
        // SOS opens at once, even offline, with what Home already loaded; contacts and the card follow when reachable.
        fun openEmergency() {
            go(Screen.Emergency)
            emergencyLoad?.cancel()
            emergencyLoad = scope.launch {
                loadContacts()
                card = recipient?.let { r -> retrying { supabase.emergencyCard(r.id) } }
            }
        }
        fun openEmergencyForm(from: Screen) { formBack = from; go(Screen.EmergencyForm) }
        suspend fun sendCode(sms: Boolean): Boolean = attempt {
            supabase.sendSignInCode(e164(phone), sms)
        } != null
        LaunchedEffect(Unit) {
            supabase.auth.awaitInitialization()
            // After rotation the sign-in screens stay; the rest reload their Care Circle from Home.
            if (screen in listOf(Screen.Onb0, Screen.Phone, Screen.Code, Screen.Onb1)) return@LaunchedEffect
            if (supabase.auth.currentSessionOrNull() != null) land(Nav.Tab) else go(Screen.Onb0, Nav.Tab)
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
                    Modifier.fillMaxSize().background(if (s == Screen.Emergency) Kf.Night else Kf.Paper).verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
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
                        Screen.Phone -> PhoneScreen(phone, { phone = it }, onBack = { go(Screen.Onb0, Nav.Back) }) {
                            sendCode(sms = false).also { if (it && screen == Screen.Phone) go(Screen.Code) }
                        }
                        Screen.Code -> CodeScreen(
                            phone, onBack = { go(Screen.Phone, Nav.Back) }, send = { sendCode(it) },
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
                                if (it) { circle = retrying { supabase.myCareCircle() }; loadHome(); onboarding = true; go(Screen.Onb2) }
                            }
                        }
                        Screen.Onb2 -> Onb2(
                            sent.orEmpty().filter { it.pending },
                            cancel = { inv -> attempt { supabase.cancelInvitation(inv.id) } != null },
                            send = { name, phone -> attempt { supabase.invite(circle!!.id, name, phone) } != null },
                            onDone = { scope.launch { loadHome(); if (onboarding) go(Screen.Onb3) else go(Screen.Home, Nav.Tab) } },
                        )
                        Screen.Onb3 -> Onb3(recipient?.name.orEmpty()) { perPerson ->
                            attempt {
                                supabase.hideByDefault(circle!!.id, if (perPerson) setOf(DataCategory.visit_notes) else emptySet())
                                onboarding = false
                                go(Screen.Home, Nav.Tab)
                            } != null
                        }
                        Screen.Invitee -> invitation?.let { inv ->
                            Invitee(inv) { take ->
                                attempt { supabase.acceptInvitation(inv.id) }?.let {
                                    // Joined already, so a turn that can't be taken now must not keep them out.
                                    // ponytail: dropped silently offline; they can still swap on the rota.
                                    if (take) inv.dutyId?.let { d -> attempt { supabase.takeTurn(d, week + DatePeriod(days = 7)) } }
                                    land(Nav.Tab)
                                } != null
                            }
                        }
                        Screen.Home -> when (t) {
                            // ponytail: header, Appointment, Medications and Terbaru are real; the rest is prototype sample data until its tickets land.
                            Tab.Home -> HomeScreen(
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
                                    medsToday = meds.current().size,
                                    nextMed = meds.current().next(now.toLocalDateTime(tz).time)
                                        ?.let { m -> listOf(m.name, m.schedule).filter { it.isNotBlank() }.joinToString(" · ") }
                                        ?: stringResource(Res.string.no_meds),
                                    week = turns.firstOrNull()?.let { t -> rotaPeople()[t.holder] }?.let { p ->
                                        (0..6).map { i -> (week + DatePeriod(days = i)).let { d -> DutyDay(shortDay(d), d.day, p, d == today) } }
                                    }.orEmpty(),
                                    dutyLegend = turns.firstOrNull()?.let { t ->
                                        if (t.holder == me()) stringResource(Res.string.legend_you, t.name, hm(t.timeOfDay))
                                        else stringResource(Res.string.legend_other, t.name, hm(t.timeOfDay), rotaPeople()[t.holder]?.name.orEmpty())
                                    }.orEmpty(),
                                    feed = timeline.take(3).map { FeedItem(author(it), text(it, tz), ago(it.at, now, tz)) },
                                ),
                                onSos = { openEmergency() }, onOpenAppointment = { opened = null; go(Screen.Appt) }, onAddAppointment = { openForm(null) },
                                // Only the Attendee writes the Visit Note; the others add Questions until it's ready.
                                onWriteNote = { opened = null; go(if (note != null || next?.attendeeId == me()) Screen.VisitNote else Screen.Appt) },
                                onRota = { nav = Nav.Tab; tab = Tab.Rota }, onRecords = { nav = Nav.Tab; tab = Tab.Records },
                                onTimeline = { nav = Nav.Tab; tab = Tab.Timeline },
                                onInvite = { go(Screen.Onb2) },
                                onFillEmergency = { openEmergencyForm(Screen.Home) },
                            )
                            Tab.Rota -> {
                                val taken = turns.map { stringResource(Res.string.swap_taken, it.inSentence()) }
                                val declined = turns.map { stringResource(Res.string.swap_declined, rotaPeople()[it.holder]?.name.orEmpty()) }
                                val offline = stringResource(Res.string.no_connection)
                                RotaScreen(
                                    week, today, tz, turns, drives, rotaPeople(), me().orEmpty(), admin = sent != null,
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
                                )
                            }
                            Tab.Timeline -> TimelineScreen(timeline.map { e -> TimelineRow(author(e), ago(e.at, now, tz), text(e, tz)) { openFromTimeline(e) } })
                            Tab.Records -> RecordsScreen(meds) { editingMed = it; go(Screen.MedForm) }
                            Tab.Circle -> CircleScreen(
                                circle?.name.orEmpty(), recipient?.name.orEmpty(), circleMembers(), onSos = { openEmergency() },
                                onMember = { viewing = it.id; memberError = null; go(Screen.Member) },
                            ) { scope.launch { loadContacts(); go(Screen.Contacts) } }
                        }
                        Screen.Appt -> visit()?.let { v ->
                            val a = v.appointment
                            ApptScreen(
                                a, v.questions, v.note != null, now, tz, ::person, ::colorOf, a.attendeeId == me(), recipient?.name.orEmpty(),
                                onBack = { go(Screen.Home, Nav.Back) }, onEdit = { openForm(a) },
                                // Not retried: adding is not idempotent.
                                ask = { text -> attempt { supabase.askQuestion(a.circleId, a.id, text); loadVisit() } != null },
                                onNote = { go(Screen.VisitNote) },
                            )
                        }
                        Screen.VisitNote -> visit()?.let { v ->
                            val a = v.appointment
                            VisitNoteScreen(
                                a, v.questions, v.note, editable = a.attendeeId == me(), recipient?.name.orEmpty(), now, tz, ::colorOf,
                                onHome = { go(Screen.Home, Nav.Back) },
                                save = { answers, steps, notes ->
                                    attempt { supabase.saveVisitNote(a.id, answers, steps, notes); loadHome() }
                                        .also { if (it != null) go(Screen.Home, Nav.Back) } != null
                                },
                            )
                        }
                        Screen.ApptForm -> ApptFormScreen(
                            editing, providers, supabase.auth.currentUserOrNull()?.id.orEmpty(), tz,
                            onBack = { go(if (editing != null) Screen.Appt else Screen.Home, Nav.Back) },
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
                                }.also { if (it != null) go(Screen.Home, Nav.Back) } != null
                            },
                            cancel = {
                                attempt { editing?.let { supabase.cancelAppointment(it.id) }; loadHome() }
                                    .also { if (it != null) go(Screen.Home, Nav.Back) } != null
                            },
                        )
                        Screen.MedForm -> MedFormScreen(editingMed, onBack = { go(Screen.Home, Nav.Back) }) { f ->
                            // Not retried: adding is not idempotent.
                            attempt {
                                val c = circle!!
                                val draft = MedicationDraft(c.id, editingMed?.recipientId ?: recipient!!.id, f.name, f.dose, f.schedule, f.timeOfDay, f.active)
                                editingMed?.let { supabase.editMedication(it.id, draft) } ?: supabase.addMedication(draft)
                                loadHome()
                            }.also { if (it != null) go(Screen.Home, Nav.Back) } != null
                        }
                        Screen.DutyForm -> DutyFormScreen(
                            editingDuty, rotaPeople(), onBack = { go(Screen.Home, Nav.Back) },
                            // Not retried: adding is not idempotent.
                            save = { f ->
                                attempt { supabase.saveDuty(circle!!.id, f.name, f.timeOfDay, f.order, week, editingDuty?.dutyId); loadRota() }
                                    .also { if (it != null) go(Screen.Home, Nav.Back) } != null
                            },
                            delete = {
                                attempt { editingDuty?.let { supabase.deleteDuty(it.dutyId) }; loadRota() }.also { if (it != null) go(Screen.Home, Nav.Back) } != null
                            },
                        )
                        Screen.Contacts -> ContactsScreen(contacts, onBack = { go(Screen.Home, Nav.Back) }) { editingContact = it; go(Screen.ContactForm) }
                        Screen.ContactForm -> ContactFormScreen(
                            editingContact, onBack = { go(Screen.Contacts, Nav.Back) },
                            save = { f ->
                                attempt {
                                    val draft = CareContactDraft(circle!!.id, f.name, f.relationship, f.phone, f.group, f.emergency)
                                    editingContact?.let { supabase.editCareContact(it.id, draft) } ?: supabase.addCareContact(draft)
                                    loadContacts() // before leaving, so the form stays busy and can't add twice
                                }.also { if (it != null) go(Screen.Contacts, Nav.Back) } != null
                            },
                            remove = {
                                attempt { editingContact?.let { supabase.removeCareContact(it.id) }; loadContacts() }.also { if (it != null) go(Screen.Contacts, Nav.Back) } != null
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
                                m, iAmAdmin = sent != null, memberError, onBack = { go(Screen.Home, Nav.Back) },
                                onToggle = onToggle,
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
                                            if (attempt { supabase.removeMember(c.id, m.id); circle = supabase.myCareCircle(); loadHome() } != null) go(Screen.Home, Nav.Back)
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
                        Screen.Emergency -> recipient?.let { r ->
                            EmergencyScreen(
                                r.name, r.allergies, r.conditions, sosMeds, contacts, card,
                                onClose = { go(Screen.Home, Nav.Back) }, onEdit = { openEmergencyForm(Screen.Emergency) },
                                // Refreshes "terakhir dipindai" when reachable; the card on screen already works.
                                onQr = { go(Screen.Qr); scope.launch { attempt { supabase.emergencyCard(r.id) }?.let { card = it } } },
                            )
                        }
                        Screen.Qr -> recipient?.let { r ->
                            card?.let { c ->
                                QrScreen(
                                    r.name, r.allergies, c, c.lastScannedAt?.let { whenLabel(it, now, tz) } ?: stringResource(Res.string.never),
                                    admin = sent != null, onBack = { go(Screen.Emergency, Nav.Back) }, onShare = { share(c.url) },
                                    revoke = { attempt { card = supabase.reissueEmergencyCard(r.id); toast = revoked } != null },
                                )
                            }
                        }
                        Screen.EmergencyForm -> recipient?.let { r ->
                            EmergencyFormScreen(r.allergies, r.conditions, onBack = { go(formBack, Nav.Back) }) { allergies, conditions ->
                                attempt {
                                    supabase.saveEmergencyInfo(r.id, allergies, conditions)
                                    recipient = r.copy(allergies = allergies, conditions = conditions)
                                }.also { if (it != null) go(formBack, Nav.Back) } != null
                            }
                        }
                    }
                }
            }
            ConfirmSheet(confirm, stringResource(Res.string.cancel)) { confirm = null }
            if (screen == Screen.Home) TabBar(tab, { nav = Nav.Tab; tab = it }, Modifier.align(Alignment.BottomCenter))
            Toast(
                toast, Modifier.align(Alignment.BottomCenter), overTabs = screen == Screen.Home,
                action = stringResource(Res.string.undo).takeIf { toast != null && undo?.first == toast },
                onAction = { undo?.second?.invoke(); undo = null; toast = null },
            )
            swapping?.let { t ->
                val offline = stringResource(Res.string.no_connection)
                Sheet(t, { swapping = null }) {
                    SwapSheet(t, rotaPeople().filterKeys { it != me() }, { load(it, turns, drives) }) { to ->
                        swapping = null
                        scope.launch { if (attempt { supabase.askSwap(t.dutyId, week, to); loadRota() } == null) toast = offline }
                    }
                }
            }
        }
    }
}


private suspend fun <T : Any> attempt(block: suspend () -> T): T? =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

// ponytail: reads only; retries forever with no message until the offline ticket designs what to show.
private suspend fun <T> retrying(block: suspend () -> T): T {
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            delay(3000)
        }
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
