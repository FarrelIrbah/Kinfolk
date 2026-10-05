package id.kinfolk.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Offer
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.Switch
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.addon_body
import kinfolk.shared.generated.resources.addon_name
import kinfolk.shared.generated.resources.not_now
import kinfolk.shared.generated.resources.one_price
import kinfolk.shared.generated.resources.pay_fine
import kinfolk.shared.generated.resources.pay_head
import kinfolk.shared.generated.resources.pay_sub
import kinfolk.shared.generated.resources.pay_sub_no_trial
import kinfolk.shared.generated.resources.per_month
import kinfolk.shared.generated.resources.plan_incl
import kinfolk.shared.generated.resources.start_trial
import kinfolk.shared.generated.resources.subscribe
import kinfolk.shared.generated.resources.total_family
import org.jetbrains.compose.resources.stringResource

// ponytail: without a store (the local stack, an emulator without Play) the prices are v3's, so the screen still shows.
private val V3 = Offer("$15", "$8", "$23", trial = true)

/**
 * `paywall` from design v3 (ADR 0006): store prices instead of "$15"/"+$8" (screen map), the add-on on at first as in
 * v3. Owner-approved in #50: without a free trial on offer, the sub drops "Coba gratis 14 hari." and the button reads
 * "Berlangganan". [onStart] gets whether the add-on is on.
 */
@Composable
fun PaywallScreen(offer: Offer?, onBack: () -> Unit, onStart: (transcription: Boolean) -> Unit) {
    val o = offer ?: V3
    var addon by remember { mutableStateOf(true) }
    @Composable
    fun price(amount: String, size: Float) = Text(buildAnnotatedString {
        withStyle(serifStyle(size).toSpanStyle()) { append(amount) }
        withStyle(SpanStyle(fontSize = 13.sp, color = Kf.Muted)) { append(stringResource(Res.string.per_month)) }
    })
    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill("✕ ${stringResource(Res.string.not_now)}", onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.pay_head), style = serifStyle(32f, 1.08f))
            Text(stringResource(if (o.trial) Res.string.pay_sub else Res.string.pay_sub_no_trial), fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        // design: #FBF8F2, radius 20, padding 18, gap 12
        Column(Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kinfolk Family", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
                Row(Modifier.alignByBaseline()) { price(o.plan, 26f) }
            }
            Text(stringResource(Res.string.plan_incl), fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Ink2)
            Text(stringResource(Res.string.one_price), fontSize = 13.sp, color = Kf.Green, fontWeight = FontWeight.SemiBold)
        }
        // design: border 1.5px #2F5D4A when on, else transparent, outside padding 18; radius 20, gap 10
        Column(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(20.dp)).border(1.5.dp, if (addon) Kf.Green else Color.Transparent, RoundedCornerShape(20.dp))
                .tap { addon = !addon }.padding(19.5.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.addon_name), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(addon)
            }
            Text(stringResource(Res.string.addon_body), fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Ink2)
            price("+${o.addOn}", 22f)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.total_family), fontSize = 15.sp, color = Kf.Muted)
            Text((if (addon) o.plus else o.plan) + stringResource(Res.string.per_month), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        PrimaryButton(stringResource(if (o.trial) Res.string.start_trial else Res.string.subscribe)) { onStart(addon) }
        Text(
            stringResource(Res.string.pay_fine), fontSize = 12.sp, lineHeight = (12 * 1.5).sp, color = Kf.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
