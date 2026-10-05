package id.kinfolk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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

/** onb1 input: height 52 plus its 1px border (an `<input>` is content-box), radius 14, 16px side padding. [multiline] grows it downwards with the text. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, multiline: Boolean = false, input: @Composable () -> Unit) = Box(modifier) {
    Labeled(label) {
        Box(
            Modifier.fillMaxWidth().let { if (multiline) it.heightIn(min = 54.dp) else it.height(54.dp) }
                .background(Kf.Card, RoundedCornerShape(14.dp))
                .border(1.dp, Kf.InputBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
            contentAlignment = Alignment.CenterStart,
        ) { input() }
    }
}

/** onb1 chip: padding 10px 16px inside a 1px border, which CSS adds to the size and Compose draws inside. */
@Composable
fun Chip(label: String, border: Color, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(
        label, color = fg, fontSize = 15.sp,
        modifier = Modifier.background(bg, CircleShape).border(1.dp, border, CircleShape).tap(onClick).padding(horizontal = 17.dp, vertical = 11.dp),
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

/** border:1.5px dashed rgba(34,38,31,.25); border-radius:16px, unless given. Drawn inside, so padding takes the extra 1.5 CSS adds. */
fun Modifier.dashed(color: Color = DashLine, radius: Dp = 16.dp) = drawBehind {
    val w = 1.5.dp.toPx()
    // ponytail: Chrome's dash rhythm for a 1.5px border (dashes and gaps ~3x the width), matched by eye.
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 3 * w))),
    )
}

/** A bare text input showing [hint] in the muted color while empty. */
@Composable
fun HintedInput(
    value: String, onValue: (String) -> Unit, hint: String, style: TextStyle, modifier: Modifier = Modifier, singleLine: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) = BasicTextField(
    value, onValue, modifier.fillMaxWidth(), textStyle = style, singleLine = singleLine, keyboardActions = keyboardActions,
    decorationBox = { field -> Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { if (value.isEmpty()) Text(hint, style = style.copy(color = Kf.Muted)); field() } },
)

/** `appt` "Tambah pertanyaan…" row: 44px input (white, 1px rgba(34,38,31,.14) added to its height, radius 12) and a dark 44px "Tambah" button. */
@Composable
fun AddRow(value: String, onValue: (String) -> Unit, hint: String, button: String, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(46.dp).background(Color.White, RoundedCornerShape(12.dp))
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

/** Switch from `notes`/`member`: 50x30 track (#2F5D4A on, #D6CEBF off), 24px white knob. */
@Composable
fun Switch(on: Boolean) {
    Box(
        Modifier.size(50.dp, 30.dp).background(if (on) Kf.Green else Kf.Line, CircleShape).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** [Switch] with its label on the right: 14px, gap 10. */
@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.tap { onChange(!on) }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(on)
        Text(label, fontSize = 14.sp)
    }
}

/**
 * Prototype toast: #22261F, radius 14, 14px/1.4, 20 from the sides; rises in over 280ms and leaves after 2.6s (5s
 * with an [action], the prototype's "Urungkan" pill: rgba(243,238,228,.14), height 32, padding 0 12, 13/600, gap 12).
 * [overTabs]: bottom 104 instead of 40, i.e. above the tab bar (its 30px home-indicator padding is the nav bar here).
 */
@Composable
fun Toast(text: String?, modifier: Modifier = Modifier, overTabs: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    val shown = remember { MutableTransitionState(false) }.apply { targetState = text != null }
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    val rise = with(LocalDensity.current) { 16.dp.roundToPx() }
    AnimatedVisibility(
        shown, (if (overTabs) modifier.navigationBarsPadding().padding(bottom = 74.dp) else modifier.padding(bottom = 40.dp)).padding(horizontal = 20.dp),
        enter = if (LocalReduceMotion.current) EnterTransition.None
        else fadeIn(tween(280, easing = KfEase)) + scaleIn(tween(280, easing = KfEase), initialScale = .97f) + slideInVertically(tween(280, easing = KfEase)) { rise },
        exit = ExitTransition.None,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .dropShadow(RoundedCornerShape(14.dp), Shadow(24.dp, Color(0x33000000), offset = DpOffset(0.dp, 8.dp)))
                .background(Kf.Ink, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(last, Modifier.weight(1f), color = Kf.Paper, fontSize = 14.sp, lineHeight = (14 * 1.4).sp)
            if (action != null) Box(Modifier.height(32.dp).background(Color(0x24F3EEE4), CircleShape).tap(onAction).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(action, color = Kf.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** What the confirm sheet asks; [run] happens on the red button. */
class Confirm(val title: String, val body: String, val action: String, val run: () -> Unit)

/** Prototype CONFIRM SHEET: scrim rgba(21,19,15,.35), sheet #FBF8F2 radius 26 on top, padding 20 20 40, gap 14; slides up over 340ms, closes at once. */
@Composable
fun ConfirmSheet(confirm: Confirm?, cancel: String, onDismiss: () -> Unit) {
    confirm ?: return
    Sheet(confirm, onDismiss) {
        Text(confirm.title, style = serifStyle(22f, 1.2f))
        Text(confirm.body, fontSize = 14.sp, lineHeight = (14 * 1.45).sp, color = Kf.Ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(50.dp).border(1.dp, Color(0x2E22261F), RoundedCornerShape(16.dp)).tap(onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text(cancel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(50.dp).background(Kf.Sos, RoundedCornerShape(16.dp)).tap { onDismiss(); confirm.run() },
                contentAlignment = Alignment.Center,
            ) { Text(confirm.action, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The prototype's bottom sheet (confirm, swap), with its handle; a new [key] slides it up again. */
@Composable
fun Sheet(key: Any, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x5915130F)).tap(onDismiss))
        AnimatedVisibility(
            remember(key) { MutableTransitionState(false) }.apply { targetState = true }, Modifier.align(Alignment.BottomCenter),
            enter = if (LocalReduceMotion.current) EnterTransition.None else slideInVertically(tween(340, easing = KfEase)) { it }, exit = ExitTransition.None,
        ) {
            Column(
                Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).tap {}
                    .navigationBarsPadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp, 5.dp).background(Kf.Line, RoundedCornerShape(3.dp)).align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}
