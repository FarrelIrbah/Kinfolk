package id.kinfolk.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import id.kinfolk.data.Ate
import id.kinfolk.data.CheckIn
import id.kinfolk.data.Mood
import id.kinfolk.data.trend
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.timeline.EmptyBox
import kinfolk.shared.generated.resources.bp_avg
import kinfolk.shared.generated.resources.bp_label
import kinfolk.shared.generated.resources.ci_mood
import kinfolk.shared.generated.resources.dia
import kinfolk.shared.generated.resources.good_of_30
import kinfolk.shared.generated.resources.habit_ate
import kinfolk.shared.generated.resources.habit_walked
import kinfolk.shared.generated.resources.line140
import kinfolk.shared.generated.resources.sys
import kinfolk.shared.generated.resources.today
import kinfolk.shared.generated.resources.trends_empty
import kinfolk.shared.generated.resources.trends_note
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.appointment.DayPicker
import id.kinfolk.ui.appointment.shortDate
import id.kinfolk.ui.home.Person
import kinfolk.shared.generated.resources.blood_thinner
import kinfolk.shared.generated.resources.given
import kinfolk.shared.generated.resources.mark_given
import kinfolk.shared.generated.resources.med_note
import kinfolk.shared.generated.resources.nobody
import kinfolk.shared.generated.resources.rec_costs
import kinfolk.shared.generated.resources.rec_docs
import kinfolk.shared.generated.resources.rec_health
import kinfolk.shared.generated.resources.rec_meds
import kinfolk.shared.generated.resources.refill
import kinfolk.shared.generated.resources.tap_taken
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Medication
import id.kinfolk.ui.DashedButton
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.Toggle
import id.kinfolk.ui.appointment.TimeInput
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.parseHm
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_med
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.med_dose
import kinfolk.shared.generated.resources.med_form_head
import kinfolk.shared.generated.resources.med_name
import kinfolk.shared.generated.resources.med_schedule
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.still_taking
import kinfolk.shared.generated.resources.stopped_meds
import kinfolk.shared.generated.resources.tab_records
import kinfolk.shared.generated.resources.time
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/** "75 mg · pagi, sesudah makan" */
fun Medication.doseLine() = listOf(dose, schedule).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * The card's coloured line (owner-approved in #21): a blood thinner first, then a refill due within a week (by
 * [refiller]), then the Catatan; null when there's nothing to say.
 */
fun Medication.noteLine(today: LocalDate, refiller: String?): Pair<String, Color>? {
    // ponytail: an overdue refill reads "hari ini"; its own copy if families leave them overdue.
    val days = refillOn?.let { (today.daysUntil(it)).coerceAtLeast(0) }
    return when {
        bloodThinner -> listOf("Pengencer darah.", note).filter { it.isNotBlank() }.joinToString(" ") to Kf.Sos
        days != null && days <= 7 -> {
            val due = when (days) { 0 -> "Isi ulang hari ini"; 1 -> "Isi ulang besok"; else -> "Isi ulang $days hari lagi" }
            (listOfNotNull(due, refiller).joinToString(" · ")) to Gold
        }
        note.isNotBlank() -> note to Kf.Muted
        else -> null
    }
}

private val Gold = Color(0xFF9A7A2F)
private val GivenBorder = Color(0x3322261F) // rgba(34,38,31,.2)

private enum class RecTab(val label: StringResource) {
    Meds(Res.string.rec_meds), Docs(Res.string.rec_docs), Costs(Res.string.rec_costs), Health(Res.string.rec_health),
}

/**
 * `records` (docs/screen-map.md): v3's four tabs; Obat marks today's doses ("Tandai" / "Diberikan ✓", [given] =
 * Medication ids). Approved deviation (#11): "+ Tambah obat", a "Tidak diminum lagi" group, tap a card to edit.
 * Kondisi (#25) charts [checkIns], the last 30 oldest first.
 * ponytail: Dokumen and Biaya stay empty until their tickets.
 */
