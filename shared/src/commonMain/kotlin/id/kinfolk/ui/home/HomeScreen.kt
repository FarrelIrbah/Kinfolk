package id.kinfolk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Kf
import id.kinfolk.ui.SvgPath
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import id.kinfolk.ui.search.Magnifier
import kinfolk.shared.generated.resources.search_ph
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_appt
import kinfolk.shared.generated.resources.add_appt_sub
import kinfolk.shared.generated.resources.add_meds
import kinfolk.shared.generated.resources.add_meds_sub
import kinfolk.shared.generated.resources.invite_siblings
import kinfolk.shared.generated.resources.fill_emergency
import kinfolk.shared.generated.resources.fill_emergency_sub
import kinfolk.shared.generated.resources.joined_of
import kinfolk.shared.generated.resources.drives_no_time
import kinfolk.shared.generated.resources.no_driver
import kinfolk.shared.generated.resources.circle_name
import kinfolk.shared.generated.resources.drives
import kinfolk.shared.generated.resources.latest
import kinfolk.shared.generated.resources.meds_today
import kinfolk.shared.generated.resources.note_ready
import kinfolk.shared.generated.resources.open_summary
import kinfolk.shared.generated.resources.questions_count
import kinfolk.shared.generated.resources.see_all
import kinfolk.shared.generated.resources.see_rota
import kinfolk.shared.generated.resources.sos
import kinfolk.shared.generated.resources.inbox
import kinfolk.shared.generated.resources.this_week
import kinfolk.shared.generated.resources.write_note
import kinfolk.shared.generated.resources.digest_row
import kinfolk.shared.generated.resources.all_given
import kinfolk.shared.generated.resources.dose_due
import kinfolk.shared.generated.resources.given
import kinfolk.shared.generated.resources.mark_all_given
import kinfolk.shared.generated.resources.morning_eyebrow
import kinfolk.shared.generated.resources.morning_title
import kinfolk.shared.generated.resources.call_recipient
import kinfolk.shared.generated.resources.edit
import kinfolk.shared.generated.resources.evening_body
import kinfolk.shared.generated.resources.evening_body_logged
import kinfolk.shared.generated.resources.evening_eyebrow
import kinfolk.shared.generated.resources.evening_title
import kinfolk.shared.generated.resources.evening_title_logged
import kinfolk.shared.generated.resources.log_checkin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

data class Person(val name: String, val color: Color) {
    val initial get() = name.take(1)
}

data class NextAppointment(
    val whenLabel: String,
    val countdown: String,
    val title: String,
    val provider: String,
    val driver: Person?,
    val leavesAt: String?,
    val questionCount: Int,
    val noteReady: Boolean,
)

/** v3's main Home cards, in priority order (#22). */
enum class HomeCard { AfterVisit, VisitToday, Evening, Morning, Next, Empty }

/**
 * Which card Home shows at [now]: after-visit > appointment today without a Visit Note > evening (from 17.00, if I
 * hold tonight's Duty) > morning (before 11.00, while morning doses are due) > next appointment > empty.
 * [visitOn] is the day of the next Appointment, today's included until the day ends.
 */
fun homeCard(now: LocalDateTime, visitOn: LocalDate?, noteReady: Boolean, holdsTonight: Boolean, morningDue: Boolean) = when {
    visitOn == now.date && noteReady -> HomeCard.AfterVisit
    visitOn == now.date -> HomeCard.VisitToday
    now.time >= LocalTime(17, 0) && holdsTonight -> HomeCard.Evening
    now.time < LocalTime(11, 0) && morningDue -> HomeCard.Morning
    visitOn != null -> HomeCard.Next
    else -> HomeCard.Empty
}

data class MorningDose(val name: String, val given: Boolean)

/** v3's morning card: "08.00 · obat pagi", each dose, and the next visit (null hides the line). */
data class Morning(val at: String, val recipient: String, val doses: List<MorningDose>, val nextVisit: String?)

/** v3's evening card: "19.00 · cek malam"; [bp] (sys to dia) once tonight's Check-in is logged. */
data class Evening(val at: String, val recipient: String, val bp: Pair<Int, Int>?)

