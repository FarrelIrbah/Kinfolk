package id.kinfolk.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.instrument_sans
import kinfolk.shared.generated.resources.newsreader
import org.jetbrains.compose.resources.Font

// Values copied from design/Kinfolk_ Family Care Coordination/Kinfolk v3.dc.html.
// Do not approximate; see CLAUDE.md.
object Kf {
    val Ink = Color(0xFF22261F)
    val Ink2 = Color(0xFF44463E)
    val Muted = Color(0xFF6B6A60)
    val Paper = Color(0xFFF3EEE4) // screen background
    val Card = Color(0xFFFBF8F2)
    val CardAlt = Color(0xFFE9E2D4)
    val Sand = Color(0xFFE4DDD0)
    val Line = Color(0xFFD6CEBF)
    val Green = Color(0xFF2F5D4A)
    val GreenTint = Color(0xFFE3EBE5)
    val Sos = Color(0xFF9E3B1E)
    val FlagBg = Color(0xFFF4E4B0)
    val FlagInk = Color(0xFF4A3B10)
    val Hairline = Color(0x1222261F) // rgba(34,38,31,.07)
    val Border = Color(0x1A22261F) // rgba(34,38,31,.1)
    val TabBorder = Color(0x1422261F) // rgba(34,38,31,.08)
    val InputBorder = Color(0x2E22261F) // rgba(34,38,31,.18)
}

@Composable
fun sans(): FontFamily = FontFamily(
    listOf(400, 500, 600).map {
        Font(Res.font.instrument_sans, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    }
)

// ponytail: browser applies Newsreader's optical size automatically (opsz = font-size); Compose doesn't, so pass it per size.
@Composable
fun serif(size: Float, weight: Int = 500): FontFamily = FontFamily(
    Font(
        Res.font.newsreader,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", size.coerceIn(6f, 72f))),
    )
)

/** `font:{weight} {size}px/{lineHeight} 'Newsreader'` from the design. */
@Composable
fun serifStyle(size: Float, lineHeight: Float? = null, weight: Int = 500): TextStyle = TextStyle(
    fontFamily = serif(size, weight),
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    color = Kf.Ink,
)

@Composable
fun KinfolkTheme(content: @Composable () -> Unit) {
    // Browser default body text: 16px Instrument Sans, #22261F.
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontFamily = sans(), fontSize = 16.sp, color = Kf.Ink),
        content = content,
    )
}
