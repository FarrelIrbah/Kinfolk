package id.kinfolk.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Task
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.DashedButton
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Labeled
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.appointment.DayPicker
import id.kinfolk.ui.appointment.shortDate
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_task
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.remind
import kinfolk.shared.generated.resources.reminded
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.task_added
import kinfolk.shared.generated.resources.task_due
import kinfolk.shared.generated.resources.task_from
import kinfolk.shared.generated.resources.task_owner
import kinfolk.shared.generated.resources.task_refill
import kinfolk.shared.generated.resources.task_text
import kinfolk.shared.generated.resources.tasks_done
import kinfolk.shared.generated.resources.tasks_open
import kinfolk.shared.generated.resources.tasks_sub
import kinfolk.shared.generated.resources.tasks_title
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * v3 `tasks`: open Tasks, then "Selesai". [person] names a Member (yourself too, like `rotation`); "Ingatkan" only on
 * someone else's open Task. The dashed "+ Tambah tugas" and tapping a Task to change it are approved in #26.
 */
@Composable
fun TasksScreen(
    tasks: List<Task>,
    today: LocalDate,
    me: String,
    person: (String) -> Person?,
    canRemind: (Task) -> Boolean,
    onBack: () -> Unit,
    onToggle: (Task) -> Unit,
    onRemind: (Task) -> Unit,
    onOpen: (Task?) -> Unit,
) {
    val open = tasks.filter { !it.done }
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt }
    // design: padding 4px 20px, gap 14
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.tasks_title), style = serifStyle(30f, 1.1f))
            Text(stringResource(Res.string.tasks_sub), fontSize = 14.sp, lineHeight = (14 * 1.45).sp, color = Kf.Muted)
        }
        Box(Modifier.padding(top = 6.dp)) { SectionLabel(stringResource(Res.string.tasks_open, open.size)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            open.forEach { TaskRow(it, today, me, person, canRemind(it), onToggle, onRemind, onOpen) }
        }
        DashedButton(stringResource(Res.string.add_task)) { onOpen(null) }
        // ponytail: every done Task, ever; keep the last month's when the list gets long.
        if (done.isNotEmpty()) {
            Box(Modifier.padding(top = 6.dp)) { SectionLabel(stringResource(Res.string.tasks_done)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                done.forEach { TaskRow(it, today, me, person, false, onToggle, onRemind, onOpen) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    t: Task, today: LocalDate, me: String, person: (String) -> Person?, canRemind: Boolean,
    onToggle: (Task) -> Unit, onRemind: (Task) -> Unit, onOpen: (Task?) -> Unit,
) {
    val owner = person(t.ownerId)
    val reminded = t.remindedAt != null
    // design: #FBF8F2, radius 16, padding 14, gap 12, opacity .6 when done
    Row(
        Modifier.fillMaxWidth().alpha(if (t.done) .6f else 1f).background(Kf.Card, RoundedCornerShape(16.dp)).tap { onOpen(t) }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // design: 28x28, radius 8, 1.5px (rendered 1px) rgba(34,38,31,.3) / filled #2F5D4A with "✓", margin-top 1
        Box(
            Modifier.padding(top = 1.dp).size(28.dp)
                .background(if (t.done) Kf.Green else Color.Transparent, RoundedCornerShape(8.dp))
                .border(1.dp, if (t.done) Kf.Green else Color(0x4D22261F), RoundedCornerShape(8.dp))
                .tap { onToggle(t) },
            contentAlignment = Alignment.Center,
        ) { if (t.done) Text("✓", color = Color.White, fontSize = 15.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.text, fontSize = 15.sp, lineHeight = (15 * 1.4).sp, textDecoration = if (t.done) TextDecoration.LineThrough else null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                owner?.let { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.initial, p.color, 18.dp, 9.sp)
                        Text(p.name, fontSize = 12.sp)
                    }
                }
                Text(
                    dueLabel(t.due, today), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (t.due < today && !t.done) Kf.Sos else Kf.Muted,
                )
                Text(
                    when (t.source) {
                        Task.Source.step -> stringResource(Res.string.task_from, fromWhat(t.fromTitle.orEmpty()))
                        Task.Source.refill -> stringResource(Res.string.task_refill)
                        Task.Source.added -> stringResource(Res.string.task_added, person(t.addedBy)?.name.orEmpty())
                    },
                    fontSize = 12.sp, color = Kf.Muted,
                )
            }
        }
        // design: height 32, padding 0 12, 1px rgba(34,38,31,.16), 12/600, green until reminded
        if (canRemind && !t.done && t.ownerId != me) Box(
            Modifier.height(32.dp).border(1.dp, Color(0x2922261F), CircleShape).tap { if (!reminded) onRemind(t) }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (reminded) Res.string.reminded else Res.string.remind),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (reminded) Kf.Muted else Kf.Green,
            )
        }
    }
}

class TaskForm(val text: String, val owner: String, val due: LocalDate)

/** The Task form (approved in #26): like the Medication form, "Tugas", "Pemilik", "Tenggat", "Simpan". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskFormScreen(editing: Task?, people: Map<String, Person>, me: String, defaultDue: LocalDate, onBack: () -> Unit, save: suspend (TaskForm) -> Boolean) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(editing?.text.orEmpty()) }
    var owner by rememberSaveable { mutableStateOf(editing?.ownerId ?: me) }
    var due by rememberSaveable { mutableStateOf((editing?.due ?: defaultDue).toString()) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.tasks_title), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.task_text), multiline = true) {
            BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = input.copy(lineHeight = (17 * 1.5).sp))
        }
        Labeled(stringResource(Res.string.task_owner)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { (id, p) -> PickChip(p.name, id == owner) { owner = id } }
            }
        }
        Field(stringResource(Res.string.task_due), Modifier.tap { picking = true }) {
            Text(shortDate(LocalDate.parse(due)), fontSize = 17.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                if (text.isNotBlank() && !busy) scope.launch {
                    busy = true
                    failed = !save(TaskForm(text.trim(), owner, LocalDate.parse(due)))
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
    if (picking) DayPicker(LocalDate.parse(due), { due = it.toString(); picking = false }) { picking = false }
}
