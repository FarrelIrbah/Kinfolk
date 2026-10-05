package id.kinfolk.ui.notes

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Note
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.HintedInput
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.Toggle
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.home.Person
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.note_hint
import kinfolk.shared.generated.resources.notes_only_me
import kinfolk.shared.generated.resources.notes_private_hint
import kinfolk.shared.generated.resources.notes_shared
import kinfolk.shared.generated.resources.notes_title
import kinfolk.shared.generated.resources.save
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Under a Note: "Sri · Baru saja · hanya Anda" (v3); "Baru saja" within the hour, else "24 Sept", like `member` (#20). */
fun noteMeta(name: String, at: Instant, private: Boolean, now: Instant, tz: TimeZone): String =
    "$name · ${if (now - at < 1.hours) "Baru saja" else dayMonth(at.toLocalDateTime(tz).date)}" + if (private) " · hanya Anda" else ""

/**
 * v3 `notes`: "Bersama" / "Hanya saya", the private hint naming the Care Recipient [recipientName] for "Bapak", the
 * input with its "Hanya saya" switch and "Simpan", then that tab's Notes. [save] returns false when it failed.
 */
@Composable
fun NotesScreen(
    notes: List<Note>,
    recipientName: String,
    now: Instant,
    tz: TimeZone,
    person: (String) -> Person,
    onBack: () -> Unit,
    save: suspend (text: String, private: Boolean) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var privateTab by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // design: padding 4px 20px, gap 16
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.notes_title), style = serifStyle(30f, 1.1f))
        // design: grid 2 cols, gap 4, #E4DDD0, r12, p4; buttons r9, h36, 14px 600; picking a tab sets the switch
        Row(Modifier.fillMaxWidth().background(Kf.Sand, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to Res.string.notes_shared, true to Res.string.notes_only_me).forEach { (p, label) ->
                Box(
                    Modifier.weight(1f).height(36.dp).background(if (p == privateTab) Kf.Card else Color.Transparent, RoundedCornerShape(9.dp))
                        .tap { privateTab = p; private = p },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        // design: 13px/1.45 #44463E on #E9E2D4, r12, padding 10px 12px
        if (privateTab) Text(
            stringResource(Res.string.notes_private_hint, recipientName), fontSize = 13.sp, lineHeight = (13 * 1.45).sp, color = Kf.Ink2,
            modifier = Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        // design: #FBF8F2, r18, p16, gap 10
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // design: h48 plus its 1px rgba(34,38,31,.14) border, r12, white, padding 0 12, 15px
            Box(
                Modifier.fillMaxWidth().height(50.dp).background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x2422261F), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { HintedInput(draft, { draft = it }, stringResource(Res.string.note_hint), LocalTextStyle.current.copy(fontSize = 15.sp, color = Kf.Ink), singleLine = true) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Toggle(stringResource(Res.string.notes_only_me), private) { private = it }
                // design: h40, r12, #22261F, #F3EEE4, padding 0 18, 14px 600
                Box(
                    Modifier.height(40.dp).background(Kf.Ink, RoundedCornerShape(12.dp)).tap {
                        val text = draft.trim()
                        if (text.isNotEmpty() && !busy) scope.launch {
                            busy = true
                            if (save(text, private)) { draft = ""; privateTab = private }
                            busy = false
                        }
                    }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.save), color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        // design: gap 8; rows #FBF8F2, r16, p14, gap 12; avatar 30, 12px; text 15/1.45; meta 12px #6B6A60, gap 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.filter { it.private == privateTab }.forEach { n ->
                val by = person(n.by)
                Row(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(16.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(by.initial, by.color, 30.dp, 12.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(n.text, fontSize = 15.sp, lineHeight = (15 * 1.45).sp)
                        Text(noteMeta(by.name, n.at, n.private, now, tz), fontSize = 12.sp, color = Kf.Muted)
                    }
                }
            }
        }
    }
}
