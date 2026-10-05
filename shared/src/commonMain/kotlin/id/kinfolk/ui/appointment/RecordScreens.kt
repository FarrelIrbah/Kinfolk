package id.kinfolk.ui.appointment

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.DoseChange
import id.kinfolk.data.Moved
import id.kinfolk.data.NextStepDraft
import id.kinfolk.data.Question
import id.kinfolk.data.Transcript
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Kf
import id.kinfolk.ui.KfEase
import id.kinfolk.ui.LocalReduceMotion
import id.kinfolk.ui.Pill
import id.kinfolk.ui.Recorder
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.dashed
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.asked_answered
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.back_home
import kinfolk.shared.generated.resources.check_yellow_first
import kinfolk.shared.generated.resources.collapse
import kinfolk.shared.generated.resources.confirm_flagged
import kinfolk.shared.generated.resources.consent_head
import kinfolk.shared.generated.resources.consent_note
import kinfolk.shared.generated.resources.consent_sub
import kinfolk.shared.generated.resources.expand
import kinfolk.shared.generated.resources.looks_right
import kinfolk.shared.generated.resources.mc_status_done
import kinfolk.shared.generated.resources.mc_status_pending
import kinfolk.shared.generated.resources.med_change
import kinfolk.shared.generated.resources.move_next
import kinfolk.shared.generated.resources.next_steps_reassign
import kinfolk.shared.generated.resources.not_answered
import kinfolk.shared.generated.resources.processing_head
import kinfolk.shared.generated.resources.processing_sub
import kinfolk.shared.generated.resources.rec_pause
import kinfolk.shared.generated.resources.rec_paused
import kinfolk.shared.generated.resources.rec_recording
import kinfolk.shared.generated.resources.rec_resume
import kinfolk.shared.generated.resources.rec_stop
import kinfolk.shared.generated.resources.self_hosted
import kinfolk.shared.generated.resources.share_circle
import kinfolk.shared.generated.resources.start_recording
import kinfolk.shared.generated.resources.transcript
import kinfolk.shared.generated.resources.transcript_hint
import kinfolk.shared.generated.resources.transcript_hint_sel
import kinfolk.shared.generated.resources.unans_why
import kinfolk.shared.generated.resources.verify_hint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

// v3 `consent`, `recording`, `processing` and `summary` with its transcript drawer (#46, docs/screen-map.md).

/** "00:41": [t] seconds into the Recording. */
fun stamp(t: Double): String = t.toInt().let { "${(it / 60).toString().padStart(2, '0')}:${(it % 60).toString().padStart(2, '0')}" }

/** One person in the room: `consent` row. */
class InRoom(val name: String, val role: String)