@Composable
fun RecordsScreen(
    meds: List<Medication>,
    given: Set<String>,
    checkIns: List<CheckIn>,
    today: LocalDate,
    nameOf: (String) -> String?,
    onToggle: (Medication) -> Unit,
    onOpen: (Medication?) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(RecTab.Meds) }
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(Res.string.tab_records), style = serifStyle(30f, 1.1f))
        // design: grid 4 cols, gap 4, #E4DDD0, r12, p4; buttons r9, h36, 13px 600
        Row(Modifier.fillMaxWidth().background(Kf.Sand, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RecTab.entries.forEach { t ->
                Box(
                    Modifier.weight(1f).height(36.dp).background(if (t == tab) Kf.Card else Color.Transparent, RoundedCornerShape(9.dp)).tap { tab = t },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(t.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (tab == RecTab.Meds) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.tap_taken), fontSize = 13.sp, color = Kf.Muted)
                meds.filter { it.active }.forEach { m ->
                    MedCard(m, m.noteLine(today, m.refillBy?.let(nameOf)), onOpen) { GivenButton(m.id in given) { onToggle(m) } }
                }
                DashedButton(stringResource(Res.string.add_med)) { onOpen(null) }
            }
            val stopped = meds.filterNot { it.active }
            if (stopped.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.stopped_meds))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { stopped.forEach { MedCard(it, null, onOpen) {} } }
            }
        }
        if (tab == RecTab.Health) Trends(checkIns)
    }
}

private val Tan = Color(0xFFC9A77C)
private val Rust = Color(0xFFC4471F)

/**
 * v3's Kondisi: blood pressure on a 60–160 scale with the dashed 140 line, and a 30-column grid per habit filled
 * from the right. Owner-approved in #25: the first Check-in's day ("31 Agu") under the grid, the empty box before
 * any. The "Bawa ke dokter" button waits for Export.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trends(checkIns: List<CheckIn>) {
    // design: gap 12
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.trends_note), fontSize = 13.sp, color = Kf.Muted)
        if (checkIns.isEmpty()) return@Column EmptyBox(stringResource(Res.string.trends_empty))
        val t = checkIns.trend()
        val last = checkIns.last()
        // design: #FBF8F2, r18, p16, gap 12
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.bp_label), Modifier.alignByBaseline(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${last.sys}/${last.dia}", Modifier.alignByBaseline(), style = serifStyle(24f))
            }
            BpChart(checkIns)
            // design: wrap, gap 12, 12px #44463E; swatches 12x2, gap 6
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(stringResource(Res.string.sys)) { Box(Modifier.width(12.dp).height(2.dp).background(Kf.Green)) }
                Legend(stringResource(Res.string.dia)) { Box(Modifier.width(12.dp).height(2.dp).background(Tan)) }
                Legend(stringResource(Res.string.line140)) {
                    Canvas(Modifier.width(12.dp).height(1.dp)) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                        drawLine(Kf.Sos, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = dash)
                    }
                }
            }
            Text(stringResource(Res.string.bp_avg, t.sys, t.dia, t.high), fontSize = 13.sp, color = Kf.Ink2)
        }
        // design: #FBF8F2, r18, p16, gap 14
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Habit(stringResource(Res.string.habit_ate), t.ate, checkIns) { when (it.ate) { Ate.yes -> Kf.Green; Ate.some -> Tan; Ate.no -> Rust } }
            Habit(stringResource(Res.string.habit_walked), t.walked, checkIns) { if (it.walked) Kf.Green else Kf.Sand }
            Habit(stringResource(Res.string.ci_mood), t.good, checkIns) { when (it.mood) { Mood.good -> Kf.Green; Mood.okay -> Tan; Mood.low -> Rust } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayMonth(checkIns.first().day), fontSize = 11.sp, color = Kf.Muted)
                Text(stringResource(Res.string.today), fontSize = 11.sp, color = Kf.Muted)
            }
        }
    }
}

/** v3's svg: viewBox 300x120 stretched, y = 120 - (v - 60) / 100 * 120, so 140 sits at 24. */
@Composable
private fun BpChart(checkIns: List<CheckIn>) = Canvas(Modifier.fillMaxWidth().height(120.dp)) {
    val y = { v: Int -> size.height - (v - 60) / 100f * size.height }
    // One Check-in has no line yet: a dot on today's side.
    val x = { i: Int -> if (checkIns.size == 1) size.width else i * size.width / (checkIns.size - 1) }
    drawLine(
        Kf.Sos.copy(alpha = .6f), Offset(0f, y(140)), Offset(size.width, y(140)), 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
    listOf<Pair<Color, (CheckIn) -> Int>>(Kf.Green to { it.sys }, Tan to { it.dia }).forEach { (color, v) ->
        if (checkIns.size == 1) return@forEach drawCircle(color, 2.dp.toPx(), Offset(x(0), y(v(checkIns[0]))))
        val path = Path().apply { checkIns.forEachIndexed { i, c -> if (i == 0) moveTo(x(i), y(v(c))) else lineTo(x(i), y(v(c))) } }
        drawPath(path, color, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun Legend(label: String, swatch: @Composable () -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Text(label, fontSize = 12.sp, color = Kf.Ink2)
    }

/** "Makan malam · 27 dari 30 baik" over 30 cells (h14, r3, gap 2); before 30 Check-ins the left ones stay #E4DDD0. */
@Composable
private fun Habit(label: String, good: Int, checkIns: List<CheckIn>, color: (CheckIn) -> Color) =
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.good_of_30, good), fontSize = 14.sp, color = Kf.Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (List(30 - checkIns.size) { Kf.Sand } + checkIns.map(color)).forEach {
                Box(Modifier.weight(1f).height(14.dp).background(it, RoundedCornerShape(3.dp)))
            }
        }
    }

@Composable
private fun MedCard(m: Medication, note: Pair<String, Color>?, onOpen: (Medication) -> Unit, action: @Composable () -> Unit) {
    // design: r18, padding 14px 16px, gap 14, centred
    Row(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap { onOpen(m) }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (m.active) Kf.Ink else Kf.Muted)
            m.doseLine().takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = if (m.active) Kf.Ink2 else Kf.Muted) }
            note?.let { (text, color) -> Text(text, fontSize = 12.sp, color = color) }
        }
        action()
    }
}

