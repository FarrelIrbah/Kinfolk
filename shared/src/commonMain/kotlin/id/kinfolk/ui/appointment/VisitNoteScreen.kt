package id.kinfolk.ui.appointment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Appointment
import id.kinfolk.data.Question
import id.kinfolk.data.VisitNote
import id.kinfolk.ui.AddRow
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.HintedInput
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.dashed
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add
import kinfolk.shared.generated.resources.add_step
import kinfolk.shared.generated.resources.answer_hint
import kinfolk.shared.generated.resources.asked_answered
import kinfolk.shared.generated.resources.back_home
import kinfolk.shared.generated.resources.carried_from
import kinfolk.shared.generated.resources.carried_to
import kinfolk.shared.generated.resources.next_steps
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.not_answered
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.tab_records
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * `summary` from design v3 as a hand-written Visit Note (approved in #7, docs/screen-map.md): the Attendee gets the
 * form, everyone else reads it. [save] returns false when the server couldn't be reached.
 */
@Composable
fun VisitNoteScreen(
    a: Appointment,
    questions: List<Question>,
    note: VisitNote?,
    editable: Boolean,
    recipientName: String,
    now: Instant,
    tz: TimeZone,
    askerColor: (String) -> Color,
    onHome: () -> Unit,
    save: suspend (answers: Map<String, String>, nextSteps: List<String>, notes: String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val answers = remember { mutableStateMapOf(*questions.map { it.id to it.answer.orEmpty() }.toTypedArray()) }
    val steps = remember { mutableStateListOf(*note?.nextSteps.orEmpty().toTypedArray()) }
    var draftStep by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(note?.notes.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val text15 = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = (15 * 1.45).sp, color = Kf.Ink)
    val carriedFrom = @Composable { q: Question ->
        if (q.carried) Text(
            stringResource(Res.string.carried_from, dayMonth(q.askedFor.toLocalDateTime(tz).date)),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kf.Muted,
        )
    }

    // design: padding:4px 20px 0; gap:18px
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Pill(stringResource(Res.string.back_home), onHome)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${dayLabel(a.startsAt, now, tz)} · ${a.provider.name}", fontSize = 13.sp, color = Kf.Muted)
            Text(a.title, style = serifStyle(28f, 1.1f))
        }

        val asked = if (editable) questions else questions.filter { !it.answer.isNullOrBlank() }
        if (asked.isNotEmpty()) Section(stringResource(Res.string.asked_answered)) {
            asked.forEach { q ->
                NoteCard(Arrangement.spacedBy(8.dp)) {
                    Text(q.text, fontSize = 14.sp, lineHeight = (14 * 1.4).sp, color = Kf.Muted)
                    if (editable) HintedInput(answers[q.id].orEmpty(), { answers[q.id] = it }, stringResource(Res.string.answer_hint), text15)
                    else Text(q.answer.orEmpty(), style = text15)
                    carriedFrom(q)
                }
            }
        }

        if (editable || steps.isNotEmpty()) Section(stringResource(Res.string.next_steps)) {
            steps.forEachIndexed { i, step ->
                NoteCard {
                    // Emptying a step removes it on save.
                    if (editable) HintedInput(step, { steps[i] = it }, "", text15.copy(lineHeight = (15 * 1.4).sp))
                    else Text(step, style = text15.copy(lineHeight = (15 * 1.4).sp))
                }
            }
            if (editable) AddRow(draftStep, { draftStep = it }, stringResource(Res.string.add_step), stringResource(Res.string.add)) {
                if (draftStep.isNotBlank()) { steps += draftStep.trim(); draftStep = "" }
            }
        }

        val open = if (editable) emptyList() else questions.filter { it.answer?.isBlank() == true }
        if (open.isNotEmpty()) Section(stringResource(Res.string.not_answered)) {
            open.forEach { q ->
                Column(Modifier.fillMaxWidth().dashed().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(q.askedByName.orEmpty().take(1), askerColor(q.askedBy), 26.dp, 11.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(q.text, fontSize = 15.sp, lineHeight = (15 * 1.4).sp)
                            carriedFrom(q)
                        }
                    }
                    Text(stringResource(Res.string.carried_to, recipientName, a.provider.name), fontSize = 13.sp, lineHeight = (13 * 1.4).sp, color = Kf.Muted)
                }
            }
        }

        if (editable || notes.isNotBlank()) Section(stringResource(Res.string.tab_records)) {
            NoteCard {
                if (editable) HintedInput(notes, { notes = it }, "", text15)
                else Text(notes, style = text15)
            }
        }

        if (editable) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(52.dp).background(Kf.Green, RoundedCornerShape(16.dp)).tap {
                    if (busy) return@tap
                    scope.launch {
                        busy = true
                        val kept = steps.map { it.trim() }.filter { it.isNotEmpty() } + listOfNotNull(draftStep.trim().ifEmpty { null })
                        failed = !save(answers.mapValues { it.value.trim() }, kept, notes.trim())
                        busy = false
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.save), color = Kf.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionLabel(label); content() }

/** `summary` line card: #FBF8F2, radius 16, padding 14 (its 1.5px border is transparent until selected). */
@Composable
private fun NoteCard(arrangement: Arrangement.Vertical = Arrangement.Top, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(16.dp)).padding(15.5.dp), verticalArrangement = arrangement) { content() }