data class DutyDay(val dow: String, val num: Int, val member: Person, val isToday: Boolean)
data class FeedItem(val by: Person, val text: String, val whenLabel: String)

data class HomeState(
    val todayLabel: String,
    val circleName: String,
    val memberCount: Int,
    val next: NextAppointment?,
    /** Who holds the first Duty each day of this week; empty hides "Minggu ini". */
    val week: List<DutyDay>,
    val dutyLegend: String,
    /** Today's doses given, of [medsToday] (#21). */
    val medsGiven: Int,
    val medsToday: Int,
    val nextMed: String,
    val feed: List<FeedItem>,
    /** Joined and total non-cancelled Invitations for `empty` row 3; null hides the row (admins only). */
    val invites: Pair<Int, Int>? = null,
    /** Shown in place of the Appointment card when [homeCard] picks it. */
    val morning: Morning? = null,
    val evening: Evening? = null,
    /** v3's Tasks row: "3 tugas belum selesai", and [tasksSub] in red while [tasksLate]. */
    val tasksTitle: String = "",
    val tasksSub: String = "",
    /** "(Budi)" after [tasksSub]: stays when the Task's text is cut short. */
    val tasksSubOwner: String = "",
    val tasksLate: Boolean = false,
    /** v3's bell badge; 0 hides it. */
    val inboxCount: Int = 0,
    /** "Dikirim ke 5 anak · lihat seperti yang Anda terima"; null (before my first digest, #40) hides the row. */
    val digestSub: String? = null,
)

private val Cream = Color(0xFFF3EEE4)
private val CreamTint = Color(0x29F3EEE4) // rgba(243,238,228,.16)
private val CreamBtn = Color(0x24F3EEE4) // rgba(243,238,228,.14)
private val CreamLine = Color(0x33F3EEE4) // rgba(243,238,228,.2)

