package id.kinfolk.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Question
import id.kinfolk.data.SwapAsk
import id.kinfolk.data.Task
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.shortDay
import id.kinfolk.ui.appointment.updatedAgo
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import id.kinfolk.ui.tasks.overdue
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.inbox_accept
import kinfolk.shared.generated.resources.inbox_decline
import kinfolk.shared.generated.resources.inbox_empty
import kinfolk.shared.generated.resources.inbox_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

// v3 `inbox` ("Menunggu Anda"), derived from data the app already reads (#31). Summary and dose-change items come
// with their own tickets.

sealed interface InboxItem {
    data class Swap(val ask: SwapAsk) : InboxItem
    data class Asked(val question: Question) : InboxItem
    data class Late(val task: Task) : InboxItem
}

/**
 * v3's order: swaps asked of me; the others' Questions on the next Appointment, newest first, until its Visit Note
 * (carried-over ones aren't new); overdue Tasks of anyone, oldest first (approved in #31).
 */
fun inbox(swaps: List<SwapAsk>, questions: List<Question>, noteReady: Boolean, tasks: List<Task>, me: String, today: LocalDate): List<InboxItem> =
    swaps.map(InboxItem::Swap) +
        (if (noteReady) emptyList() else questions.filter { it.askedBy != me && !it.carried }.reversed().map(InboxItem::Asked)) +
        tasks.overdue(today).map(InboxItem::Late)

/** "Agus meminta Anda ambil telepon cek malam Kam 1 Okt, 19.00". */
fun swapTitle(asker: String, s: SwapAsk) =
    "$asker meminta Anda ambil ${s.name.replaceFirstChar { it.lowercase() }} ${shortDay(s.day)} ${dayMonth(s.day)}, ${hm(s.timeOfDay)}"

/** v3's "Dikirim 20 mnt lalu." without its reason, which swaps don't carry (approved in #31). */
fun swapSub(s: SwapAsk, now: Instant) = "Dikirim ${updatedAgo(s.askedAt, now)}."

/** "Perpanjangan izin parkir terlambat 2 hari". */
fun lateTitle(t: Task, today: LocalDate) = "${t.text} terlambat ${t.due.daysUntil(today)} hari"

/** "Tugas Budi · Anda bisa mengingatkan", "· Diingatkan" once sent; just "Tugas Anda" for mine (approved in #31). */
fun lateSub(t: Task, me: String, owner: String, canRemind: Boolean) = when {
    t.ownerId == me -> "Tugas Anda"
    !canRemind -> "Tugas $owner"
    t.remindedAt != null -> "Tugas $owner · Diingatkan"
    else -> "Tugas $owner · Anda bisa mengingatkan"
}

/** One row as v3 draws it; [onAccept]/[onDecline] only on a swap. */
class InboxRow(val who: Person, val title: String, val sub: String, val onOpen: () -> Unit, val onAccept: (() -> Unit)? = null, val onDecline: (() -> Unit)? = null)

@Composable
fun InboxScreen(rows: List<InboxRow>, onBack: () -> Unit) {
    // design: padding 4px 20px, gap 16
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.inbox_title), style = serifStyle(30f, 1.1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { rows.forEach { InboxCard(it) } }
        if (rows.isEmpty()) {
            // design: 15px #6B6A60, centered, padding 40px 0
            Text(
                stringResource(Res.string.inbox_empty), Modifier.fillMaxWidth().padding(vertical = 40.dp),
                fontSize = 15.sp, color = Kf.Muted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InboxCard(r: InboxRow) {
    // design: #FBF8F2, radius 18, padding 14 16, gap 12
    Column(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().tap(r.onOpen), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(r.who.initial, r.who.color, 32.dp, 13.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = (15 * 1.35).sp)
                Text(r.sub, fontSize = 13.sp, lineHeight = (13 * 1.4).sp, color = Kf.Muted)
            }
        }
        if (r.onAccept != null && r.onDecline != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // design: height 42, radius 12; Terima #2F5D4A on #F3EEE4, Tolak 1px rgba(34,38,31,.18)
            Box(
                Modifier.weight(1f).height(42.dp).background(Kf.Green, RoundedCornerShape(12.dp)).tap(r.onAccept),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.inbox_accept), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(42.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(12.dp)).background(Color.Transparent).tap(r.onDecline),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.inbox_decline), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
