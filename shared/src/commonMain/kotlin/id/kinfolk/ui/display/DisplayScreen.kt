package id.kinfolk.ui.display

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.ui.Card
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.Switch
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.a11y_note
import kinfolk.shared.generated.resources.a11y_preview
import kinfolk.shared.generated.resources.a11y_title
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.high_contrast
import kinfolk.shared.generated.resources.high_contrast_sub
import kinfolk.shared.generated.resources.reduce_motion
import kinfolk.shared.generated.resources.reduce_motion_sub
import kinfolk.shared.generated.resources.size_default
import kinfolk.shared.generated.resources.size_large
import kinfolk.shared.generated.resources.size_largest
import kinfolk.shared.generated.resources.text_size
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** What "Tampilan & aksesibilitas" sets, kept on this phone (#41). [size] 0..2 zooms the app 1, 1.1 or 1.2. */
@Serializable
data class Display(val size: Int = 0, val highContrast: Boolean = false, val reduceMotion: Boolean = false) {
    val zoom get() = listOf(1f, 1.1f, 1.2f)[size]
}

/** `a11y` from design v3 (#41); "Bapak" in the note is [recipientName], as in Mode Bapak (#37). */
@Composable
fun DisplayScreen(display: Display, recipientName: String, onBack: () -> Unit, onChange: (Display) -> Unit) {
    // design: padding 4px 20px, gap 18
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.a11y_title), style = serifStyle(30f, 1.1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.text_size), fontSize = 13.sp, color = Kf.Muted)
            // design: grid 3 cols, gap 4, #E4DDD0, r12, p4; buttons r9, h44, 14/16/18px 600
            Row(Modifier.fillMaxWidth().background(Kf.Sand, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Res.string.size_default to 14, Res.string.size_large to 16, Res.string.size_largest to 18).forEachIndexed { i, (label, fs) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).background(if (display.size == i) Kf.Card else Color.Transparent, RoundedCornerShape(9.dp))
                            .tap { onChange(display.copy(size = i)) },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(label), fontSize = fs.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Card {
            listOf(
                Triple(Res.string.high_contrast, Res.string.high_contrast_sub, display.highContrast) to { display.copy(highContrast = !display.highContrast) },
                Triple(Res.string.reduce_motion, Res.string.reduce_motion_sub, display.reduceMotion) to { display.copy(reduceMotion = !display.reduceMotion) },
            ).forEach { (row, flipped) ->
                val (label, desc, on) = row
                // design: padding 14px 16px, gap 12; label 15/500, desc 12 muted, gap 2; border-bottom on each
                Row(
                    Modifier.fillMaxWidth().tap { onChange(flipped()) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(label), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(desc), fontSize = 12.sp, color = Kf.Muted)
                    }
                    Switch(on)
                }
                Hairline()
            }
        }
        // design: #2F5D4A, #F3EEE4, r18, p16, 16/1.45
        Text(
            stringResource(Res.string.a11y_preview), color = Kf.Paper, fontSize = 16.sp, lineHeight = (16 * 1.45).sp,
            modifier = Modifier.fillMaxWidth().background(Kf.Green, RoundedCornerShape(18.dp)).padding(16.dp),
        )
        Text(stringResource(Res.string.a11y_note, recipientName), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
    }
}