@Composable
fun HomeScreen(
    s: HomeState,
    onSos: () -> Unit,
    onOpenAppointment: () -> Unit,
    onAddAppointment: () -> Unit,
    onWriteNote: () -> Unit,
    onRota: () -> Unit,
    onRecords: () -> Unit,
    onTimeline: () -> Unit,
    onInvite: () -> Unit,
    onFillEmergency: () -> Unit,
    onMarkMorning: () -> Unit,
    onCall: () -> Unit,
    onCheckIn: () -> Unit,
    onTasks: () -> Unit,
    onInbox: () -> Unit,
    onSearch: () -> Unit,
    onDigest: () -> Unit,
) {
    // design: padding:4px 20px; gap:22px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.circle_name, s.circleName, s.memberCount), fontSize = 13.sp, color = Kf.Muted)
                Text(s.todayLabel, style = serifStyle(30f, 1.1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // design: 44px #FBF8F2 circle, 20px bell; badge 18px #9E3B1E at top 3 right 3, 11px white
                val inbox = stringResource(Res.string.inbox)
                Box(Modifier.size(44.dp).background(Kf.Card, CircleShape).tap(onInbox).semantics { contentDescription = inbox }) {
                    SvgPath("M6 16v-5a6 6 0 1 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0", 20.dp, Kf.Ink, Modifier.align(Alignment.Center))
                    if (s.inboxCount > 0) Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).height(18.dp).widthIn(min = 18.dp)
                            .background(Kf.Sos, RoundedCornerShape(9.dp)).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(s.inboxCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    Modifier.height(44.dp).background(Kf.Sos, CircleShape).tap(onSos).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.sos), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em)
                }
            }
        }
        // design: h46, 1px rgba(34,38,31,.1), r14, #FBF8F2, padding 0 14, gap 10; 18px magnifier; 15px muted
        Row(
            Modifier.fillMaxWidth().height(46.dp).background(Kf.Card, RoundedCornerShape(14.dp))
                .border(1.dp, Kf.Border, RoundedCornerShape(14.dp)).tap(onSearch).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            SvgPath(Magnifier, 18.dp, Kf.Muted)
            Text(stringResource(Res.string.search_ph), fontSize = 15.sp, color = Kf.Muted)
        }
        // ponytail: the after-visit card falls back to these until its ticket lands.
        if (s.evening != null) EveningCard(s.evening, onCall, onCheckIn)
        else if (s.morning != null) MorningCard(s.morning, onMarkMorning)
        else s.next?.let { AppointmentCard(it, onOpenAppointment, onWriteNote) } ?: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStep(1, stringResource(Res.string.add_appt), stringResource(Res.string.add_appt_sub), part = false, onAddAppointment)
            EmptyStep(2, stringResource(Res.string.add_meds), stringResource(Res.string.add_meds_sub), part = false, onRecords)
            s.invites?.let { (joined, total) ->
                EmptyStep(3, stringResource(Res.string.invite_siblings), stringResource(Res.string.joined_of, joined, total).takeIf { total > 0 }, part = total > 0, onInvite)
            }
            EmptyStep(4, stringResource(Res.string.fill_emergency), stringResource(Res.string.fill_emergency_sub), part = false, onFillEmergency)
        }

        // design: #FBF8F2, radius 18, padding 14 16, gap 12; 40px #E3EBE5 icon tile, radius 12
        Row(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onTasks).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(Kf.GreenTint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                SvgPath("M4 6.5l2 2 3.5-3.5M4 15.5l2 2 3.5-3.5M13 7h7M13 16h7", 20.dp, Kf.Green)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.tasksTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row {
                    val color = if (s.tasksLate) Kf.Sos else Kf.Muted
                    Text(s.tasksSub, Modifier.weight(1f, fill = false), fontSize = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.tasksSubOwner.isNotEmpty()) Text(" " + s.tasksSubOwner, fontSize = 13.sp, color = color, maxLines = 1)
                }
            }
            Text("›", color = Kf.Muted, fontSize = 18.sp)
        }
        // design: #E9E2D4, radius 18, padding 14 16; 15px semibold over 13px muted
        if (s.digestSub != null) Row(
            Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(18.dp)).tap(onDigest).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.digest_row), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(s.digestSub, fontSize = 13.sp, color = Kf.Muted)
            }
            Text("›", color = Kf.Muted, fontSize = 18.sp)
        }

        // Hidden until the Care Circle has a Duty (approved in #10).
        if (s.week.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(Res.string.this_week), stringResource(Res.string.see_rota), onRota)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.week.forEach { d ->
                    Column(
                        Modifier.weight(1f)
                            .background(if (d.isToday) Kf.Card else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(d.dow, fontSize = 11.sp, color = Kf.Muted)
                        Text(d.num.toString(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Avatar(d.member.initial, d.member.color, 22.dp, 10.sp)
                    }
                }
            }
            Text(s.dutyLegend, fontSize = 13.sp, color = Kf.Muted)
        }

        Row(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onRecords).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // design: conic-gradient(#2F5D4A {{ medDeg }}deg, #E4DDD0 0), "2/4" inside
            val sweep = if (s.medsToday == 0) 0f else 360f * s.medsGiven / s.medsToday
            Box(
                Modifier.size(44.dp).background(Kf.Sand, CircleShape).drawBehind { drawArc(Kf.Green, -90f, sweep, useCenter = true) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).background(Kf.Card, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (s.medsToday == 0) "0" else "${s.medsGiven}/${s.medsToday}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.meds_today), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(s.nextMed, fontSize = 13.sp, color = Kf.Muted)
            }
            Text("›", color = Kf.Muted, fontSize = 18.sp)
        }

        // Hidden until the Timeline has something (approved in #8).
        if (s.feed.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(Res.string.latest), stringResource(Res.string.see_all), onTimeline)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kf.Card)) {
                s.feed.forEach { e ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(e.by.initial, e.by.color, 30.dp, 12.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(e.text, fontSize = 14.sp, lineHeight = (14 * 1.4).sp)
                            Text("${e.by.name} · ${e.whenLabel}", fontSize = 12.sp, color = Kf.Muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.Hairline))
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(a: NextAppointment, onOpen: () -> Unit, onWriteNote: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Kf.Green, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(a.whenLabel, fontSize = 13.sp, color = Cream.copy(alpha = .85f))
            Text(
                if (a.noteReady) stringResource(Res.string.note_ready) else a.countdown,
                fontSize = 12.sp, color = Cream,
                modifier = Modifier.background(CreamTint, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.tap(onOpen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title, style = serifStyle(26f, 1.15f).copy(color = Cream))
            Text(a.provider, fontSize = 14.sp, color = Cream.copy(alpha = .85f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CreamLine))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            a.driver?.let { Avatar(it.initial, it.color, 26.dp, 12.sp) }
            val line = when {
                a.driver == null -> stringResource(Res.string.no_driver)
                a.leavesAt == null -> stringResource(Res.string.drives_no_time, a.driver.name)
                else -> stringResource(Res.string.drives, a.driver.name, a.leavesAt)
            }
            Text(line, fontSize = 14.sp, color = Cream, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(stringResource(Res.string.questions_count, a.questionCount), CreamBtn, Cream, onOpen, Modifier.weight(1f))
            CardButton(stringResource(if (a.noteReady) Res.string.open_summary else Res.string.write_note), Cream, Kf.Green, onWriteNote, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MorningCard(m: Morning, onMark: () -> Unit) {
    val all = m.doses.all { it.given }
    Column(
        Modifier.fillMaxWidth().background(Kf.Green, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.morning_eyebrow, m.at), fontSize = 13.sp, color = Cream.copy(alpha = .85f))
        Text(stringResource(Res.string.morning_title, m.recipient), style = serifStyle(26f, 1.15f).copy(color = Cream))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            m.doses.forEach { d ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, fontSize = 15.sp, color = Cream, modifier = Modifier.weight(1f))
                    Text(stringResource(if (d.given) Res.string.given else Res.string.dose_due), fontSize = 13.sp, color = Cream.copy(alpha = .85f))
                }
            }
        }
        CardButton(stringResource(if (all) Res.string.all_given else Res.string.mark_all_given), Cream, Kf.Green, { if (!all) onMark() }, Modifier.fillMaxWidth())
        m.nextVisit?.let { Text(it, fontSize = 13.sp, color = Cream.copy(alpha = .85f)) }
    }
}

@Composable
private fun EveningCard(e: Evening, onCall: () -> Unit, onCheckIn: () -> Unit) {
    // design: #22261F, radius 22, padding 20, gap 14
    Column(
        Modifier.fillMaxWidth().background(Kf.Ink, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.evening_eyebrow, e.at), fontSize = 13.sp, color = Cream.copy(alpha = .8f))
        Text(stringResource(if (e.bp == null) Res.string.evening_title else Res.string.evening_title_logged), style = serifStyle(26f, 1.15f).copy(color = Cream))
        Text(
            e.bp?.let { (sys, dia) -> stringResource(Res.string.evening_body_logged, sys, dia) }
                ?: stringResource(Res.string.evening_body, e.recipient),
            fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Cream.copy(alpha = .85f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(stringResource(Res.string.call_recipient, e.recipient), CreamBtn, Cream, onCall, Modifier.weight(1f))
            CardButton(stringResource(if (e.bp == null) Res.string.log_checkin else Res.string.edit), Cream, Kf.Ink, onCheckIn, Modifier.weight(1f))
        }
    }
}

/** Rows 1-4 of the v3 `empty` screen, shown on Home while nothing is upcoming (docs/screen-map.md). */
@Composable
private fun EmptyStep(num: Int, title: String, sub: String?, part: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(if (part) Kf.FlagBg else Color.Transparent, CircleShape)
                .border(1.5.dp, if (part) Color(0xFFC9A77C) else Color(0x4022261F), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(num.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, fontSize = 13.sp, color = Kf.Muted) }
        }
        Text("›", color = Kf.Muted, fontSize = 18.sp)
    }
}

@Composable
private fun CardButton(label: String, bg: Color, fg: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(44.dp).background(bg, RoundedCornerShape(12.dp)).tap(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = serifStyle(20f), modifier = Modifier.alignByBaseline())
        Text(action, color = Kf.Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline().tap(onAction))
    }
}
