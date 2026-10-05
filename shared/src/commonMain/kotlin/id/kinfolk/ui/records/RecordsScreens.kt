package id.kinfolk.ui.records

import androidx.compose.ui.unit.em
import kinfolk.shared.generated.resources.mc_view
import kinfolk.shared.generated.resources.mc_label
import kinfolk.shared.generated.resources.mc_apply
import id.kinfolk.ui.appointment.DoseLine
import id.kinfolk.data.DoseChange
import id.kinfolk.data.AppliedDoseChange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import id.kinfolk.data.Ate
import id.kinfolk.data.Expense
import id.kinfolk.data.DataCategory
import id.kinfolk.data.Document
import id.kinfolk.ui.circle.CircleMember
import kinfolk.shared.generated.resources.cat_wishes
import kinfolk.shared.generated.resources.docs_note
import kinfolk.shared.generated.resources.export_title
import kinfolk.shared.generated.resources.bring_to_dr
import kinfolk.shared.generated.resources.upload_doc
import kinfolk.shared.generated.resources.vis_all
import kinfolk.shared.generated.resources.vis_n
import kotlin.time.Instant
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Card
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.HintedInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kinfolk.shared.generated.resources.add_expense
import kinfolk.shared.generated.resources.costs_note
import kinfolk.shared.generated.resources.paid_by
import kinfolk.shared.generated.resources.what_ph
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
 * [refiller]), then a dose [changed] at a visit within a week (#47, with its day), then the Catatan; null when
 * there's nothing to say.
 */
fun Medication.noteLine(today: LocalDate, refiller: String?, changed: Pair<AppliedDoseChange, LocalDate>? = null): Pair<String, Color>? {
    // ponytail: an overdue refill reads "hari ini"; its own copy if families leave them overdue.
    val days = refillOn?.let { (today.daysUntil(it)).coerceAtLeast(0) }
    return when {
        bloodThinner -> listOf("Pengencer darah.", note).filter { it.isNotBlank() }.joinToString(" ") to Kf.Sos
        days != null && days <= 7 -> {
            val due = when (days) { 0 -> "Isi ulang hari ini"; 1 -> "Isi ulang besok"; else -> "Isi ulang $days hari lagi" }
            (listOfNotNull(due, refiller).joinToString(" · ")) to Gold
        }
        // ponytail: a week, like the refill line; v3 never says how long "Naik dari 5 mg" stays.
        changed != null && changed.second.daysUntil(today) <= 7 ->
            "Diubah dari ${changed.first.fromDose} pada ${dayMonth(changed.second)} · ${changed.first.saidBy}" to Kf.Green
        note.isNotBlank() -> note to Kf.Muted
        else -> null
    }
}

/** v3's yellow "Dosis diubah di kunjungan hari ini" banner on Obat, [source] its "Dr. Rao mengatakannya …" line. */
@Composable
private fun DoseBanner(d: DoseChange, source: String, onApply: () -> Unit, onView: () -> Unit) {
    // design: #F4E4B0, radius 18, padding 16, gap 10
    Column(Modifier.fillMaxWidth().background(Kf.FlagBg, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.mc_label).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .08.em, color = Kf.FlagInk)
        DoseLine(d, 17, Kf.Ink)
        Text(source, fontSize = 13.sp, lineHeight = (13 * 1.45).sp, color = Kf.FlagInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(42.dp).background(Kf.Ink, RoundedCornerShape(12.dp)).tap(onApply), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.mc_apply), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.weight(1f).height(42.dp).border(1.dp, DashLine, RoundedCornerShape(12.dp)).tap(onView), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.mc_view), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val DashLine = Color(0x4022261F) // rgba(34,38,31,.25)

/** Approved deviation: Rupiah, not v3's dollars. "Rp 250.000" */
fun rupiah(amount: Long) = "Rp " + amount.toString().reversed().chunked(3).joinToString(".").reversed()

/** What was typed in the "Rp" box: dots are thousands, so only the digits count; null for nothing or zero. */
fun parseRupiah(typed: String): Long? = typed.filter { it.isDigit() }.takeIf { it.length <= 15 }?.toLongOrNull()?.takeIf { it > 0 }

private val monthNames = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")

/** "September" */
fun monthName(d: LocalDate) = monthNames[d.month.ordinal]

/** v3's "September sejauh ini", for [today]'s month. */
fun soFar(today: LocalDate) = "${monthName(today)} sejauh ini"

private val Gold = Color(0xFF9A7A2F)
private val GivenBorder = Color(0x3322261F) // rgba(34,38,31,.2)

enum class RecTab(val label: StringResource) {
    Meds(Res.string.rec_meds), Docs(Res.string.rec_docs), Costs(Res.string.rec_costs), Health(Res.string.rec_health),
}

/**
 * `records` (docs/screen-map.md): v3's four tabs; Obat marks today's doses ("Tandai" / "Diberikan ✓", [given] =
 * Medication ids). Approved deviation (#11): "+ Tambah obat", a "Tidak diminum lagi" group, tap a card to edit.
 * Kondisi (#25) charts [checkIns], the last 30 oldest first. Biaya (#28) shows [costs], gone while Tagihan & uang
 * is hidden from me (null). [bring]: Kondisi's "Lampirkan ke kunjungan Dr. Rao (PDF)" with the next Appointment's
 * Provider, null without one (owner-approved in #33).
 */
@Composable
fun RecordsScreen(
    meds: List<Medication>,
    given: Set<String>,
    checkIns: List<CheckIn>,
    docs: Docs?,
    costs: Costs?,
    today: LocalDate,
    picked: RecTab,
    onPick: (RecTab) -> Unit,
    nameOf: (String) -> String?,
    bring: Pair<String, () -> Unit>?,
    /** Dose changes heard at a visit and not applied yet (#47): the yellow banner. */
    pending: List<DoseChange>,
    /** The newest applied change of a Medication, with its day. */
    changed: (Medication) -> Pair<AppliedDoseChange, LocalDate>?,
    source: (DoseChange) -> String,
    onApply: (DoseChange) -> Unit,
    onView: (DoseChange) -> Unit,
    onToggle: (Medication) -> Unit,
    onOpen: (Medication?) -> Unit,
) {
    val tabs = RecTab.entries.filter { (it != RecTab.Costs || costs != null) && (it != RecTab.Docs || docs != null) }
    val tab = picked.takeIf { it in tabs } ?: RecTab.Meds
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(Res.string.tab_records), style = serifStyle(30f, 1.1f))
        // design: grid 4 cols, gap 4, #E4DDD0, r12, p4; buttons r9, h36, 13px 600
        Row(Modifier.fillMaxWidth().background(Kf.Sand, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { t ->
                Box(
                    Modifier.weight(1f).height(36.dp).background(if (t == tab) Kf.Card else Color.Transparent, RoundedCornerShape(9.dp)).tap { onPick(t) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(t.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (tab == RecTab.Meds) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.tap_taken), fontSize = 13.sp, color = Kf.Muted)
                pending.forEach { DoseBanner(it, source(it), { onApply(it) }) { onView(it) } }
                meds.filter { it.active }.forEach { m ->
                    MedCard(m, m.noteLine(today, m.refillBy?.let(nameOf), changed(m)), onOpen) { GivenButton(m.id in given) { onToggle(m) } }
                }
                DashedButton(stringResource(Res.string.add_med)) { onOpen(null) }
            }
            val stopped = meds.filterNot { it.active }
            if (stopped.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.stopped_meds))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { stopped.forEach { MedCard(it, null, onOpen) {} } }
            }
        }
        if (tab == RecTab.Docs && docs != null) Docs(docs, today)
        if (tab == RecTab.Costs && costs != null) Costs(costs, today)
        if (tab == RecTab.Health) Trends(checkIns, bring)
    }
}

