package id.kinfolk.ui.emergency

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.EmergencyCard
import id.kinfolk.data.EmergencyDraft
import id.kinfolk.data.EmergencyInfo
import id.kinfolk.data.ageLine
import id.kinfolk.data.bloodLine
import id.kinfolk.data.sub
import id.kinfolk.ui.HintedInput
import id.kinfolk.ui.appointment.DayPicker
import id.kinfolk.ui.appointment.fullDate
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.LightStatusBarIcons
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.allergies
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.call
import kinfolk.shared.generated.resources.close
import kinfolk.shared.generated.resources.conditions
import kinfolk.shared.generated.resources.current_meds
import kinfolk.shared.generated.resources.edit
import kinfolk.shared.generated.resources.em_offline
import kinfolk.shared.generated.resources.em_title
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.qr_body
import kinfolk.shared.generated.resources.qr_head
import kinfolk.shared.generated.resources.qr_meta
import kinfolk.shared.generated.resources.qr_revoke
import kinfolk.shared.generated.resources.qr_scan
import kinfolk.shared.generated.resources.qr_sub
import kinfolk.shared.generated.resources.qr_title
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.born_on
import kinfolk.shared.generated.resources.weight
import kinfolk.shared.generated.resources.wishes
import kinfolk.shared.generated.resources.wishes_hint
import kinfolk.shared.generated.resources.qr_print
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The prototype's 21x21 QR, drawn in the card's ink. */
@Composable
private fun Qr(url: String, size: Dp) = Image(
    rememberQrCodePainter(url) { colors { dark = QrBrush.solid(Kf.Night) } }, null, Modifier.size(size),
)

@Composable
private fun Eyebrow(text: String, size: Int) =
    Text(text.uppercase(), fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em, color = Kf.Peach)

