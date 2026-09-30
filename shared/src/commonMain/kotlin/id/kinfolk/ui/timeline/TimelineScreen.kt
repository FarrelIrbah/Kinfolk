package id.kinfolk.ui.timeline

import androidx.compose.foundation.background
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
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.kind_visit
import kinfolk.shared.generated.resources.same_history
import kinfolk.shared.generated.resources.tab_timeline
import kinfolk.shared.generated.resources.timeline_empty
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Entry copy approved in #8, after the prototype's "Ringkasan neurologi: fisioterapi 2x/minggu, …". */
fun text(e: TimelineEntry, tz: TimeZone): String = when (e.kind) {
    TimelineEntry.Kind.appointment -> {
        val at = e.startsAt.toLocalDateTime(tz)
        "Menjadwalkan ${e.title} dengan ${e.provider}, ${shortDate(at.date)} · ${hm(at.time)}."
    }
    TimelineEntry.Kind.visit_note -> when {
        e.nextSteps.isNotEmpty() -> "${e.title}: ${e.nextSteps.joinToString(", ").trimEnd('.')}."
        e.notes.isNotBlank() -> "${e.title}: ${e.notes}"
        else -> e.title
    }
}

/** A Timeline row, ready to draw. */
class TimelineRow(val by: Person, val whenLabel: String, val text: String, val open: () -> Unit)

private val EmptyLine = Color(0x3322261F) // rgba(34,38,31,.2)

/**
 * `timeline` (docs/screen-map.md). Approved deviation (#8): Appointments and Visit Notes only, all "Kunjungan";
 * no filter chips or "Catatan" pill; the `empty` screen's dashed box while there's nothing yet.
 */
@Composable
fun TimelineScreen(rows: List<TimelineRow>) {
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.same_history), fontSize = 13.sp, color = Kf.Muted)
            Text(stringResource(Res.string.tab_timeline), style = serifStyle(30f, 1.1f))
        }
        if (rows.isEmpty()) EmptyTimeline()
        Column {
            val kind = stringResource(Res.string.kind_visit).uppercase()
            rows.forEach { e ->
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
                            Text(kind, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em, color = Kf.Green, modifier = Modifier.alignByBaseline())
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
fun EmptyTimeline() {
    Text(
        stringResource(Res.string.timeline_empty),
        fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().dashed(EmptyLine, 18.dp).padding(20.dp),
    )
}
