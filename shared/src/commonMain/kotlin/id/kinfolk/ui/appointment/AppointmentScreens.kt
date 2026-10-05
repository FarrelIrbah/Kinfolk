package id.kinfolk.ui.appointment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Appointment
import id.kinfolk.data.Provider
import id.kinfolk.data.Question
import id.kinfolk.ui.AddRow
import id.kinfolk.ui.Card
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Labeled
import id.kinfolk.ui.Link
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.appt_form_head
import kinfolk.shared.generated.resources.notes_by_hand
import kinfolk.shared.generated.resources.record_this
import kinfolk.shared.generated.resources.record_this_sub
import kinfolk.shared.generated.resources.record_visit
import kinfolk.shared.generated.resources.visit_note_card_sub
import kinfolk.shared.generated.resources.visit_note_card
import kinfolk.shared.generated.resources.questions_to_ask
import kinfolk.shared.generated.resources.open_summary
import kinfolk.shared.generated.resources.from_all
import kinfolk.shared.generated.resources.carried_from
import kinfolk.shared.generated.resources.add_question
import kinfolk.shared.generated.resources.add
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.bring
import kinfolk.shared.generated.resources.cancel_appt
import kinfolk.shared.generated.resources.date
import kinfolk.shared.generated.resources.departs
import kinfolk.shared.generated.resources.doctor
import kinfolk.shared.generated.resources.driver
import kinfolk.shared.generated.resources.edit
import kinfolk.shared.generated.resources.in_room
import kinfolk.shared.generated.resources.new_doctor
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.nobody
import kinfolk.shared.generated.resources.place
import kinfolk.shared.generated.resources.purpose
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.time
import kinfolk.shared.generated.resources.you
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * `appt` from design v3 (docs/screen-map.md). The Attendee gets v3's record card (#46), with "Catat manual saja" for the
 * hand-written Visit Note (#7) until something is recorded; [summaryReady]: a Recording's summary I can read. Once a
 * hand-written Visit Note is saved, everyone gets its card ("Buka ringkasan"). [ask] returns false when unreachable.
 */
@Composable
fun ApptScreen(
    a: Appointment,
    questions: List<Question>,
    noteReady: Boolean,
    now: Instant,
    tz: TimeZone,
    person: (String?) -> Person?,
    askerColor: (String) -> Color,
    isAttendee: Boolean,
    recipientName: String,
    recorded: Boolean,
    summaryReady: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    ask: suspend (String) -> Boolean,
    onNote: () -> Unit,
    onRecord: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Pill(stringResource(Res.string.back), onBack)
            Pill(stringResource(Res.string.edit), onEdit)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(whenLabel(a.startsAt, now, tz), fontSize = 13.sp, color = Kf.Muted)
            Text(a.title, style = serifStyle(30f, 1.1f))
            Text(a.withWhom(), fontSize = 15.sp, color = Kf.Ink2)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val driver = person(a.driverId)
            RoleCard(stringResource(Res.string.driver), Modifier.weight(1f)) {
                if (driver == null) Text(stringResource(Res.string.nobody), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(driver.initial, driver.color, 24.dp, 11.sp)
                    Text(driver.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // design: "Sri, Bapak" — the Attendee, then the Care Recipient.
            val room = person(a.attendeeId)?.name?.let { "$it, $recipientName" } ?: stringResource(Res.string.nobody)
            RoleCard(stringResource(Res.string.in_room), Modifier.weight(1f)) {
                Text(room, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.questions_to_ask), style = serifStyle(20f), modifier = Modifier.alignByBaseline())
                Text(stringResource(Res.string.from_all), fontSize = 13.sp, color = Kf.Muted, modifier = Modifier.alignByBaseline())
            }
            Card {
                questions.forEach { q ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(q.askedByName.orEmpty().take(1), askerColor(q.askedBy), 26.dp, 11.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(q.text, fontSize = 15.sp, lineHeight = (15 * 1.4).sp)
                            if (q.carried) Text(
                                stringResource(Res.string.carried_from, dayMonth(q.askedFor.toLocalDateTime(tz).date)),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kf.Muted,
                            )
                        }
                    }
                    Hairline()
                }
                // Once the Visit Note is saved, new Questions belong to the next visit.
                if (!noteReady) AddRow(draft, { draft = it }, stringResource(Res.string.add_question), stringResource(Res.string.add), Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !busy) scope.launch {
                        busy = true
                        failed = !ask(text)
                        if (!failed && draft.trim() == text) draft = ""
                        busy = false
                    }
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
        if (a.bring.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.bring), style = serifStyle(20f))
            Text(a.bring, fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        val record = recorded || (isAttendee && !noteReady)
        if (record || noteReady) Column(
            Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(if (record) Res.string.record_this else Res.string.visit_note_card), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (record) Res.string.record_this_sub else Res.string.visit_note_card_sub), fontSize = 14.sp, lineHeight = (14 * 1.45).sp, color = Kf.Ink2)
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth().height(50.dp).background(Kf.Green, RoundedCornerShape(14.dp)).tap(if (record) onRecord else onNote),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(if (noteReady || summaryReady) Res.string.open_summary else Res.string.record_visit), color = Kf.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            // Approved by the owner in #46: the hand-written Visit Note stays reachable until #48 and #50 give it a home.
            if (record && !recorded) Link(stringResource(Res.string.notes_by_hand), Kf.Green, onNote, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
    }
}

/** "Dr. Anand Rao · Peninsula Neurology, Suite 204" */
fun Appointment.withWhom() = listOfNotNull(provider.name, location).joinToString(" · ")

@Composable
private fun RoleCard(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight().background(Kf.Card, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 12.sp, color = Kf.Muted)
        content()
    }
}

/** What the Appointment form hands back; [providerId] is null when [newProvider] should be added first. */
data class ApptForm(
    val title: String,
    val providerId: String?,
    val newProvider: String,
    val location: String?,
    val startsAt: Instant,
    val departsAt: Instant?,
    val driverId: String?,
    val attendeeId: String?,
    val bring: String,
)

/**
 * Approved deviation (#6): create or edit an Appointment on the onb1 layout. [save] and [cancel] return false
 * when the server couldn't be reached.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ApptFormScreen(
    editing: Appointment?,
    providers: List<Provider>,
    me: String,
    tz: TimeZone,
    onBack: () -> Unit,
    save: suspend (ApptForm) -> Boolean,
    cancel: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val start = editing?.startsAt?.toLocalDateTime(tz)
    var title by rememberSaveable { mutableStateOf(editing?.title.orEmpty()) }
    var providerId by rememberSaveable { mutableStateOf(editing?.provider?.id ?: providers.firstOrNull()?.id) }
    var newProvider by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf(editing?.location.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(start?.date?.toString()) }
    var time by rememberSaveable { mutableStateOf(start?.time?.let { hm(it).replace(".", "") }.orEmpty()) }
    var departs by rememberSaveable { mutableStateOf(editing?.departsAt?.toLocalDateTime(tz)?.time?.let { hm(it).replace(".", "") }.orEmpty()) }
    var driverId by rememberSaveable { mutableStateOf(editing?.driverId) }
    var attendeeId by rememberSaveable { mutableStateOf(editing?.attendeeId) }
    var bring by rememberSaveable { mutableStateOf(editing?.bring.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    fun submit() {
        val day = date?.let(LocalDate::parse) ?: return
        val at = parseHm(time) ?: return
        val leave = if (departs.isEmpty()) null else parseHm(departs) ?: return
        if (title.isBlank() || (providerId == null && newProvider.isBlank()) || busy) return
        scope.launch {
            busy = true
            failed = !save(
                ApptForm(
                    title.trim(), providerId, newProvider.trim(), location.trim().ifEmpty { null },
                    LocalDateTime(day, at).toInstant(tz), leave?.let { LocalDateTime(day, it).toInstant(tz) },
                    driverId, attendeeId, bring.trim(),
                ),
            )
            busy = false
        }
    }

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.appt_form_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.purpose)) {
            BasicTextField(title, { title = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Labeled(stringResource(Res.string.doctor)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { p -> PickChip(p.name, p.id == providerId) { providerId = p.id } }
                    PickChip(stringResource(Res.string.new_doctor), providerId == null) { providerId = null }
                }
            }
            if (providerId == null) Field("") {
                BasicTextField(newProvider, { newProvider = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true)
            }
        }
        Field(stringResource(Res.string.place)) {
            BasicTextField(location, { location = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(Res.string.date), Modifier.weight(1f).tap { picking = true }) {
                Text(date?.let { shortDate(LocalDate.parse(it)) }.orEmpty(), fontSize = 17.sp)
            }
            Field(stringResource(Res.string.time), Modifier.weight(1f)) { TimeInput(time, { time = it }) }
        }
        Field(stringResource(Res.string.departs)) { TimeInput(departs, { departs = it }) }
        MemberPick(stringResource(Res.string.driver), driverId, me) { driverId = it }
        MemberPick(stringResource(Res.string.in_room), attendeeId, me) { attendeeId = it }
        Field(stringResource(Res.string.bring), multiline = true) {
            BasicTextField(bring, { bring = it }, Modifier.fillMaxWidth(), textStyle = input.copy(lineHeight = (17 * 1.5).sp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) { submit() }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
            if (editing != null) Link(stringResource(Res.string.cancel_appt), Kf.Sos, {
                if (!busy) scope.launch { busy = true; failed = !cancel(); busy = false }
            }, Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (picking) DayPicker(date?.let(LocalDate::parse), { date = it.toString(); picking = false }) { picking = false }
}

/** Material calendar in Kinfolk's colours (#6). Picking a day closes it, so it needs no buttons (and no copy the design doesn't have). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPicker(day: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
    )
    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let {
            val picked = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
            if (picked != day) onPick(picked)
        }
    }
    val colors = DatePickerDefaults.colors(
        containerColor = Kf.Paper, selectedDayContainerColor = Kf.Green, selectedDayContentColor = Kf.Paper,
        todayDateBorderColor = Kf.Green, todayContentColor = Kf.Green, dayContentColor = Kf.Ink,
    )
    val sans = LocalTextStyle.current.fontFamily
    val type = Typography().run {
        copy(
            titleSmall = titleSmall.copy(fontFamily = sans), bodyLarge = bodyLarge.copy(fontFamily = sans),
            labelLarge = labelLarge.copy(fontFamily = sans), labelMedium = labelMedium.copy(fontFamily = sans),
        )
    }
    MaterialTheme(typography = type) {
        DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {}, colors = colors) {
            DatePicker(state, title = null, headline = null, showModeToggle = false, colors = colors)
        }
    }
}

/** "Anda" or "Belum ada". Another Member already assigned stays assigned until one of these is picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberPick(label: String, picked: String?, me: String, onPick: (String?) -> Unit) = Labeled(label) {
    // ponytail: only "Anda" until Invitation (#4) brings other Members and their names.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickChip(stringResource(Res.string.you), picked == me) { onPick(me) }
        PickChip(stringResource(Res.string.nobody), picked == null) { onPick(null) }
    }
}

/** Four digits shown as "14.30". */
private val TimeDots = VisualTransformation { text ->
    val out = if (text.length > 2) text.text.take(2) + "." + text.text.drop(2) else text.text
    TransformedText(AnnotatedString(out), object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = if (offset > 2) offset + 1 else offset
        override fun transformedToOriginal(offset: Int) = (if (offset > 2) offset - 1 else offset).coerceAtMost(text.length)
    })
}

@Composable
fun TimeInput(digits: String, onDigits: (String) -> Unit) = BasicTextField(
    digits, { onDigits(it.filter(Char::isDigit).take(4)) }, Modifier.fillMaxWidth(),
    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    visualTransformation = TimeDots,
    singleLine = true,
)
