package id.kinfolk.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Invitation
import id.kinfolk.data.InvitationToMe
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Card
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.contacts.localPhone
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_person
import kinfolk.shared.generated.resources.cancel_invite
import kinfolk.shared.generated.resources.inv_head
import kinfolk.shared.generated.resources.inv_sub
import kinfolk.shared.generated.resources.invite
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.onb2_head
import kinfolk.shared.generated.resources.onb2_note
import kinfolk.shared.generated.resources.onb2_sub
import kinfolk.shared.generated.resources.open_circle
import kinfolk.shared.generated.resources.send_invites
import kinfolk.shared.generated.resources.skip_for_now
import kinfolk.shared.generated.resources.will_send
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// onb2 and invitee from design v3, with the deviations approved in #4 (docs/screen-map.md).

/** Budi, Dewi, Agus, Rina: the prototype's sibling colors, in its invite-list order. */
/** Avatar colors of Budi, Dewi, Agus and Rina in the prototype: everyone but yourself. */
val InviteColors = listOf(Color(0xFFB0643A), Color(0xFF6C5A8E), Color(0xFF3E6E8E), Color(0xFF9A7A2F))

private data class NewInvite(val name: String, val local: String, val send: Boolean = true)

private fun complete(name: String, local: String) = name.isNotBlank() && local.length >= 9

/**
 * onb2. [pending] Invitations can be cancelled; new people are added under "+ Tambah orang lain". [send] invites one
 * person (name, E.164) and [cancel] one Invitation; both return false when offline. [onDone] leaves for Home.
 */
@Composable
fun Onb2(
    pending: List<Invitation>,
    cancel: suspend (Invitation) -> Boolean,
    send: suspend (String, String) -> Boolean,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(pending) }
    var added by remember { mutableStateOf(emptyList<NewInvite>()) }
    var drafting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var local by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val draft = NewInvite(name.trim(), local).takeIf { drafting && complete(name, local) }
    val toSend = added.filter { it.send } + listOfNotNull(draft)
    fun run(action: suspend () -> Boolean) {
        if (!busy) scope.launch { busy = true; failed = !action(); busy = false }
    }

    // design: padding 12px 24px, gap 22
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Kf.Green, Kf.Green, Kf.Line).forEach {
                Box(Modifier.weight(1f).height(4.dp).background(it, RoundedCornerShape(2.dp)))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.onb2_head), style = serifStyle(30f, 1.1f))
            Text(stringResource(Res.string.onb2_sub), fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2)
        }
        Card {
            waiting.forEachIndexed { i, inv ->
                InviteRow(inv.name, localPhone(inv.phone), InviteColors[i % 4], stringResource(Res.string.cancel_invite), on = false) {
                    run { cancel(inv).also { if (it) waiting = waiting - inv } }
                }
            }
            added.forEachIndexed { i, p ->
                val label = stringResource(if (p.send) Res.string.will_send else Res.string.invite)
                InviteRow(p.name, localPhone(e164(p.local)), InviteColors[(waiting.size + i) % 4], label, p.send) {
                    added = added.toMutableList().also { it[i] = p.copy(send = !p.send) }
                }
            }
            Text(
                stringResource(Res.string.add_person), fontSize = 15.sp, color = Kf.Green, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().tap {
                    if (draft != null) { added = added + draft; name = ""; local = "" } else drafting = true
                }.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (drafting) {
                val input = LocalTextStyle.current.copy(fontSize = 17.sp)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("") { BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
                    Field("") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("+62", fontSize = 17.sp)
                            BasicTextField(
                                local, { local = localDigits(it).take(13) }, Modifier.weight(1f).padding(start = 12.dp), textStyle = input,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), visualTransformation = PhoneGrouping, singleLine = true,
                            )
                        }
                    }
                }
            }
        }
        Text(stringResource(Res.string.onb2_note), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cta = if (toSend.isEmpty()) stringResource(Res.string.skip_for_now) else stringResource(Res.string.send_invites, toSend.size)
            PrimaryButton(cta) {
                // Sent people leave the list one by one, so a retry after going offline sends only the rest.
                run {
                    for (p in toSend) {
                        if (!send(p.name, e164(p.local))) return@run false
                        if (p === draft) { drafting = false; name = ""; local = "" } else added = added - p
                    }
                    onDone()
                    true
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}

// design: padding 14px 16px, gap 12, avatar 36, name 16/500, phone 13 muted, pill 8px 14px 13/600
@Composable
private fun InviteRow(name: String, phone: String, color: Color, label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name.take(1), color, 36.dp, 14.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(phone, fontSize = 13.sp, color = Kf.Muted)
        }
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Kf.Paper else Kf.Ink,
            modifier = Modifier.background(if (on) Kf.Green else Kf.Sand, CircleShape).tap(onToggle).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
    Hairline()
}

/** `invitee`: head and button only until last visit (#7), Duty ask (#10) and sharing (#9) land. [accept] returns false when offline. */
@Composable
fun Invitee(invitation: InvitationToMe, accept: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // design: padding 4px 20px, gap 18
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Kinfolk", style = serifStyle(20f, weight = 600))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.inv_head, invitation.name), style = serifStyle(32f, 1.1f))
            Text(
                stringResource(Res.string.inv_sub, invitation.inviter.orEmpty(), invitation.circle),
                fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Kf.Ink2,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(Res.string.open_circle)) {
                if (!busy) scope.launch { busy = true; failed = !accept(); busy = false }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}
