package id.kinfolk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Kf
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_appt
import kinfolk.shared.generated.resources.add_appt_sub
import kinfolk.shared.generated.resources.add_meds
import kinfolk.shared.generated.resources.add_meds_sub
import kinfolk.shared.generated.resources.invite_siblings
import kinfolk.shared.generated.resources.joined_of
import kinfolk.shared.generated.resources.drives_no_time
import kinfolk.shared.generated.resources.no_driver
import kinfolk.shared.generated.resources.circle_name
import kinfolk.shared.generated.resources.drives
import kinfolk.shared.generated.resources.latest
import kinfolk.shared.generated.resources.meds_today
import kinfolk.shared.generated.resources.note_ready
import kinfolk.shared.generated.resources.questions_count
import kinfolk.shared.generated.resources.see_all
import kinfolk.shared.generated.resources.see_rota
import kinfolk.shared.generated.resources.sos
import kinfolk.shared.generated.resources.this_week
import kinfolk.shared.generated.resources.write_note
import org.jetbrains.compose.resources.stringResource

data class Person(val name: String, val color: Color) {
    val initial get() = name.take(1)
}

data class NextAppointment(
    val whenLabel: String,
    val countdown: String,
    val title: String,
    val provider: String,
    val driver: Person?,
    val leavesAt: String?,
    val questionCount: Int,
    val noteReady: Boolean,
)

data class DutyDay(val dow: String, val num: Int, val member: Person, val isToday: Boolean)
data class FeedItem(val by: Person, val text: String, val whenLabel: String)

data class HomeState(
    val todayLabel: String,
    val circleName: String,
    val memberCount: Int,
    val next: NextAppointment?,
    val week: List<DutyDay>,
    val dutyLegend: String,
    val medsToday: Int,
    val nextMed: String,
    val feed: List<FeedItem>,
    /** Joined and total non-cancelled Invitations for `empty` row 3; null hides the row (admins only). */
    val invites: Pair<Int, Int>? = null,
)

private val Cream = Color(0xFFF3EEE4)
private val CreamTint = Color(0x29F3EEE4) // rgba(243,238,228,.16)
private val CreamBtn = Color(0x24F3EEE4) // rgba(243,238,228,.14)
private val CreamLine = Color(0x33F3EEE4) // rgba(243,238,228,.2)

@Composable
fun HomeScreen(
    s: HomeState,
    onSos: () -> Unit,
    onOpenAppointment: () -> Unit,
    onAddAppointment: () -> Unit,
    onWriteNote: () -> Unit,
    onRota: () -> Unit,
    onRecords: () -> Unit,
    onTimeline: () -> Unit,
    onInvite: () -> Unit,
) {
    // design: padding:4px 20px; gap:22px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.circle_name, s.circleName, s.memberCount), fontSize = 13.sp, color = Kf.Muted)
                Text(s.todayLabel, style = serifStyle(30f, 1.1f))
            }
            // ponytail: inbox bell hidden until in-app notifications ship (docs/screen-map.md).
            Box(
                Modifier.height(44.dp).background(Kf.Sos, CircleShape).tap(onSos).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.sos), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em)
            }
        }
        // ponytail: search bar, tasks row and weekly digest row hidden until those features ship.

        s.next?.let { AppointmentCard(it, onOpenAppointment, onWriteNote) } ?: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStep(1, stringResource(Res.string.add_appt), stringResource(Res.string.add_appt_sub), part = false, onAddAppointment)
            EmptyStep(2, stringResource(Res.string.add_meds), stringResource(Res.string.add_meds_sub), part = false, onRecords)
            s.invites?.let { (joined, total) ->
                EmptyStep(3, stringResource(Res.string.invite_siblings), stringResource(Res.string.joined_of, joined, total).takeIf { total > 0 }, part = total > 0, onInvite)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(Res.string.this_week), stringResource(Res.string.see_rota), onRota)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.week.forEach { d ->
                    Column(
                        Modifier.weight(1f)
                            .background(if (d.isToday) Kf.Card else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(d.dow, fontSize = 11.sp, color = Kf.Muted)
                        Text(d.num.toString(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Avatar(d.member.initial, d.member.color, 22.dp, 10.sp)
                    }
                }
            }
            Text(s.dutyLegend, fontSize = 13.sp, color = Kf.Muted)
        }

        Row(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onRecords).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ponytail: ring drawn full; dose progress returns with dose logging (docs/screen-map.md).
            Box(Modifier.size(44.dp).background(Kf.Green, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).background(Kf.Card, CircleShape), contentAlignment = Alignment.Center) {
                    Text(s.medsToday.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.meds_today), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(s.nextMed, fontSize = 13.sp, color = Kf.Muted)
            }
            Text("›", color = Kf.Muted, fontSize = 18.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(Res.string.latest), stringResource(Res.string.see_all), onTimeline)
            Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp))) {
                s.feed.forEach { e ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(e.by.initial, e.by.color, 30.dp, 12.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(e.text, fontSize = 14.sp, lineHeight = (14 * 1.4).sp)
                            Text("${e.by.name} · ${e.whenLabel}", fontSize = 12.sp, color = Kf.Muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.Hairline))
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(a: NextAppointment, onOpen: () -> Unit, onWriteNote: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Kf.Green, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(a.whenLabel, fontSize = 13.sp, color = Cream.copy(alpha = .85f))
            Text(
                if (a.noteReady) stringResource(Res.string.note_ready) else a.countdown,
                fontSize = 12.sp, color = Cream,
                modifier = Modifier.background(CreamTint, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.tap(onOpen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title, style = serifStyle(26f, 1.15f).copy(color = Cream))
            Text(a.provider, fontSize = 14.sp, color = Cream.copy(alpha = .85f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CreamLine))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            a.driver?.let { Avatar(it.initial, it.color, 26.dp, 12.sp) }
            val line = when {
                a.driver == null -> stringResource(Res.string.no_driver)
                a.leavesAt == null -> stringResource(Res.string.drives_no_time, a.driver.name)
                else -> stringResource(Res.string.drives, a.driver.name, a.leavesAt)
            }
            Text(line, fontSize = 14.sp, color = Cream, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(stringResource(Res.string.questions_count, a.questionCount), CreamBtn, Cream, onOpen, Modifier.weight(1f))
            CardButton(stringResource(Res.string.write_note), Cream, Kf.Green, onWriteNote, Modifier.weight(1f))
        }
    }
}

/** Rows 1-3 of the v3 `empty` screen, shown on Home while nothing is upcoming (docs/screen-map.md). */
@Composable
private fun EmptyStep(num: Int, title: String, sub: String?, part: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(if (part) Kf.FlagBg else Color.Transparent, CircleShape)
                .border(1.5.dp, if (part) Color(0xFFC9A77C) else Color(0x4022261F), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(num.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, fontSize = 13.sp, color = Kf.Muted) }
        }
        Text("›", color = Kf.Muted, fontSize = 18.sp)
    }
}

@Composable
private fun CardButton(label: String, bg: Color, fg: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(44.dp).background(bg, RoundedCornerShape(12.dp)).tap(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = serifStyle(20f), modifier = Modifier.alignByBaseline())
        Text(action, color = Kf.Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline().tap(onAction))
    }
}