/** v3 `consent`, you last and already ticked. [onStart] runs once everyone is ticked; before that, [onNotAll] (v3's toast). */
@Composable
fun ConsentScreen(people: List<InRoom>, onBack: () -> Unit, onNotAll: () -> Unit, onStart: () -> Unit) {
    val ticked = remember { mutableStateListOf(*Array(people.size) { it == people.lastIndex }) }
    val all = ticked.all { it }
    // design: padding:4px 20px; gap:22px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.consent_head), style = serifStyle(30f, 1.1f))
            Text(stringResource(Res.string.consent_sub), fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kf.Card)) {
            people.forEachIndexed { i, p ->
                val on = ticked[i]
                Row(
                    Modifier.fillMaxWidth().tap { ticked[i] = !on }.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).background(if (on) Kf.Green else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.5.dp, if (on) Kf.Green else Color(0x4D22261F), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Text("✓", color = Color.White, fontSize = 15.sp) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(p.role, fontSize = 13.sp, color = Kf.Muted)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.Hairline)) // border-bottom on every row
            }
        }
        Text(stringResource(Res.string.consent_note), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        Box(
            Modifier.fillMaxWidth().height(54.dp).background(if (all) Kf.Green else Color(0xFFA9ADA2), RoundedCornerShape(16.dp))
                .tap { if (all) onStart() else onNotAll() },
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(Res.string.start_recording), color = Kf.Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * v3 `recording`, without "Ditranskripsi sambil berjalan" (approved by the owner in #46: the worker transcribes after
 * Stop). The waveform is the microphone's level. [onStop] gets how long was recorded, pauses left out.
 */
@Composable
fun RecordingScreen(recorder: Recorder, onStop: (seconds: Int) -> Unit) {
    var paused by remember { mutableStateOf(false) }
    var ms by remember { mutableStateOf(0L) }
    val levels = remember { mutableStateListOf(*Array(36) { 0f }) }
    // Wall-clock, so it keeps counting while the screen is off and frames stop.
    LaunchedEffect(Unit) {
        var last = Clock.System.now()
        while (true) {
            delay(120)
            val t = Clock.System.now()
            if (!paused) { ms += (t - last).inWholeMilliseconds; levels.removeAt(0); levels.add(recorder.level()) }
            last = t
        }
    }
    val pulse = if (LocalReduceMotion.current) 1f else rememberInfiniteTransition().animateFloat(
        1f, .35f, infiniteRepeatable(tween(600), RepeatMode.Reverse),
    ).value
    val secs = (ms / 1000).toInt()
    // design: padding:8px 20px; gap:22px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(pulse).background(Kf.Sos, CircleShape))
            Text(stringResource(if (paused) Res.string.rec_paused else Res.string.rec_recording), color = Kf.Sos, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stamp(secs.toDouble()), style = serifStyle(64f, 1f, weight = 400).copy(letterSpacing = (-.02).em))
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            levels.forEachIndexed { i, l ->
                val v = if (paused) .12f else .25f + .75f * l.coerceIn(0f, 1f)
                Box(Modifier.weight(1f).height(maxOf(4, (v * 60).roundToInt()).dp).alpha(if (i > 30) .35f else 1f).background(Kf.Green, RoundedCornerShape(2.dp)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(54.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(16.dp)).tap {
                    if (paused) recorder.resume() else recorder.pause()
                    paused = !paused
                },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(if (paused) Res.string.rec_resume else Res.string.rec_pause), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(54.dp).background(Kf.Sos, RoundedCornerShape(16.dp)).tap { onStop(secs) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.rec_stop), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** v3 `processing`, with the real length: "Mentranskripsi 4 menit audio, …" (at least 1). */
@Composable
fun ProcessingScreen(seconds: Int) {
    val turn = if (LocalReduceMotion.current) 0f else rememberInfiniteTransition().animateFloat(
        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)),
    ).value
    // design: min-height:700px; justify-content:center; gap:14px; padding:0 32px
    Column(Modifier.fillMaxWidth().heightIn(min = 700.dp).padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        // 48px circle, 3px #D6CEBF border with a #2F5D4A top.
        Canvas(Modifier.size(48.dp).rotate(turn)) {
            val w = 3.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            drawArc(Kf.Line, 0f, 360f, false, Offset(w / 2, w / 2), box, style = Stroke(w))
            drawArc(Kf.Green, -135f, 90f, false, Offset(w / 2, w / 2), box, style = Stroke(w))
        }
        Text(stringResource(Res.string.processing_head), style = serifStyle(28f, 1.15f))
        Text(stringResource(Res.string.processing_sub, ((seconds + 30) / 60).coerceAtLeast(1)), fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
    }
}

/** What the summary has selected (v3 `sel`): its key and the transcript lines behind it; the drawer follows it. */
class Selection {
    var key by mutableStateOf<String?>(null)
    var refs by mutableStateOf(emptyList<Int>())
    var expanded by mutableStateOf(false)
    fun toggle(k: String, lines: List<Int>) { if (key == k) { key = null; refs = emptyList() } else { key = k; refs = lines } }
}

/**
 * v3 `summary` of a Recording. [editable]: the Attendee before sharing, who reassigns owners, confirms the yellow
 * lines in [unchecked] ([check], #47), moves unanswered Questions ([move], null without a next visit) and shares
 * ([share] gets the steps, false when unreachable; locked while a line is unchecked, [onToast] then says why).
 * After sharing [shared] is the button's "Dibagikan · …" label; others see no button. [dose]: "Perubahan obat".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(
    meta: String,
    title: String,
    transcript: Transcript,
    steps: List<NextStepDraft>,
    unanswered: List<Question>,
    editable: Boolean,
    shared: String?,
    sel: Selection,
    unchecked: List<String>,
    dose: DoseChange?,
    owners: List<String>,
    person: (String) -> Person?,
    askerColor: (String) -> Color,
    onHome: () -> Unit,
    move: (suspend (Question) -> Moved?)?,
    movedLabel: (Moved) -> String,
    share: suspend (List<NextStepDraft>) -> Boolean,
    check: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val owned = remember(steps) { mutableStateListOf(*steps.toTypedArray()) }
    val moved = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }
    val times = { refs: List<Int> -> refs.mapNotNull { transcript.segments.getOrNull(it)?.let { s -> stamp(s.t) } } }
    val flags = if (editable) unchecked else emptyList()
    // design: border #2F5D4A selected, else #E0C060 while flagged; the yellow "Cek: …" box under it (gap 10)
    @Composable fun Line(key: String, note: String?, content: @Composable () -> Unit) = Column(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(16.dp))
            .border(1.5.dp, when { sel.key == key -> Kf.Green; key in flags -> FlagLine; else -> Color.Transparent }, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        if (key in flags && note != null) Row(
            Modifier.fillMaxWidth().background(Kf.FlagBg, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(note, Modifier.weight(1f), fontSize = 13.sp, lineHeight = (13 * 1.4).sp, color = Kf.FlagInk)
            Box(Modifier.height(32.dp).background(Kf.Ink, CircleShape).tap { check(key) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.looks_right), color = Kf.Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // design: padding:4px 20px 0; gap:18px
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(Res.string.back_home), onHome)
            Text(stringResource(Res.string.self_hosted), fontSize = 12.sp, color = Kf.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meta, fontSize = 13.sp, color = Kf.Muted)
            Text(title, style = serifStyle(28f, 1.1f))
        }
        Text(
            stringResource(Res.string.verify_hint), Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            fontSize = 13.sp, lineHeight = (13 * 1.45).sp, color = Kf.Ink2,
        )
        if (transcript.qa.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(Res.string.asked_answered))
            transcript.qa.forEachIndexed { i, q ->
                Line("q$i", q.check) {
                    Column(Modifier.fillMaxWidth().tap { sel.toggle("q$i", q.segments); sel.expanded = false }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(q.question, fontSize = 14.sp, lineHeight = (14 * 1.4).sp, color = Kf.Muted)
                        Text(q.answer, fontSize = 15.sp, lineHeight = (15 * 1.45).sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            times(q.segments).forEach { t ->
                                Text(t, Modifier.background(Kf.Sand, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kf.Ink2)
                            }
                        }
                    }
                }
            }
        }
        if (dose != null || owned.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dose != null) {
                SectionLabel(stringResource(Res.string.med_change))
                val refs = listOfNotNull(transcript.medication?.segment)
                Column(
                    Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(16.dp))
                        .border(1.5.dp, if (sel.key == "mc") Kf.Green else Color.Transparent, RoundedCornerShape(16.dp))
                        .tap { sel.toggle("mc", refs); sel.expanded = false }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DoseLine(dose, 15, Kf.Green)
                    Text(if (dose.applied) stringResource(Res.string.mc_status_done, dose.toDose) else stringResource(Res.string.mc_status_pending), fontSize = 13.sp, color = Kf.Ink2)
                    dose.t?.let { Text(stamp(it), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kf.Muted) }
                }
            }
            // design: margin-top:10px under "Perubahan obat"
            if (owned.isNotEmpty()) Box(Modifier.padding(top = if (dose != null) 10.dp else 0.dp)) { SectionLabel(stringResource(Res.string.next_steps_reassign)) }
            owned.forEachIndexed { i, step ->
                val refs = transcript.steps.getOrNull(i)?.segments.orEmpty()
                Line("n$i", transcript.steps.getOrNull(i)?.check) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).tap { sel.toggle("n$i", refs) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(step.text, fontSize = 15.sp, lineHeight = (15 * 1.4).sp)
                            times(refs).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", "), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kf.Muted) }
                        }
                        val owner = person(step.owner)
                        Box(
                            Modifier.height(32.dp).background(owner?.color ?: Kf.Muted, CircleShape)
                                .let { m -> if (editable && owners.isNotEmpty()) m.tap { owned[i] = step.copy(owner = owners[(owners.indexOf(step.owner) + 1) % owners.size]) } else m }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(owner?.name.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        if (unanswered.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(Res.string.not_answered))
            unanswered.forEach { q ->
                Column(Modifier.fillMaxWidth().dashed().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Avatar(q.askedByName.orEmpty().take(1), askerColor(q.askedBy), 26.dp, 11.sp)
                        Text(q.text, fontSize = 15.sp, lineHeight = (15 * 1.4).sp)
                    }
                    Text(stringResource(Res.string.unans_why, person(q.askedBy)?.name ?: q.askedByName.orEmpty()), fontSize = 13.sp, lineHeight = (13 * 1.4).sp, color = Kf.Muted)
                    if (editable && move != null) {
                        val done = moved[q.id]
                        Box(
                            Modifier.height(36.dp).background(if (done != null) Kf.GreenTint else Kf.Ink, CircleShape).tap {
                                if (done != null || busy) return@tap
                                scope.launch { busy = true; move(q)?.let { moved[q.id] = movedLabel(it) }; busy = false }
                            }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(done ?: stringResource(Res.string.move_next), color = if (done != null) Kf.Green else Kf.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        val locked = shared == null && flags.isNotEmpty()
        val checkFirst = stringResource(Res.string.check_yellow_first)
        if (editable || shared != null) Box(
            Modifier.fillMaxWidth().height(52.dp).background(when { shared != null -> Kf.Muted; locked -> Locked; else -> Kf.Green }, RoundedCornerShape(16.dp)).tap {
                if (shared != null || busy) return@tap
                if (locked) return@tap onToast(checkFirst)
                scope.launch { busy = true; share(owned.toList()); busy = false }
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shared ?: if (locked) stringResource(Res.string.confirm_flagged, flags.size) else stringResource(Res.string.share_circle),
                color = Kf.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(220.dp)) // v3 bottomPad 250 on `summary`: room under the drawer
    }
}

private val FlagLine = Color(0xFFE0C060)
private val Locked = Color(0xFFA9ADA2)

/** "Amlodipine ~~5 mg~~ → 10 mg" in [size]sp, the new dose in [toColor]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoseLine(d: DoseChange, size: Int, toColor: Color) {
    // design: display:flex; align-items:baseline; gap:8px; flex-wrap:wrap
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(d.name, Modifier.alignByBaseline(), fontSize = size.sp, fontWeight = FontWeight.SemiBold)
        Text(d.fromDose, Modifier.alignByBaseline(), fontSize = size.sp, color = Kf.Muted, textDecoration = TextDecoration.LineThrough)
        Text("→", Modifier.alignByBaseline(), fontSize = size.sp)
        Text(d.toDose, Modifier.alignByBaseline(), fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = toColor)
    }
}

/** v3 TRANSCRIPT DRAWER over `summary`: #22261F, radius 24 on top, 120 or 430 tall (.28s). */
@Composable
fun TranscriptDrawer(transcript: Transcript, speaker: (Transcript.Segment) -> String, sel: Selection, modifier: Modifier) {
    val list = rememberLazyListState()
    val inset = with(LocalDensity.current) { 4.dp.roundToPx() }
    LaunchedEffect(sel.key) { sel.refs.firstOrNull()?.let { list.animateScrollToItem(it, -inset) } }
    val reduce = LocalReduceMotion.current
    val h by animateDpAsState(if (sel.expanded) 430.dp else 120.dp, if (reduce) tween(0) else tween(280, easing = KfEase))
    Column(
        modifier.fillMaxWidth()
            .dropShadow(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), Shadow(32.dp, Color(0x3822261F), offset = DpOffset(0.dp, (-12).dp)))
            .background(Kf.Ink, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().tap { sel.expanded = !sel.expanded }.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(40.dp).height(5.dp).background(Color(0x4DF3EEE4), RoundedCornerShape(3.dp)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.transcript).uppercase(), Modifier.alpha(.7f), color = Kf.Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .08.em)
                    Text(stringResource(if (sel.key != null) Res.string.transcript_hint_sel else Res.string.transcript_hint), Modifier.alpha(.55f), color = Kf.Paper, fontSize = 12.sp)
                }
                Box(Modifier.height(30.dp).background(Color(0x1FF3EEE4), CircleShape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (sel.expanded) Res.string.collapse else Res.string.expand), color = Kf.Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().height(h), list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(transcript.segments) { i, s ->
                val on = i in sel.refs
                Column(
                    Modifier.fillMaxWidth().background(if (on) Color(0x47C9A77C) else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("${stamp(s.t)} · ${speaker(s)}", Modifier.alpha(.65f), color = Kf.Paper, fontSize = 11.sp)
                    Text(s.text, Modifier.alpha(if (sel.key == null || on) 1f else .55f), color = Kf.Paper, fontSize = 14.sp, lineHeight = (14 * 1.45).sp)
                }
            }
        }
    }
}
