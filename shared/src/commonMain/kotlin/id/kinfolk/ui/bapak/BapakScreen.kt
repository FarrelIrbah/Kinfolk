package id.kinfolk.ui.bapak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.bp_exit
import kinfolk.shared.generated.resources.bp_fine
import kinfolk.shared.generated.resources.bp_help
import kinfolk.shared.generated.resources.bp_mode
import kinfolk.shared.generated.resources.bp_today
import kinfolk.shared.generated.resources.bp_who
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/** Approved in #37: "Selamat sore, Pak Tukiman" by the WIB hour; "Pak"/"Bu" for a father or mother, else the name alone. */
fun greeting(hour: Int, relation: String?, name: String): String {
    val part = when (hour) { in 4..10 -> "pagi"; in 11..14 -> "siang"; in 15..17 -> "sore"; else -> "malam" }
    val title = when (relation) { "father" -> "Pak "; "mother" -> "Bu "; else -> "" }
    return "Selamat $part, $title$name"
}

/** v3's "Dr. Rao jam 14.30. Budi menjemput jam 13.45." (approved in #37: without a departure, "Budi menjemput."). */
fun todayPlan(provider: String, starts: LocalTime, driver: String?, departs: LocalTime?): String =
    "$provider jam ${hm(starts)}." + when {
        driver == null -> ""
        departs == null -> " $driver menjemput."
        else -> " $driver menjemput jam ${hm(departs)}."
    }

/** v3's "Menghubungi Sri dan Budi sekarang. Budi 10 menit dari sini.", the last part only with a distance filled. */
fun helpLine(who: String, near: Pair<String, String>?): String =
    "Menghubungi $who sekarang." + (near?.let { (name, distance) -> " $name $distance dari sini." } ?: "")

/** A row of "Siapa melihat apa": [sub] is "Melihat semuanya" or "Melihat 4 dari 6 · ketuk untuk ubah". */
class Kid(val id: String, val name: String, val color: Color, val sub: String)

/**
 * v3 `bapak`, "Bapak" being the Care Recipient's name (#9). [message] is the green box after a press; the third
 * button calls the organizer ([callLabel], "Telepon Sri").
 */
@Composable
fun BapakScreen(
    recipient: String, hello: String, plan: String, message: String?, callLabel: String, kids: List<Kid>,
    onExit: () -> Unit, onFine: () -> Unit, onHelp: () -> Unit, onCall: () -> Unit, onKid: (Kid) -> Unit,
) {
    // design: padding:4px 22px; gap:18px
    Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.bp_mode, recipient), fontSize = 15.sp, color = Kf.Muted)
            Box(Modifier.height(44.dp).background(Kf.Card, CircleShape).tap(onExit).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.bp_exit), fontSize = 15.sp, color = Kf.Ink)
            }
        }
        Text(hello, style = serifStyle(40f, 1.05f))
        // design: #FBF8F2, radius 20, padding 18, gap 6; 16 muted over 24/600 line-height 1.3
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.bp_today), fontSize = 16.sp, color = Kf.Muted)
            Text(plan, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = (24 * 1.3).sp)
        }
        // design: #E3EBE5, radius 16, padding 14px 16px, 18px/1.4 #1F4033
        if (message != null) Text(
            message, fontSize = 18.sp, lineHeight = (18 * 1.4).sp, color = Color(0xFF1F4033),
            modifier = Modifier.fillMaxWidth().background(Kf.GreenTint, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        // design: height 92, radius 24, 26/600
        listOf(
            Triple(stringResource(Res.string.bp_fine), Kf.Green to Kf.Paper, onFine),
            Triple(stringResource(Res.string.bp_help), Kf.Sos to Color.White, onHelp),
            Triple(callLabel, Kf.Ink to Kf.Paper, onCall),
        ).forEach { (label, colors, onClick) ->
            Box(Modifier.fillMaxWidth().height(92.dp).background(colors.first, RoundedCornerShape(24.dp)).tap(onClick), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.second)
            }
        }
        // design: gap 10, padding-top 8; 20/600; list #FBF8F2 radius 20
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.bp_who), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Kf.Card)) {
                kids.forEach { k ->
                    // design: padding 16, gap 14, min-height 64; avatar 40/16; 19/600 over 15 muted; › 22 muted
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).tap { onKid(k) }.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(k.name.take(1), k.color, 40.dp, 16.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(k.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(k.sub, fontSize = 15.sp, color = Kf.Muted)
                        }
                        Text("›", color = Kf.Muted, fontSize = 22.sp)
                    }
                    Hairline()
                }
            }
        }
    }
}
