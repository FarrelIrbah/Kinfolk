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
import id.kinfolk.data.CareContact
import id.kinfolk.data.EmergencyCard
import id.kinfolk.data.Medication
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.LightStatusBarIcons
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.contacts.localPhone
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
import kinfolk.shared.generated.resources.share_link
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** "Amlodipine 5 mg · Clopidogrel 75 mg", as the emergency web page lists them. */
fun List<Medication>.emergencyLine() = filter { it.active }.joinToString(" · ") { "${it.name} ${it.dose}".trim() }

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

/**
 * `emergency`. Approved deviation (#12): only ADR 0003's fixed fields, so no age line, blood-thinner banner or "Keinginan";
 * Alergi is one full-width tile; empty sections are hidden; "Ubah" opens the form. [updated]: age of the offline copy (#14).
 */
@Composable
fun EmergencyScreen(
    name: String, allergies: String, conditions: String, meds: List<Medication>, contacts: List<CareContact>, card: EmergencyCard?, updated: String?,
    onClose: () -> Unit, onEdit: () -> Unit, onQr: () -> Unit,
) {
    LightStatusBarIcons()
    val uri = LocalUriHandler.current
    val ink = Kf.NightInk
    // design: padding 60px (under the status bar) 20px 40px; gap 14
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(Res.string.em_title), 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightPill(stringResource(Res.string.edit), onEdit)
                NightPill(stringResource(Res.string.close), onClose)
            }
        }
        Text(name, style = serifStyle(40f, 1.05f).copy(color = ink))
        if (allergies.isNotBlank()) Tile(stringResource(Res.string.allergies), 14.dp, 4.dp) {
            Text(allergies, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
        if (conditions.isNotBlank()) Tile(stringResource(Res.string.conditions), 16.dp, 6.dp) {
            Text(conditions, fontSize = 16.sp, lineHeight = (16 * 1.45).sp, color = ink)
        }
        meds.emergencyLine().takeIf { it.isNotEmpty() }?.let { line ->
            Tile(stringResource(Res.string.current_meds), 16.dp, 6.dp) { Text(line, fontSize = 16.sp, lineHeight = (16 * 1.55).sp, color = ink) }
        }
        contacts.filter { it.emergency }.takeIf { it.isNotEmpty() }?.let { list ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().background(Kf.NightTile, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink)
                            Text(
                                listOf(c.relationship, localPhone(c.phone)).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 13.sp, color = ink, modifier = Modifier.alpha(.7f),
                            )
                        }
                        Box(Modifier.height(44.dp).background(ink, CircleShape).tap { uri.openUri("tel:${c.phone}") }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.call), color = Kf.Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
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
 * `qr`. Approved deviation (#12): no age line or blood-thinner banner on the card; "Bagikan tautan" shares the link
 * instead of printing; "Cabut & buat baru" is for admins only. [revoke] returns false when offline.
 */
@Composable
fun QrScreen(name: String, allergies: String, card: EmergencyCard, scanned: String, admin: Boolean, onBack: () -> Unit, onShare: () -> Unit, revoke: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val ink = Kf.NightInk
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
                    Text(name, style = serifStyle(24f, 1.1f).copy(color = ink))
                }
                if (allergies.isNotBlank()) Text("${stringResource(Res.string.allergies)}: $allergies", fontSize = 11.sp, color = ink)
            }
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(ink, RoundedCornerShape(6.dp)).padding(6.dp)) { Qr(card.url, 96.dp) }
                Text(stringResource(Res.string.qr_scan), fontSize = 10.sp, color = ink, modifier = Modifier.alpha(.75f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(Res.string.share_link), Modifier.weight(1f), onShare)
                if (admin) Box(
                    Modifier.weight(1f).height(54.dp).border(1.dp, Kf.InputBorder, RoundedCornerShape(16.dp)).tap {
                        if (!busy) scope.launch { busy = true; failed = !revoke(); busy = false }
                    },
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

/** Approved deviation (#12): Alergi and Kondisi on the onb1 layout, like the Obat form. [save] returns false when offline. */
@Composable
fun EmergencyFormScreen(allergies: String, conditions: String, onBack: () -> Unit, save: suspend (allergies: String, conditions: String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var a by rememberSaveable { mutableStateOf(allergies) }
    var c by rememberSaveable { mutableStateOf(conditions) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.em_title), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.allergies), multiline = true) { BasicTextField(a, { a = it }, Modifier.fillMaxWidth(), textStyle = input) }
        Field(stringResource(Res.string.conditions), multiline = true) { BasicTextField(c, { c = it }, Modifier.fillMaxWidth(), textStyle = input) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                if (!busy) scope.launch {
                    busy = true
                    failed = !save(a.trim(), c.trim())
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}
