package id.kinfolk.ui.rota

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Appointment
import id.kinfolk.data.AwayReason
import id.kinfolk.data.DutyTurn
import id.kinfolk.data.MonthLoad
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.DashedButton
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Labeled
import id.kinfolk.ui.Link
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.appointment.TimeInput
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.appointment.dayName
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.parseHm
import id.kinfolk.ui.appointment.shortDay
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.records.monthName
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_duty
import kinfolk.shared.generated.resources.away_banner
import kinfolk.shared.generated.resources.away_none
import kinfolk.shared.generated.resources.away_reason
import kinfolk.shared.generated.resources.away_send
import kinfolk.shared.generated.resources.away_sub
import kinfolk.shared.generated.resources.away_title
import kinfolk.shared.generated.resources.reason_other
import kinfolk.shared.generated.resources.reason_sick
import kinfolk.shared.generated.resources.reason_travelling
import kinfolk.shared.generated.resources.reason_work
import kinfolk.shared.generated.resources.cant_week
import kinfolk.shared.generated.resources.lg_calls
import kinfolk.shared.generated.resources.lg_drive
import kinfolk.shared.generated.resources.lg_other
import kinfolk.shared.generated.resources.month_detail
import kinfolk.shared.generated.resources.month_note
import kinfolk.shared.generated.resources.month_range
import kinfolk.shared.generated.resources.this_week
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.day_done
import kinfolk.shared.generated.resources.delete_duty
import kinfolk.shared.generated.resources.drive_confirmed
import kinfolk.shared.generated.resources.drive_task
import kinfolk.shared.generated.resources.duty_form_head
import kinfolk.shared.generated.resources.duty_order
import kinfolk.shared.generated.resources.duty_task
import kinfolk.shared.generated.resources.load_this_week
import kinfolk.shared.generated.resources.med_name
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.not_this_week
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.swap
import kinfolk.shared.generated.resources.swap_asked
import kinfolk.shared.generated.resources.swap_sub
import kinfolk.shared.generated.resources.swap_title
import kinfolk.shared.generated.resources.swap_waiting
import kinfolk.shared.generated.resources.take_it
import kinfolk.shared.generated.resources.tasks_count
import kinfolk.shared.generated.resources.time
import kinfolk.shared.generated.resources.today
import kinfolk.shared.generated.resources.who_this_week
import kinfolk.shared.generated.resources.you
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

// `rotation` from design v3, week view only, one holder a day (#23), with the deviations approved in #10 (docs/screen-map.md).

private val PastDay = Color(0x8CFBF8F2) // rgba(251,248,242,.55)

/** "28 Sept – 4 Okt" */
fun weekRange(week: LocalDate) = "${dayMonth(week)} – ${dayMonth(week + DatePeriod(days = 6))}"

/** "Telepon cek malam" → "telepon cek malam", inside a sentence. */
fun DutyTurn.inSentence() = name.replaceFirstChar { it.lowercase() }

/** How many Duty days and drives [id] has this week, for the fairness pills and the swap sheet. */
fun load(id: String, turns: List<DutyTurn>, drives: List<Appointment>) = turns.count { it.holder == id } + drives.count { it.driverId == id }

/** "Budi", "Budi dan Dewi", "Budi, Dewi, dan Agus" */
fun listing(names: List<String>, and: String) =
    if (names.size < 3) names.joinToString(" $and ") else names.dropLast(1).joinToString(", ") + ", $and ${names.last()}"

/**
 * v3 away sheet: each of [me]'s days from [today] not already asked goes to whoever of [others] has the least
 * [load], counting the days handed to them so far; ties go to the first in [others].
 */
fun handOff(turns: List<DutyTurn>, me: String, today: LocalDate, others: List<String>, load: (String) -> Int): List<Pair<DutyTurn, String>> {
    if (others.isEmpty()) return emptyList()
    val now = others.associateWith(load).toMutableMap()
    return turns.filter { it.holder == me && it.day >= today && it.swapTo == null }.sortedBy { it.day }.map { t ->
        val to = others.minBy { now.getValue(it) }
        now[to] = now.getValue(to) + 1
        t to to
    }
}

