package id.kinfolk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Kf
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.digest_day
import kinfolk.shared.generated.resources.digest_phone
import org.jetbrains.compose.resources.stringResource

private val Gray = Color(0xFF8E8E93)
private val Line = Color(0xFFE5E5EA)

/**
 * `sms` from design v3 as a read-only WhatsApp preview of my last weekly digest [text] (#40): no reply chips, "Anda"
 * for Rina. The phone's own look: system font on white (App paints the screen white up to the top edge).
 */
@Composable
fun DigestScreen(text: String, onBack: () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Default)) {
        Column {
            // design: padding 4px 16px 10px under margin-top:-60px + padding-top:56px, so 4px less from the top
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.back), Modifier.width(70.dp).tap(onBack), color = Color(0xFF007AFF), fontSize = 16.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(38.dp).background(Kf.Green, CircleShape), contentAlignment = Alignment.Center) {
                        Text("K", style = serifStyle(18f), color = Color(0xFFF3EEE4))
                    }
                    Text("Kinfolk", fontSize = 11.sp, color = Color(0xFF3C3C43))
                }
                Text(stringResource(Res.string.digest_phone), Modifier.width(70.dp), fontSize = 11.sp, color = Gray, textAlign = TextAlign.End)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            // design: padding 14px 12px, gap 8; bubble max-width 80%, #E9E9EB, radius 18, padding 9px 13px, 15px/1.35
            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.digest_day), Modifier.fillMaxWidth(), fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text, Modifier.weight(1f, fill = false).background(Color(0xFFE9E9EB), RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
                        color = Color.Black, fontSize = 15.sp, lineHeight = (15 * 1.35).sp,
                    )
                    Spacer(Modifier.fillMaxWidth(0.2f))
                }
            }
        }
    }
}