/**
 * Dokumen: [list] the newest version of each, [seenBy] its pill (null = "Semua"), [person] who uploaded it, [day] when.
 * [open] shows a file, [upload] starts the picker.
 */
class Docs(
    val list: List<Document>,
    val seenBy: (DataCategory) -> Int?,
    val person: (String) -> Person,
    val day: (Instant) -> LocalDate,
    val open: (Document) -> Unit,
    val upload: () -> Unit,
    val export: () -> Unit,
)

/** v3's pill: null ("Semua") when every Member but the Care Recipient sees [category], else how many do. */
fun seenBy(members: List<CircleMember>, category: DataCategory): Int? {
    val others = members.filterNot { it.isRecipient }
    return others.count { category in it.sees }.takeIf { it < others.size }
}

/** v3's "12 Juni · v2 · Rina", the day as elsewhere ("12 Jun"), with its year when not this one. */
fun docMeta(day: LocalDate, today: LocalDate, version: Int, by: String) =
    "${dayMonth(day)}${if (day.year != today.year) " ${day.year}" else ""} · v$version · $by"

private val PillAll = Color(0xFFE3EBE5)
private val PillSome = Color(0xFFE9E2D4)
private val PillLegal = Color(0xFFF0DDD3)
private val TileBorder = Color(0x1A22261F) // rgba(34,38,31,.1)