@Composable
private fun GivenButton(on: Boolean, onClick: () -> Unit) {
    // design: 1.5px border, r999, h40, padding 0 14px, 13px 600
    Box(
        Modifier.height(40.dp).background(if (on) Kf.Green else Color.Transparent, CircleShape)
            .border(1.5.dp, if (on) Kf.Green else GivenBorder, CircleShape).tap(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(if (on) Res.string.given else Res.string.mark_given), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Kf.Paper else Kf.Ink)
    }
}

data class MedForm(
    val name: String, val dose: String, val schedule: String, val timeOfDay: LocalTime, val active: Boolean,
    val note: String, val bloodThinner: Boolean, val refillOn: LocalDate?, val refillBy: String?,
)

/**
 * Approved deviation (#11, #21): add or edit a Medication on the onb1 layout, refilled by one of [people].
 * [save] returns false when offline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedFormScreen(editing: Medication?, people: Map<String, Person>, onBack: () -> Unit, save: suspend (MedForm) -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var dose by rememberSaveable { mutableStateOf(editing?.dose.orEmpty()) }
    var schedule by rememberSaveable { mutableStateOf(editing?.schedule.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(editing?.timeOfDay?.let { hm(it).replace(".", "") }.orEmpty()) }
    var active by rememberSaveable { mutableStateOf(editing?.active ?: true) }
    var note by rememberSaveable { mutableStateOf(editing?.note.orEmpty()) }
    var thinner by rememberSaveable { mutableStateOf(editing?.bloodThinner ?: false) }
    var refillOn by rememberSaveable { mutableStateOf(editing?.refillOn?.toString()) }
    var refillBy by rememberSaveable { mutableStateOf(editing?.refillBy) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.med_form_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.med_name)) { BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.med_dose)) { BasicTextField(dose, { dose = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.med_schedule)) { BasicTextField(schedule, { schedule = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.time)) { TimeInput(time, { time = it }) }
        Field(stringResource(Res.string.med_note), multiline = true) {
            BasicTextField(note, { note = it }, Modifier.fillMaxWidth(), textStyle = input.copy(lineHeight = (17 * 1.5).sp))
        }
        Toggle(stringResource(Res.string.blood_thinner), thinner) { thinner = it }
        // Date, then who refills it (kept even before a date is picked); "Belum ada" clears both.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(Res.string.refill), Modifier.tap { picking = true }) {
                Text(refillOn?.let { shortDate(LocalDate.parse(it)) }.orEmpty(), fontSize = 17.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { (id, p) -> PickChip(p.name, id == refillBy) { refillBy = id } }
                PickChip(stringResource(Res.string.nobody), refillOn == null && refillBy == null) { refillOn = null; refillBy = null }
            }
        }
        if (editing != null) Toggle(stringResource(Res.string.still_taking), active) { active = it }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                val at = parseHm(time)
                if (name.isNotBlank() && at != null && !busy) scope.launch {
                    busy = true
                    failed = !save(
                        MedForm(
                            name.trim(), dose.trim(), schedule.trim(), at, active,
                            note.trim(), thinner, refillOn?.let(LocalDate::parse), refillBy,
                        ),
                    )
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
    if (picking) DayPicker(refillOn?.let(LocalDate::parse), { refillOn = it.toString(); picking = false }) { picking = false }
}
