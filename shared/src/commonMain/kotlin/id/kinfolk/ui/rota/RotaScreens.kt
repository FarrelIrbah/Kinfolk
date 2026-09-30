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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Appointment
import id.kinfolk.data.DutyTurn
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
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.parseHm
import id.kinfolk.ui.appointment.shortDay
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_duty
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

// `rotation` from design v3, week view only, with the deviations approved in #10 (docs/screen-map.md).

private val PastDay = Color(0x8CFBF8F2) // rgba(251,248,242,.55)
private val OutlineBtn = Color(0x2E22261F) // rgba(34,38,31,.18)

/** "28 Sept – 4 Okt" */
fun weekRange(week: LocalDate) = "${dayMonth(week)} – ${dayMonth(week + DatePeriod(days = 6))}"

/** "Telepon cek malam" → "telepon cek malam", inside a sentence. */
fun DutyTurn.inSentence() = name.replaceFirstChar { it.lowercase() }

/** How many Duties and drives [id] has this week, for the fairness pills and the swap sheet. */
fun load(id: String, turns: List<DutyTurn>, drives: List<Appointment>) = turns.count { it.holder == id } + drives.count { it.driverId == id }

/**
 * [people]: the current Members by id, yourself ([me]) first, with their own names and circle colors. Admins add
 * Duties ([onAdd]) and change one by tapping its slot ([onEdit]); holders ask for a swap ([onSwap]); the one asked
 * answers on today's card ([onAnswer]).
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
) {
    val you = stringResource(Res.string.you)
    fun sub(id: String) = if (id == me) you else people[id]?.name.orEmpty()
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(weekRange(week), fontSize = 13.sp, color = Kf.Muted)
            Text(stringResource(Res.string.who_this_week), style = serifStyle(30f, 1.1f))
        }
        // ponytail: week/month switch and "Saya tidak bisa minggu ini" hidden until the month and away views ship.
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
                    turns.forEach { t ->
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
                        if (askedOfMe && isToday) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.weight(1f).height(46.dp).background(Kf.Green, RoundedCornerShape(12.dp)).tap { onAnswer(t, true) },
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(Res.string.take_it), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                            Box(
                                Modifier.weight(1f).height(46.dp).border(1.dp, OutlineBtn, RoundedCornerShape(12.dp)).tap { onAnswer(t, false) },
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(Res.string.not_this_week), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        if (admin) DashedButton(stringResource(Res.string.add_duty), onAdd)
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
    Text(stringResource(Res.string.swap_title, turn.inSentence()), style = serifStyle(22f, 1.2f))
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