/** v3's Dokumen: the note, the list, "+ Unggah dokumen", "Ekspor untuk dokter baru". */
@Composable
private fun Docs(d: Docs, today: LocalDate) {
    // design: gap 10
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.docs_note), fontSize = 13.sp, color = Kf.Muted)
        if (d.list.isNotEmpty()) Card {
            d.list.forEach { doc ->
                val n = d.seenBy(doc.category)
                // design: padding 14px 16px, gap 14, border-bottom rgba(34,38,31,.07)
                Row(Modifier.fillMaxWidth().tap { d.open(doc) }.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    // design: 38x46, r6, #E9E2D4, 1px rgba(34,38,31,.1), ext at the bottom (padding-bottom 5), 9px 600 #6B6A60
                    Box(
                        Modifier.width(38.dp).height(46.dp).background(PillSome, RoundedCornerShape(6.dp)).border(1.dp, TileBorder, RoundedCornerShape(6.dp)).padding(bottom = 5.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) { Text(doc.ext, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Kf.Muted) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(doc.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(docMeta(d.day(doc.at), today, doc.version, d.person(doc.by).name), fontSize = 12.sp, color = Kf.Muted)
                    }
                    // design: 11px 600, padding 3px 8px, r999
                    Text(
                        if (n == null) stringResource(Res.string.vis_all) else stringResource(Res.string.vis_n, n),
                        Modifier.background(if (doc.legal) PillLegal else if (n == null) PillAll else PillSome, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Hairline()
            }
        }
        DashedButton(stringResource(Res.string.upload_doc), d.upload)
        // design: h50, r16, #22261F, #F3EEE4, 15px 600
        Box(Modifier.fillMaxWidth().height(50.dp).background(Kf.Ink, RoundedCornerShape(16.dp)).tap(d.export), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.export_title), color = Kf.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Approved deviation (#29): after the picker, its name ("Nama", from the file) and whether it is legal. */
class DocForm(val name: String, val legal: Boolean)

/**
 * The form after picking a file, styled like Form Obat: "Dokumen", "Nama", the "Keinginan & hukum" switch (only for
 * whoever sees both categories; [legalOnly]: sees just that one), "Simpan".
 */
@Composable
fun DocFormScreen(picked: String, canChoose: Boolean, legalOnly: Boolean, onBack: () -> Unit, save: suspend (DocForm) -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(picked) }
    var legal by rememberSaveable { mutableStateOf(legalOnly) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.rec_docs), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.med_name)) { BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        if (canChoose) Toggle(stringResource(Res.string.cat_wishes), legal) { legal = it }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                if (name.isNotBlank() && !busy) scope.launch {
                    busy = true
                    failed = !save(DocForm(name.trim(), legal))
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}

/**
 * Biaya: [expenses] newest first, [payers] the "Dibayar oleh" chips and bars (v3's SIBS, me first), [person] for
 * anyone who paid. [add] gets what was typed and returns true once saved, clearing the draft.
 */
class Costs(
    val expenses: List<Expense>,
    val payers: Map<String, Person>,
    val me: String,
    val person: (String) -> Person,
    val add: suspend (what: String, amount: Long?, paidBy: String) -> Boolean,
)

private val BarTrack = Color(0xFFEDE7DB)
private val InputLine = Color(0x2422261F) // rgba(34,38,31,.14)

/** v3's Biaya: the note, this month's total with a bar per payer, the list, and "Tambah pengeluaran". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Costs(c: Costs, today: LocalDate) {
    val scope = rememberCoroutineScope()
    var what by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(c.me) }
    // Me until another is picked; whoever left meanwhile falls back too.
    val by = picked.takeIf { it in c.payers } ?: c.me.takeIf { it in c.payers } ?: c.payers.keys.firstOrNull().orEmpty()
    var busy by remember { mutableStateOf(false) }
    val month = c.expenses.filter { it.day.year == today.year && it.day.month == today.month }
    val sums = c.payers.keys.associateWith { id -> month.filter { it.paidBy == id }.sumOf { it.amount } }
    val max = maxOf(1L, sums.values.maxOrNull() ?: 0L)
    val field = LocalTextStyle.current.copy(fontSize = 15.sp, color = Kf.Ink)
    // design: gap 12
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.costs_note), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        // design: #FBF8F2, r18, p16, gap 12
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(soFar(today), Modifier.alignByBaseline(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(rupiah(month.sumOf { it.amount }), Modifier.alignByBaseline(), style = serifStyle(24f))
            }
            // design: grid 44px 1fr 56px, gap 10, 13px; track h8 r4 #EDE7DB. A column each, so the bars line up.
            // The amount column is as wide as its widest, at least 56px: "Rp 250.000" doesn't fit 56 (Rupiah deviation).
            @Composable fun Cell(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
                Box(modifier.height(16.dp), contentAlignment = Alignment.CenterStart) { content() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(44.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.payers.values.forEach { Cell { Text(it.name, fontSize = 13.sp, maxLines = 1) } }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.payers.forEach { (id, p) ->
                        Cell {
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(BarTrack)) {
                                Box(Modifier.fillMaxWidth(sums.getValue(id).toFloat() / max).fillMaxHeight().background(p.color))
                            }
                        }
                    }
                }
                Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.payers.keys.forEach { id ->
                        Cell(Modifier.fillMaxWidth()) {
                            Text(rupiah(sums.getValue(id)), Modifier.fillMaxWidth(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        if (c.expenses.isNotEmpty()) Card {
            c.expenses.forEach { e ->
                val p = c.person(e.paidBy)
                // design: padding 12px 16px, gap 12, border-bottom rgba(34,38,31,.07)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.initial, p.color, 30.dp, 12.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.what, fontSize = 15.sp)
                        Text("${p.name} · ${if (e.day == today) stringResource(Res.string.today) else dayMonth(e.day)}", fontSize = 12.sp, color = Kf.Muted)
                    }
                    Text(rupiah(e.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Hairline()
            }
        }
        // design: #FBF8F2, r18, p16, gap 12
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.add_expense), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            // design: inputs h46, 1px rgba(34,38,31,.14), r12, #fff, padding 0 12px, 15px; the amount 80px wide, content-box so 106 with padding and border
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputBox(Modifier.weight(1f)) { HintedInput(what, { what = it }, stringResource(Res.string.what_ph), field, singleLine = true) }
                InputBox(Modifier.width(106.dp)) {
                    BasicTextField(
                        amount, { amount = it }, Modifier.fillMaxWidth(), textStyle = field, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { f -> Box { if (amount.isEmpty()) Text("Rp", style = field.copy(color = Kf.Muted)); f() } },
                    )
                }
            }
            Text(stringResource(Res.string.paid_by), fontSize = 12.sp, color = Kf.Muted)
            // design: wrap, gap 6; chips 1px border outside padding 8px 14px, r999, 14px; picked in the payer's colour
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.payers.forEach { (id, p) ->
                    val on = id == by
                    Text(
                        p.name, color = if (on) Color.White else Kf.Ink, fontSize = 14.sp,
                        modifier = Modifier.background(if (on) p.color else Color.Transparent, CircleShape)
                            .border(1.dp, if (on) p.color else Kf.InputBorder, CircleShape).tap { picked = id }.padding(horizontal = 15.dp, vertical = 9.dp),
                    )
                }
            }
            // design: h46, r12, #22261F, 14px 600
            Box(
                Modifier.fillMaxWidth().height(46.dp).background(Kf.Ink, RoundedCornerShape(12.dp)).tap {
                    if (!busy) scope.launch {
                        busy = true
                        if (c.add(what, parseRupiah(amount), by)) { what = ""; amount = "" }
                        busy = false
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.save), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** `costs` input: height 46 plus its 1px border (`<input>` is content-box). */
@Composable
private fun InputBox(modifier: Modifier, input: @Composable () -> Unit) = Box(
    modifier.height(48.dp).background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, InputLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
    contentAlignment = Alignment.CenterStart,
) { input() }

private val Tan = Color(0xFFC9A77C)

/**
 * v3's Kondisi: blood pressure on a 60–160 scale with the dashed 140 line, and a 30-column grid per habit filled
 * from the right. Owner-approved in #25: the first Check-in's day ("31 Agu") under the grid, the empty box before
 * any. The "Bawa ke dokter" button waits for Export.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trends(checkIns: List<CheckIn>, bring: Pair<String, () -> Unit>?) {
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
            Habit(stringResource(Res.string.habit_ate), t.ate, checkIns) { when (it.ate) { Ate.yes -> Kf.Green; Ate.some -> Tan; Ate.no -> Kf.Rust } }
            Habit(stringResource(Res.string.habit_walked), t.walked, checkIns) { if (it.walked) Kf.Green else Kf.Sand }
            Habit(stringResource(Res.string.ci_mood), t.good, checkIns) { when (it.mood) { Mood.good -> Kf.Green; Mood.okay -> Tan; Mood.low -> Kf.Rust } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayMonth(checkIns.first().day), fontSize = 11.sp, color = Kf.Muted)
                Text(stringResource(Res.string.today), fontSize = 11.sp, color = Kf.Muted)
            }
        }
        // design: h50, 1px rgba(34,38,31,.18), r16, transparent, 15px 600
        bring?.let { (provider, attach) ->
            Box(Modifier.fillMaxWidth().height(50.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(16.dp)).tap(attach), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.bring_to_dr, provider), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