/**
 * [people]: the current Members by id, yourself ([me]) first, with their own names and circle colors. Admins add
 * Duties ([onAdd]) and change one by tapping its slot ([onEdit]); holders ask to swap a day ([onSwap]); the one asked
 * answers on that day's card ([onAnswer]). [month] switches to v3's month view of [monthLoad]; "Saya tidak bisa
 * minggu ini" ([onAway], null for those who take no Duty days) until I am [away], then v3's yellow banner until the week ends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotaScreen(
    week: LocalDate,
    today: LocalDate,
    tz: TimeZone,
    turns: List<DutyTurn>,
    drives: List<Appointment>,
    people: Map<String, Person>,
    me: String,
    admin: Boolean,
    onSwap: (DutyTurn) -> Unit,
    onAnswer: (DutyTurn, Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (DutyTurn) -> Unit,
    month: Boolean,
    onMonth: (Boolean) -> Unit,
    monthLoad: List<MonthLoad>,
    away: Boolean,
    onAway: (() -> Unit)?,
) {
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (month) stringResource(Res.string.month_range, monthName(today)) else weekRange(week), fontSize = 13.sp, color = Kf.Muted)
            Text(stringResource(Res.string.who_this_week), style = serifStyle(30f, 1.1f))
        }
        // design: grid 2 cols, gap 4, #E4DDD0, r12, p4; buttons r9, h36, 14px 600
        Row(Modifier.fillMaxWidth().background(Kf.Sand, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to stringResource(Res.string.this_week), true to monthName(today)).forEach { (m, label) ->
                Box(
                    Modifier.weight(1f).height(36.dp).background(if (m == month) Kf.Card else Color.Transparent, RoundedCornerShape(9.dp)).tap { onMonth(m) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (away) Text(
            stringResource(Res.string.away_banner, "${dayName(week + DatePeriod(days = 6))} ${dayMonth(week + DatePeriod(days = 6))}"),
            Modifier.fillMaxWidth().background(Kf.FlagBg, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.FlagInk,
        )
        else if (onAway != null) Box(
            Modifier.fillMaxWidth().height(46.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(14.dp)).tap(onAway),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(Res.string.cant_week), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        if (month) MonthView(people, monthLoad) else WeekView(week, today, tz, turns, drives, people, me, admin, onSwap, onAnswer, onAdd, onEdit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekView(
    week: LocalDate,
    today: LocalDate,
    tz: TimeZone,
    turns: List<DutyTurn>,
    drives: List<Appointment>,
    people: Map<String, Person>,
    me: String,
    admin: Boolean,
    onSwap: (DutyTurn) -> Unit,
    onAnswer: (DutyTurn, Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (DutyTurn) -> Unit,
) {
    val you = stringResource(Res.string.you)
    fun sub(id: String) = if (id == me) you else people[id]?.name.orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { (id, p) ->
            Row(
                Modifier.background(Kf.Card, CircleShape).padding(start = 5.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.initial, p.color, 22.dp, 10.sp)
                Text(stringResource(Res.string.tasks_count, load(id, turns, drives)), fontSize = 13.sp)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..6).forEach { i ->
            val day = week + DatePeriod(days = i)
            val isToday = day == today
            Column(
                Modifier.fillMaxWidth().background(if (isToday) Kf.Card else PastDay, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${shortDay(day)} ${day.day}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
                    val note = when {
                        isToday -> stringResource(Res.string.today)
                        day < today -> stringResource(Res.string.day_done)
                        else -> ""
                    }
                    Text(note, fontSize = 12.sp, color = Kf.Muted, modifier = Modifier.alignByBaseline())
                }
                drives.filter { it.startsAt.toLocalDateTime(tz).date == day }.forEach { a ->
                    val id = a.driverId ?: return@forEach
                    val p = people[id] ?: return@forEach
                    val at = (a.departsAt ?: a.startsAt).toLocalDateTime(tz).time
                    val confirmed = a.driverConfirmedAt != null
                    Slot(
                        p, stringResource(Res.string.drive_task, a.title.replaceFirstChar { it.lowercase() }, hm(at)),
                        if (confirmed) stringResource(Res.string.drive_confirmed) else sub(id), if (confirmed) Kf.Green else Kf.Muted, null, {},
                    ) {}
                }
                turns.filter { it.day == day }.forEach { t ->
                    val id = t.holder ?: return@forEach
                    val p = people[id] ?: return@forEach
                    val waiting = t.swapTo?.takeIf { id == me }
                    val askedOfMe = t.swapTo == me
                    Slot(
                        p, stringResource(Res.string.duty_task, t.name, hm(t.timeOfDay)),
                        when {
                            waiting != null -> stringResource(Res.string.swap_waiting, people[waiting]?.name.orEmpty())
                            askedOfMe -> stringResource(Res.string.swap_asked, people[id]?.name.orEmpty())
                            else -> sub(id)
                        },
                        if (waiting != null) Kf.Sos else Kf.Muted,
                        stringResource(Res.string.swap).takeIf { id == me && t.swapTo == null && day >= today },
                        { onSwap(t) },
                    ) { if (admin) onEdit(t) }
                    if (askedOfMe && day >= today) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(46.dp).background(Kf.Green, RoundedCornerShape(12.dp)).tap { onAnswer(t, true) },
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(Res.string.take_it), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        Box(
                            Modifier.weight(1f).height(46.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(12.dp)).tap { onAnswer(t, false) },
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(Res.string.not_this_week), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
    if (admin) DashedButton(stringResource(Res.string.add_duty), onAdd)
}

private val CallsColor = Color(0xFFC9A77C)
private val OtherColor = Color(0xFF8FA79A)
private val BarTrack = Color(0xFFEDE7DB)

/** v3 month view: a stacked bar per Member, scaled to whoever has the most. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthView(people: Map<String, Person>, loads: List<MonthLoad>) {
    val byId = loads.associateBy { it.memberId }
    val rows = people.map { (id, p) -> p to (byId[id] ?: MonthLoad(id, 0, 0, 0)) }
    val most = rows.maxOfOrNull { (_, l) -> l.drives + l.calls + l.other }?.takeIf { it > 0 } ?: 1
    // design: gap 14; card #FBF8F2 r18 p16 gap 16
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { (p, l) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.initial, p.color, 24.dp, 11.sp)
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(Res.string.tasks_count, l.drives + l.calls + l.other), fontSize = 14.sp, color = Kf.Muted)
                    }
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(BarTrack)) {
                        val total = l.drives + l.calls + l.other
                        listOf(l.drives to Kf.Green, l.calls to CallsColor, l.other to OtherColor).forEach { (n, c) ->
                            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c))
                        }
                        if (total < most) Spacer(Modifier.weight((most - total).toFloat()))
                    }
                    Text(stringResource(Res.string.month_detail, l.drives, l.calls, l.other), fontSize = 12.sp, color = Kf.Muted)
                }
            }
            // design: wrap gap 12, 12px #44463E; swatch 10, r2, gap 6
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Kf.Green to Res.string.lg_drive, CallsColor to Res.string.lg_calls, OtherColor to Res.string.lg_other).forEach { (c, label) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                        Text(stringResource(label), fontSize = 12.sp, color = Kf.Ink2)
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.month_note),
            Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Ink2,
        )
    }
}

// design: gap 10, avatar 30, task 14, sub 12, action 12/600 green
@Composable
private fun Slot(p: Person, task: String, sub: String, subColor: Color, action: String?, onAction: () -> Unit, onTap: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onTap), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.initial, p.color, 30.dp, 12.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(task, fontSize = 14.sp)
            Text(sub, fontSize = 12.sp, color = subColor)
        }
        action?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Kf.Green, modifier = Modifier.tap(onAction)) }
    }
}

/** SWAP SHEET body: the other Members with their load this week. */
@Composable
fun ColumnScope.SwapSheet(turn: DutyTurn, others: Map<String, Person>, load: (String) -> Int, onPick: (String) -> Unit) {
    // "Tukar telepon cek malam Anda · Min 4"
    Text(stringResource(Res.string.swap_title, turn.inSentence(), "${shortDay(turn.day)} ${turn.day.day}"), style = serifStyle(22f, 1.2f))
    Text(stringResource(Res.string.swap_sub), fontSize = 14.sp, color = Kf.Ink2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        others.forEach { (id, p) ->
            Row(
                Modifier.fillMaxWidth().background(Kf.Paper, RoundedCornerShape(14.dp)).tap { onPick(id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.initial, p.color, 32.dp, 13.sp)
                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(stringResource(Res.string.load_this_week, load(id)), fontSize = 12.sp, color = Kf.Muted)
            }
        }
    }
}

/** AWAY SHEET body: a reason, then each of [asks] (my day → who is asked); [onSend] with the reason picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.AwaySheet(asks: List<Pair<DutyTurn, String>>, people: Map<String, Person>, onSend: (AwayReason) -> Unit) {
    var reason by remember { mutableStateOf(AwayReason.sick) }
    Text(stringResource(Res.string.away_title), style = serifStyle(22f, 1.2f))
    Text(stringResource(Res.string.away_sub), fontSize = 14.sp, lineHeight = (14 * 1.45).sp, color = Kf.Ink2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.away_reason), fontSize = 12.sp, color = Kf.Muted)
        // design: border 1, r999, p 8 14, 14px; picked filled green
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                AwayReason.sick to Res.string.reason_sick, AwayReason.travelling to Res.string.reason_travelling,
                AwayReason.work to Res.string.reason_work, AwayReason.other to Res.string.reason_other,
            ).forEach { (r, label) ->
                val on = r == reason
                Text(
                    stringResource(label), color = if (on) Kf.Paper else Kf.Ink, fontSize = 14.sp,
                    modifier = Modifier.background(if (on) Kf.Green else Color.Transparent, CircleShape)
                        .border(1.dp, if (on) Kf.Green else Kf.InputBorder, CircleShape).tap { reason = r }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        asks.forEach { (t, to) ->
            val p = people[to] ?: return@forEach
            // design: #F3EEE4 r14 p 12 14, gap 12
            Row(
                Modifier.fillMaxWidth().background(Kf.Paper, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${shortDay(t.day)} ${t.day.day}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.duty_task, t.name, hm(t.timeOfDay)), fontSize = 13.sp, color = Kf.Muted)
                }
                Text("→", fontSize = 13.sp, color = Kf.Muted)
                Avatar(p.initial, p.color, 28.dp, 12.sp)
                Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    PrimaryButton(if (asks.isEmpty()) stringResource(Res.string.away_none) else stringResource(Res.string.away_send, asks.size)) { onSend(reason) }
}

data class DutyForm(val name: String, val timeOfDay: LocalTime, val order: List<String>)

/**
 * Approved deviation (#10): add or change a Duty on the onb1 layout, like Form Obat. Tapping Members under "Urutan"
 * numbers them in turn; tapping again takes them out. [save] and [delete] return false when offline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DutyFormScreen(editing: DutyTurn?, people: Map<String, Person>, onBack: () -> Unit, save: suspend (DutyForm) -> Boolean, delete: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(editing?.timeOfDay?.let { hm(it).replace(".", "") }.orEmpty()) }
    var order by remember { mutableStateOf(editing?.rotation.orEmpty().filter { it in people }) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Boolean) {
        if (!busy) scope.launch { busy = true; failed = !action(); busy = false }
    }

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.duty_form_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.med_name)) {
            BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = 17.sp), singleLine = true)
        }
        Field(stringResource(Res.string.time)) { TimeInput(time, { time = it }) }
        Labeled(stringResource(Res.string.duty_order)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { (id, p) ->
                    val n = order.indexOf(id) + 1
                    PickChip(if (n > 0) "$n · ${p.name}" else p.name, n > 0) { order = if (n > 0) order - id else order + id }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                val at = parseHm(time)
                if (name.isNotBlank() && at != null && order.isNotEmpty()) run { save(DutyForm(name.trim(), at, order)) }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
            if (editing != null) Link(stringResource(Res.string.delete_duty), Kf.Sos, { run(delete) }, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
