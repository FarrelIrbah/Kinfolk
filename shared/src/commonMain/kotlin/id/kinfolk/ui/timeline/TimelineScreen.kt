package id.kinfolk.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.TimelineEntry
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Kf
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.shortDate
import id.kinfolk.ui.dashed
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.SvgPath
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.filter_all
import kinfolk.shared.generated.resources.kind_checkin
import kinfolk.shared.generated.resources.kind_document
import kinfolk.shared.generated.resources.kind_medicine
import kinfolk.shared.generated.resources.kind_rota
import kinfolk.shared.generated.resources.kind_visit
import kinfolk.shared.generated.resources.notes_title
import kinfolk.shared.generated.resources.same_history
import kinfolk.shared.generated.resources.tab_timeline
import kinfolk.shared.generated.resources.timeline_empty
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Entry copy approved in #8, after the prototype's "Ringkasan neurologi: fisioterapi 2x/minggu, …". */
fun text(e: TimelineEntry, tz: TimeZone): String = when (e.kind) {
    TimelineEntry.Kind.appointment -> {
        val at = e.startsAt!!.toLocalDateTime(tz)
        "Menjadwalkan ${e.title} dengan ${e.provider}, ${shortDate(at.date)} · ${hm(at.time)}."
    }
    TimelineEntry.Kind.visit_note -> when {
        e.nextSteps.isNotEmpty() -> "${e.title}: ${e.nextSteps.joinToString(", ").trimEnd('.')}."
        e.notes.isNotBlank() -> "${e.title}: ${e.notes}"
        else -> e.title.orEmpty()
    }
    TimelineEntry.Kind.drive_confirmed, TimelineEntry.Kind.access_change, TimelineEntry.Kind.dose_given,
    TimelineEntry.Kind.check_in, TimelineEntry.Kind.document -> e.text
}

/** The v3 kinds, with their uppercase label and colour (`timeline()` in the prototype). */
enum class EntryType(val label: StringResource, val color: Color) {
    Visit(Res.string.kind_visit, Color(0xFF2F5D4A)),
    Medicine(Res.string.kind_medicine, Color(0xFF9A7A2F)),
    CheckIn(Res.string.kind_checkin, Color(0xFF3E6E8E)),
    Document(Res.string.kind_document, Color(0xFF6C5A8E)),
    Rota(Res.string.kind_rota, Color(0xFFB0643A)),
}

fun type(kind: TimelineEntry.Kind) = when (kind) {
    TimelineEntry.Kind.appointment, TimelineEntry.Kind.visit_note -> EntryType.Visit
    TimelineEntry.Kind.drive_confirmed -> EntryType.Rota
    TimelineEntry.Kind.access_change -> EntryType.CheckIn // #20
    TimelineEntry.Kind.dose_given -> EntryType.Medicine // #21
    TimelineEntry.Kind.check_in -> EntryType.CheckIn // #24
    TimelineEntry.Kind.document -> EntryType.Document // #29
}

/** The filter chips: Semua (null), then a chip per kind but Rota, which only Semua shows. */
val Filters = listOf(null, EntryType.Visit, EntryType.Medicine, EntryType.CheckIn, EntryType.Document)

/** A Timeline row, ready to draw. */
class TimelineRow(val by: Person, val type: EntryType, val whenLabel: String, val text: String, val open: () -> Unit)

private val EmptyLine = Color(0x3322261F) // rgba(34,38,31,.2)

/**
 * `timeline` (docs/screen-map.md): every kind, filtered by [filter] (null = Semua). The `empty` screen's dashed box
 * while there's nothing yet. The "Catatan" pill opens `notes` (#27).
 */
@Composable
fun TimelineScreen(rows: List<TimelineRow>, filter: EntryType?, onFilter: (EntryType?) -> Unit, onNotes: () -> Unit) {
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // design: space-between, align-items:flex-end, gap 10
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.same_history), fontSize = 13.sp, color = Kf.Muted)
                Text(stringResource(Res.string.tab_timeline), style = serifStyle(30f, 1.1f))
            }
            // design: #FBF8F2, r999, h40, padding 0 14, 14px 500, gap 6, pencil 16
            Row(
                Modifier.height(40.dp).background(Kf.Card, CircleShape).tap(onNotes).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                SvgPath("M4 20h4L19 9l-4-4L4 16zM14 6l4 4", 16.dp, Kf.Ink)
                Text(stringResource(Res.string.notes_title), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (rows.isEmpty()) EmptyBox(stringResource(Res.string.timeline_empty))
        // design: display:flex; gap:6px; overflow-x:auto
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Filters.forEach { f ->
                val on = f == filter
                Text(
                    stringResource(f?.label ?: Res.string.filter_all), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (on) Kf.Paper else Kf.Ink,
                    modifier = Modifier.background(if (on) Kf.Ink else Kf.Card, CircleShape).tap { onFilter(f) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Column {
            rows.filter { filter == null || it.type == filter }.forEach { e ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(e.by.initial, e.by.color, 30.dp, 12.sp)
                        Box(Modifier.weight(1f).heightIn(min = 14.dp).width(1.5.dp).background(Kf.Line))
                    }
                    Column(
                        Modifier.weight(1f).tap(e.open).padding(top = 4.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(e.type.label).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em, color = e.type.color, modifier = Modifier.alignByBaseline())
                            Text(e.whenLabel, fontSize = 12.sp, color = Kf.Muted, modifier = Modifier.alignByBaseline())
                        }
                        Text(e.text, fontSize = 15.sp, lineHeight = (15 * 1.45).sp)
                        Text(e.by.name, fontSize = 12.sp, color = Kf.Muted)
                    }
                }
            }
        }
    }
}

/** The `empty` screen's box: 1.5px dashed rgba(34,38,31,.2), radius 18, padding 20, 14px/1.5 muted, centered. */
@Composable
fun EmptyBox(text: String) {
    Text(
        text,
        fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().dashed(EmptyLine, 18.dp).padding(20.dp),
    )
}
