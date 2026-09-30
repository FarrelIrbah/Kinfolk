package id.kinfolk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Draws a design SVG path (`viewBox="0 0 24 24"`, round caps/joins) at [size]. */
@Composable
fun SvgPath(d: String, size: Dp, color: Color, modifier: Modifier = Modifier, fill: Color? = null, strokeWidth: Float = 1.8f) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            fill?.let { drawPath(path, it) }
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Buttons in the design have no ripple. */
fun Modifier.tap(onClick: () -> Unit) = clickable(interactionSource = null, indication = null, onClick = onClick)

@Composable
fun Avatar(initial: String, color: Color, size: Dp, fontSize: TextUnit) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

/** List card from `contacts`/`circle`/onb2: #FBF8F2, radius 18, rows clipped. */
@Composable
fun Card(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kf.Card)) { content() }

/** border-bottom:1px solid rgba(34,38,31,.07) on each row. */
@Composable
fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.Hairline))

/** Back pill from `appt`/`handoff`: height 40, #FBF8F2, 14px. */
@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(40.dp).background(Kf.Card, CircleShape).tap(onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label.isNotEmpty()) Text(label, fontSize = 13.sp, color = Kf.Muted)
        content()
    }
}

/** onb1 input: height 52, radius 14, 16px side padding. [multiline] grows it downwards with the text. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, multiline: Boolean = false, input: @Composable () -> Unit) = Box(modifier) {
    Labeled(label) {
        Box(
            Modifier.fillMaxWidth().let { if (multiline) it.heightIn(min = 52.dp) else it.height(52.dp) }
                .background(Kf.Card, RoundedCornerShape(14.dp))
                .border(1.dp, Kf.InputBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
            contentAlignment = Alignment.CenterStart,
        ) { input() }
    }
}

@Composable
fun Chip(label: String, border: Color, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(
        label, color = fg, fontSize = 15.sp,
        modifier = Modifier.background(bg, CircleShape).border(1.dp, border, CircleShape).tap(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** onb1 "Beliau adalah" chip: one of a set, filled green when picked. */
@Composable
fun PickChip(label: String, on: Boolean, onClick: () -> Unit) =
    Chip(label, if (on) Kf.Green else Kf.InputBorder, if (on) Kf.Green else Kf.Card, if (on) Kf.Paper else Kf.Ink, onClick)

/** Approved sign-in error style: 13px, the SOS red. */
@Composable
fun ErrorText(text: String) = Text(text, fontSize = 13.sp, color = Kf.Sos)

/** Styled like "Lihat semua": 14px, 500. */
@Composable
fun Link(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = modifier.tap(onClick))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(54.dp).background(Kf.Green, RoundedCornerShape(16.dp)).tap(onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Kf.Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Uppercase group label from `contacts`/`circle`: 12px, 600, letter-spacing .08em, muted. */
@Composable
fun SectionLabel(text: String) =
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em, color = Kf.Muted)

/** "+ Unggah dokumen" / "+ Tambah kontak": height 50, radius 16, 1.5px dashed rgba(34,38,31,.25), 15px 500. */
@Composable
fun DashedButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(50.dp).dashed().tap(onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private val DashLine = Color(0x4022261F) // rgba(34,38,31,.25)

/** border:1.5px dashed rgba(34,38,31,.25); border-radius:16px. */
fun Modifier.dashed() = drawBehind {
    val w = 1.5.dp.toPx()
    // ponytail: Chrome's dash rhythm for a 1.5px border (dashes and gaps ~3x the width), matched by eye.
    drawRoundRect(
        DashLine, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(16.dp.toPx() - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 3 * w))),
    )
}

/** A bare text input showing [hint] in the muted color while empty. */
@Composable
fun HintedInput(value: String, onValue: (String) -> Unit, hint: String, style: TextStyle, modifier: Modifier = Modifier, singleLine: Boolean = false) =
    BasicTextField(
        value, onValue, modifier.fillMaxWidth(), textStyle = style, singleLine = singleLine,
        decorationBox = { field -> Box { if (value.isEmpty()) Text(hint, style = style.copy(color = Kf.Muted)); field() } },
    )

/** `appt` "Tambah pertanyaan…" row: 44px input (white, 1px rgba(34,38,31,.14), radius 12) and a dark "Tambah" button. */
@Composable
fun AddRow(value: String, onValue: (String) -> Unit, hint: String, button: String, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(44.dp).background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x2422261F), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            HintedInput(value, onValue, hint, LocalTextStyle.current.copy(fontSize = 15.sp, color = Kf.Ink), singleLine = true)
        }
        Box(Modifier.height(44.dp).background(Kf.Ink, RoundedCornerShape(12.dp)).tap(onAdd).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(button, color = Kf.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Switch from `notes`/`member`: 50x30 track (#2F5D4A on, #D6CEBF off), 24px white knob; label 14px, gap 10. */
@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.tap { onChange(!on) }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(50.dp, 30.dp).background(if (on) Kf.Green else Kf.Line, CircleShape).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(24.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
        }
        Text(label, fontSize = 14.sp)
    }
}