@Composable
private fun NightPill(text: String, onClick: () -> Unit) {
    Box(Modifier.height(40.dp).background(Kf.NightPill, CircleShape).tap(onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Kf.NightInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A tile on `emergency`: rgba(255,248,238,.08), radius 14, label 12px at .7. */
@Composable
private fun Tile(label: String, horizontal: Dp, gap: Dp, value: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Kf.NightTile, RoundedCornerShape(14.dp)).padding(horizontal = horizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(label, fontSize = 12.sp, color = Kf.NightInk, modifier = Modifier.alpha(.7f))
        value()
    }
}

/** A tile on `emergency`: rgba(255,248,238,.08), radius 14, label 12px at .7; [modifier] sizes it. */
@Composable
private fun Tile(label: String, horizontal: Dp, gap: Dp, modifier: Modifier = Modifier.fillMaxWidth(), value: @Composable () -> Unit) {
    Column(
        modifier.background(Kf.NightTile, RoundedCornerShape(14.dp)).padding(horizontal = horizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(label, fontSize = 12.sp, color = Kf.NightInk, modifier = Modifier.alpha(.7f))
        value()
    }
}

/**
 * v3 `emergency` from [info], the QR page's summary (ADR 0003). Approved deviations (#12): empty sections are hidden (a
 * lone Alergi or Keinginan fills the row); "Ubah" opens the form. [updated]: age of the offline copy (#14).
 */
@Composable
fun EmergencyScreen(
    info: EmergencyInfo, today: LocalDate, card: EmergencyCard?, updated: String?,
    onClose: () -> Unit, onEdit: () -> Unit, onQr: () -> Unit,
) {
    LightStatusBarIcons()
    val uri = LocalUriHandler.current
    val ink = Kf.NightInk
    val age = info.ageLine(today)
    val blood = info.bloodLine()
    // design: padding 60px (under the status bar) 20px 40px; gap 14
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(Res.string.em_title), 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightPill(stringResource(Res.string.edit), onEdit)
                NightPill(stringResource(Res.string.close), onClose)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(info.name, style = serifStyle(40f, 1.05f).copy(color = ink))
            if (age.isNotEmpty()) Text(age, fontSize = 17.sp, color = ink, modifier = Modifier.alpha(.85f))
        }
        if (blood.isNotEmpty()) Text(
            blood, Modifier.fillMaxWidth().background(Kf.Rust, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = (19 * 1.3).sp, color = ink,
        )
        val pair = listOf(Res.string.allergies to info.allergies, Res.string.wishes to info.wishes).filter { it.second.isNotBlank() }
        if (pair.isNotEmpty()) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (label, value) ->
                Tile(stringResource(label), 14.dp, 4.dp, Modifier.weight(1f).fillMaxHeight()) {
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
                }
            }
        }
        if (info.conditions.isNotBlank()) Tile(stringResource(Res.string.conditions), 16.dp, 6.dp) {
            Text(info.conditions, fontSize = 16.sp, lineHeight = (16 * 1.45).sp, color = ink)
        }
        if (info.medications.isNotEmpty()) Tile(stringResource(Res.string.current_meds), 16.dp, 6.dp) {
            Text(info.medications.joinToString(" · "), fontSize = 16.sp, lineHeight = (16 * 1.55).sp, color = ink)
        }
        if (info.contacts.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info.contacts.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().background(Kf.NightTile, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Text(c.sub(), fontSize = 13.sp, color = ink, modifier = Modifier.alpha(.7f))
                    }
                    Box(Modifier.height(44.dp).background(ink, CircleShape).tap { uri.openUri("tel:${c.phone}") }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.call), color = Kf.Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        card?.let {
            Row(
                Modifier.fillMaxWidth().background(ink, RoundedCornerShape(14.dp)).tap(onQr).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Qr(it.url, 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.qr_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Kf.Night)
                    Text(stringResource(Res.string.qr_sub), fontSize = 13.sp, lineHeight = (13 * 1.4).sp, color = Kf.Night, modifier = Modifier.alpha(.75f))
                }
            }
        }
        updated?.let {
            Text(
                stringResource(Res.string.em_offline, it), Modifier.fillMaxWidth().padding(top = 4.dp).alpha(.6f),
                fontSize = 12.sp, color = ink, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * v3 `qr`. Approved deviations (#12): "Alergi: …" only when filled in, "Cabut & buat baru" for admins only. [print]
 * opens the system print dialog (#34) and returns false when offline; [revoke] too.
 */
@Composable
fun QrScreen(info: EmergencyInfo, today: LocalDate, card: EmergencyCard, scanned: String, admin: Boolean, onBack: () -> Unit, print: suspend () -> Boolean, revoke: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val ink = Kf.NightInk
    val age = info.ageLine(today)
    val blood = info.bloodLine()
    fun run(action: suspend () -> Boolean) { if (!busy) scope.launch { busy = true; failed = !action(); busy = false } }
    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.qr_head), style = serifStyle(30f, 1.1f))
            Text(stringResource(Res.string.qr_body), fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        Row(
            Modifier.fillMaxWidth().aspectRatio(1.586f)
                .dropShadow(RoundedCornerShape(16.dp), Shadow(30.dp, Color(0x4015130F), offset = DpOffset(0.dp, 12.dp)))
                .background(Kf.Night, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Eyebrow(stringResource(Res.string.em_title), 10)
                    Text(info.name, style = serifStyle(24f, 1.1f).copy(color = ink))
                    if (age.isNotEmpty()) Text(age, fontSize = 11.sp, color = ink, modifier = Modifier.alpha(.8f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (blood.isNotEmpty()) Text(
                        blood, Modifier.fillMaxWidth().background(Kf.Rust, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = (11 * 1.3).sp, color = ink,
                    )
                    if (info.allergies.isNotBlank()) Text("${stringResource(Res.string.allergies)}: ${info.allergies}", fontSize = 11.sp, color = ink)
                }
            }
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(ink, RoundedCornerShape(6.dp)).padding(6.dp)) { Qr(card.url, 96.dp) }
                Text(stringResource(Res.string.qr_scan), fontSize = 10.sp, color = ink, modifier = Modifier.alpha(.75f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(Res.string.qr_print), Modifier.weight(1f)) { run(print) }
                if (admin) Box(
                    Modifier.weight(1f).height(54.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(16.dp)).tap { run(revoke) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.qr_revoke), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
        Text(stringResource(Res.string.qr_meta, card.version, scanned), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
    }
}

/**
 * Approved deviations (#12, #34): the onb1 layout like the Obat form, "Tanggal lahir" (the appointment calendar),
 * "Berat badan" with "kg" after it, "Alergi", "Keinginan" (hint "Tindakan penuh"), "Kondisi". [save] returns false when offline.
 */
@Composable
fun EmergencyFormScreen(start: EmergencyDraft, onBack: () -> Unit, save: suspend (EmergencyDraft) -> Boolean) {
    val scope = rememberCoroutineScope()
    var born by rememberSaveable { mutableStateOf(start.bornOn?.toString()) }
    var weight by rememberSaveable { mutableStateOf(start.weightKg?.toString().orEmpty()) }
    var a by rememberSaveable { mutableStateOf(start.allergies) }
    var w by rememberSaveable { mutableStateOf(start.wishes) }
    var c by rememberSaveable { mutableStateOf(start.conditions) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.em_title), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.born_on), Modifier.tap { picking = true }) {
            Text(born?.let { fullDate(LocalDate.parse(it)) }.orEmpty(), fontSize = 17.sp)
        }
        Field(stringResource(Res.string.weight)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    weight, { v -> weight = v.filter { it.isDigit() }.take(3) }, Modifier.weight(1f), textStyle = input, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("kg", fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Field(stringResource(Res.string.allergies), multiline = true) { BasicTextField(a, { a = it }, Modifier.fillMaxWidth(), textStyle = input) }
        Field(stringResource(Res.string.wishes), multiline = true) { HintedInput(w, { w = it }, stringResource(Res.string.wishes_hint), input) }
        Field(stringResource(Res.string.conditions), multiline = true) { BasicTextField(c, { c = it }, Modifier.fillMaxWidth(), textStyle = input) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                if (!busy) scope.launch {
                    busy = true
                    failed = !save(EmergencyDraft(born?.let(LocalDate::parse), weight.toIntOrNull()?.takeIf { it > 0 }, a.trim(), w.trim(), c.trim()))
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
    if (picking) DayPicker(born?.let(LocalDate::parse), { born = it.toString(); picking = false }) { picking = false }
}
