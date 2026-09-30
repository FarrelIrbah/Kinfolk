package id.kinfolk.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Need
import id.kinfolk.data.Relation
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Labeled
import id.kinfolk.ui.Link
import id.kinfolk.ui.Chip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.code_head
import kinfolk.shared.generated.resources.code_label
import kinfolk.shared.generated.resources.code_sub
import kinfolk.shared.generated.resources.code_sub_sms
import kinfolk.shared.generated.resources.code_wrong
import kinfolk.shared.generated.resources.continue_
import kinfolk.shared.generated.resources.main_need
import kinfolk.shared.generated.resources.need_check_ins
import kinfolk.shared.generated.resources.need_medicines
import kinfolk.shared.generated.resources.need_paperwork
import kinfolk.shared.generated.resources.need_visits
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.onb1_head
import kinfolk.shared.generated.resources.onb_create
import kinfolk.shared.generated.resources.onb_head
import kinfolk.shared.generated.resources.onb_invited
import kinfolk.shared.generated.resources.onb_sign_in
import kinfolk.shared.generated.resources.onb_sub
import kinfolk.shared.generated.resources.phone_head
import kinfolk.shared.generated.resources.phone_label
import kinfolk.shared.generated.resources.phone_sub
import kinfolk.shared.generated.resources.relation_father
import kinfolk.shared.generated.resources.relation_grandparent
import kinfolk.shared.generated.resources.relation_mother
import kinfolk.shared.generated.resources.relation_spouse
import kinfolk.shared.generated.resources.resend
import kinfolk.shared.generated.resources.resend_in
import kinfolk.shared.generated.resources.send_code
import kinfolk.shared.generated.resources.send_sms
import kinfolk.shared.generated.resources.sign_in
import kinfolk.shared.generated.resources.sms_fallback
import kinfolk.shared.generated.resources.their_name
import kinfolk.shared.generated.resources.they_are
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// onb0 and onb1 copied from design v3; L1 Nomor and L2 Kode are the approved sign-in deviation (docs/screen-map.md).

/** Digits of an Indonesian mobile number after +62, however it was typed or pasted. */
fun localDigits(input: String): String = input.filter(Char::isDigit).removePrefix("62").removePrefix("0")
fun e164(local: String) = "+62$local"
/** `812 3456 7890`, as typed in the L1 field. */
fun groupDigits(local: String) = listOf(local.take(3), local.drop(3).take(4), local.drop(7)).filter { it.isNotEmpty() }.joinToString(" ")
/** `+62 812-3456-7890`, as shown in the L2 subtitle. */
fun prettyPhone(local: String) = "+62 " + groupDigits(local).replace(' ', '-')

val PhoneGrouping = VisualTransformation { text ->
    // Spaces go after the 3rd and 7th digit.
    fun spacesBefore(o: Int) = (if (o > 3) 1 else 0) + (if (o > 7) 1 else 0)
    val out = groupDigits(text.text)
    TransformedText(AnnotatedString(out), object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = offset + spacesBefore(offset)
        override fun transformedToOriginal(offset: Int) = (offset - (if (offset > 3) 1 else 0) - (if (offset > 8) 1 else 0)).coerceIn(0, text.length)
    })
}

@Composable
fun Onb0(onCreate: () -> Unit, onInvited: () -> Unit, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 740.dp).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Kinfolk", style = serifStyle(22f, weight = 600).copy(letterSpacing = (-.01).em))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.onb_head), style = serifStyle(40f, 1.05f))
            Text(stringResource(Res.string.onb_sub), fontSize = 16.sp, lineHeight = (16 * 1.5).sp, color = Kf.Ink2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.onb_create), onClick = onCreate)
            Box(
                Modifier.fillMaxWidth().height(54.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(16.dp)).tap(onInvited),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.onb_invited), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Link(stringResource(Res.string.onb_sign_in), Kf.Green, onSignIn, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** L1 Nomor. [send] returns false when the code couldn't be sent. */
@Composable
fun PhoneScreen(phone: String, onPhone: (String) -> Unit, onBack: () -> Unit, send: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    SignInLayout(onBack, stringResource(Res.string.phone_head), stringResource(Res.string.phone_sub)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(Res.string.phone_label)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+62", fontSize = 17.sp)
                    BasicTextField(
                        phone, { onPhone(localDigits(it).take(13)); failed = false },
                        Modifier.weight(1f).padding(start = 12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        visualTransformation = PhoneGrouping,
                        singleLine = true,
                    )
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.send_code)) {
                if (phone.length >= 9 && !busy) scope.launch {
                    busy = true
                    failed = !send()
                    busy = false
                }
            }
            Text(stringResource(Res.string.sms_fallback), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        }
    }
}

/**
 * L2 Kode. [verify] returns true when signed in, false for a wrong code, null when the server couldn't be reached.
 * [send] resends over WhatsApp, or SMS when `sms` is true.
 */
@Composable
fun CodeScreen(phone: String, onBack: () -> Unit, send: suspend (sms: Boolean) -> Boolean, verify: suspend (String) -> Boolean?) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var fails by rememberSaveable { mutableIntStateOf(0) }
    var wrong by rememberSaveable { mutableStateOf(false) }
    var sms by rememberSaveable { mutableStateOf(false) }
    var sends by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(30) }
    var busy by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(sends) {
        while (left > 0) { delay(1000); left-- }
    }
    // The countdown restarts before sending, so the links can't be tapped again meanwhile.
    fun resend(viaSms: Boolean) {
        sms = viaSms
        left = 30
        sends++
        scope.launch { offline = !send(viaSms) }
    }

    SignInLayout(onBack, stringResource(Res.string.code_head), stringResource(if (sms) Res.string.code_sub_sms else Res.string.code_sub, prettyPhone(phone))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(Res.string.code_label)) {
                BasicTextField(
                    code, { code = it.filter(Char::isDigit).take(6); wrong = false; offline = false },
                    Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, textAlign = TextAlign.Center, letterSpacing = .3.em),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            }
            if (wrong) ErrorText(stringResource(Res.string.code_wrong))
            if (offline) ErrorText(stringResource(Res.string.no_connection))
            if (left > 0) Link(stringResource(Res.string.resend_in, "0:" + left.toString().padStart(2, '0')), Kf.Muted, {})
            else {
                Link(stringResource(Res.string.resend), Kf.Green, { resend(sms) })
                if (fails >= 2 && !sms) Link(stringResource(Res.string.send_sms), Kf.Green, { resend(viaSms = true) })
            }
        }
        PrimaryButton(stringResource(Res.string.sign_in)) {
            if (code.length == 6 && !busy) scope.launch {
                busy = true
                when (verify(code)) {
                    false -> { wrong = true; fails++ }
                    null -> offline = true
                    true -> {}
                }
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
/** [onCreate] returns false when the Care Circle couldn't be created. */
fun Onb1(onCreate: suspend (name: String, relation: Relation, needs: Set<Need>) -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf(Relation.Father) }
    var needs by remember { mutableStateOf(setOf(Need.Visits, Need.Medicines)) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val relationLabels = mapOf(
        Relation.Father to Res.string.relation_father, Relation.Mother to Res.string.relation_mother,
        Relation.Grandparent to Res.string.relation_grandparent, Relation.Spouse to Res.string.relation_spouse,
    )
    val needLabels = mapOf(
        Need.Visits to Res.string.need_visits, Need.Medicines to Res.string.need_medicines,
        Need.CheckIns to Res.string.need_check_ins, Need.Paperwork to Res.string.need_paperwork,
    )

    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Kf.Green, Kf.Line, Kf.Line).forEach {
                Box(Modifier.weight(1f).height(4.dp).background(it, RoundedCornerShape(2.dp)))
            }
        }
        Text(stringResource(Res.string.onb1_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.their_name)) {
            BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = 17.sp), singleLine = true)
        }
        Labeled(stringResource(Res.string.they_are)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Relation.entries.forEach { r ->
                    PickChip(stringResource(relationLabels.getValue(r)), r == relation) { relation = r }
                }
            }
        }
        Labeled(stringResource(Res.string.main_need)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Need.entries.forEach { n ->
                    val on = n in needs
                    Chip(stringResource(needLabels.getValue(n)), if (on) Kf.Green else Kf.InputBorder, if (on) Kf.GreenTint else Kf.Card, Kf.Ink) {
                        needs = if (on) needs - n else needs + n
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(Res.string.continue_), Modifier.padding(top = 12.dp)) {
                if (name.isNotBlank() && !busy) scope.launch {
                    busy = true
                    failed = !onCreate(name.trim(), relation, needs)
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}

// design: padding 12px 24px, gap 26 (onb1); back pill from `handoff`; title + subtitle gap 8 (onb2).
@Composable
private fun SignInLayout(onBack: () -> Unit, title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = serifStyle(30f, 1.1f))
            Text(subtitle, fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        content()
    }
}
