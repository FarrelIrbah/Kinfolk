package id.kinfolk.ui.circle

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import id.kinfolk.ui.HintedInput
import kinfolk.shared.generated.resources.emergency_contact
import kinfolk.shared.generated.resources.emergency_contact_sub
import kinfolk.shared.generated.resources.jarak
import kinfolk.shared.generated.resources.jarak_hint
import androidx.compose.ui.unit.sp
import id.kinfolk.data.DataCategory
import id.kinfolk.data.Role
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Card
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Link
import id.kinfolk.ui.Pill
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.Switch
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.access_full
import kinfolk.shared.generated.resources.access_part
import kinfolk.shared.generated.resources.back_circle
import kinfolk.shared.generated.resources.can_see
import kinfolk.shared.generated.resources.perm_foot
import kinfolk.shared.generated.resources.change_shown
import kinfolk.shared.generated.resources.change_hidden
import kinfolk.shared.generated.resources.no_changes
import kinfolk.shared.generated.resources.change_history
import kinfolk.shared.generated.resources.cat_money_desc
import kinfolk.shared.generated.resources.cat_money
import kinfolk.shared.generated.resources.cat_wishes_desc
import kinfolk.shared.generated.resources.cat_wishes
import kinfolk.shared.generated.resources.cat_documents_desc
import kinfolk.shared.generated.resources.cat_documents
import kinfolk.shared.generated.resources.cat_appointments
import kinfolk.shared.generated.resources.cat_appointments_desc
import kinfolk.shared.generated.resources.cat_medications
import kinfolk.shared.generated.resources.cat_medications_desc
import kinfolk.shared.generated.resources.cat_visit_notes
import kinfolk.shared.generated.resources.cat_visit_notes_desc
import kinfolk.shared.generated.resources.perm_intro
import kinfolk.shared.generated.resources.recipient_sub
import kinfolk.shared.generated.resources.channel_app
import kinfolk.shared.generated.resources.channel_whatsapp
import kinfolk.shared.generated.resources.plan
import kinfolk.shared.generated.resources.replay_onb
import kinfolk.shared.generated.resources.bp_mode
import kinfolk.shared.generated.resources.bp_mode_sub
import kinfolk.shared.generated.resources.circle_name
import kinfolk.shared.generated.resources.contacts
import kinfolk.shared.generated.resources.contacts_sub
import kinfolk.shared.generated.resources.leave
import kinfolk.shared.generated.resources.make_admin
import kinfolk.shared.generated.resources.member_joined
import kinfolk.shared.generated.resources.member_you
import kinfolk.shared.generated.resources.more_ways
import kinfolk.shared.generated.resources.a11y_sub
import kinfolk.shared.generated.resources.a11y_title
import kinfolk.shared.generated.resources.notes_sub
import kinfolk.shared.generated.resources.export_title
import kinfolk.shared.generated.resources.export_short
import kinfolk.shared.generated.resources.digest_tool
import kinfolk.shared.generated.resources.digest_tool_sub
import kinfolk.shared.generated.resources.notes_title
import kinfolk.shared.generated.resources.remove_member
import kinfolk.shared.generated.resources.role_admin
import kinfolk.shared.generated.resources.role_parent
import kinfolk.shared.generated.resources.role_sibling
import kinfolk.shared.generated.resources.role_viewer
import kinfolk.shared.generated.resources.sos
import kinfolk.shared.generated.resources.tab_circle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A current Member as `circle` and `member` show them. [sees] are the Data Categories they can see; [isRecipient]
 * when they are the Care Recipient, who sees everything and sets restrictions (ADR 0004).
 */
class CircleMember(
    val id: String, val name: String, val color: Color, val role: Role, val joined: LocalDate, val isMe: Boolean,
    val sees: Set<DataCategory> = DataCategory.entries.toSet(), val isRecipient: Boolean = false,
    val emergency: Boolean = false, val distance: String = "", val inApp: Boolean = true,
)

/** `member` rows: v3's six (#20). */
val DataCategory.label: StringResource get() = when (this) {
    DataCategory.appointments -> Res.string.cat_appointments
    DataCategory.visit_notes -> Res.string.cat_visit_notes
    DataCategory.medications -> Res.string.cat_medications
    DataCategory.documents -> Res.string.cat_documents
    DataCategory.wishes -> Res.string.cat_wishes
    DataCategory.money -> Res.string.cat_money
}
private val DataCategory.desc get() = when (this) {
    DataCategory.appointments -> Res.string.cat_appointments_desc
    DataCategory.visit_notes -> Res.string.cat_visit_notes_desc
    DataCategory.medications -> Res.string.cat_medications_desc
    DataCategory.documents -> Res.string.cat_documents_desc
    DataCategory.wishes -> Res.string.cat_wishes_desc
    DataCategory.money -> Res.string.cat_money_desc
}

/** A "Riwayat perubahan" line: [category] hidden or shared, over [meta] ("Baru saja · oleh Sri"). */
class HistoryLine(val category: DataCategory, val hidden: Boolean, val meta: String)

@Composable
private fun CircleMember.roleText() = stringResource(
    when (role) {
        Role.admin -> Res.string.role_admin
        Role.sibling -> Res.string.role_sibling
        Role.parent -> Res.string.role_parent
        Role.viewer -> Res.string.role_viewer
    },
)

/** "Anda · pengatur" (#5); the Care Recipient "Penerima perawatan · menentukan akses" (#9); others their channel (#36). */
@Composable
private fun CircleMember.sub() = when {
    isRecipient -> stringResource(Res.string.recipient_sub)
    isMe -> stringResource(Res.string.member_you, roleText())
    else -> stringResource(if (inApp) Res.string.channel_app else Res.string.channel_whatsapp)
}

/**
 * `circle` from design v3: header, Members, "Cara lain", "Paket" with [plan] as its sub (#50). Approved in #9: the intro
 * box only while the Care Recipient [recipientName] is a Member; access "Penuh" or "N/6" (#20). #36: the Care
 * Recipient's row always first, no access column; "Ulangi onboarding" while [onReplay] is set (admins). #37: the Care
 * Recipient's row and "Cara lain"'s first row open Mode Bapak ([onBapak]), as in v3. #40: "Tampilan WhatsApp Anda" after
 * the export row while [onDigest] is set (after my first weekly digest). #41: "Tampilan & aksesibilitas" last, as in v3.
 */
@Composable
fun CircleScreen(circleName: String, recipientName: String, members: List<CircleMember>, onSos: () -> Unit, onMember: (CircleMember) -> Unit, onContacts: () -> Unit, onNotes: () -> Unit, onExport: () -> Unit, onReplay: (() -> Unit)?, onBapak: () -> Unit, onDigest: (() -> Unit)?, onDisplay: () -> Unit, plan: String, onPlan: () -> Unit) {
    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.circle_name, circleName, members.size), fontSize = 13.sp, color = Kf.Muted)
                Text(stringResource(Res.string.tab_circle), style = serifStyle(30f, 1.1f))
            }
            Box(Modifier.height(44.dp).background(Kf.Sos, CircleShape).tap(onSos).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.sos), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em)
            }
        }
        // design: 14px/1.5 #44463E on #E9E2D4, radius 14, padding 12px 14px
        if (members.any { it.isRecipient }) Text(
            stringResource(Res.string.perm_intro, recipientName), fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Ink2,
            modifier = Modifier.fillMaxWidth().background(Kf.CardAlt, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        )
        if (members.isNotEmpty()) Card {
            @Composable
            fun row(name: String, color: Color, sub: String, access: String, open: () -> Unit) {
                Row(
                    Modifier.fillMaxWidth().tap(open).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(name.take(1), color, 38.dp, 14.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(sub, fontSize = 13.sp, color = Kf.Muted)
                    }
                    Text(access, fontSize = 12.sp, color = Kf.Muted)
                    Text("›", color = Kf.Muted, fontSize = 18.sp)
                }
                Hairline()
            }
            if (members.none { it.isRecipient }) row(recipientName, Kf.Ink, stringResource(Res.string.recipient_sub), "", onBapak)
            members.forEach { m ->
                row(
                    m.name, m.color, m.sub(),
                    when {
                        m.isRecipient -> ""
                        m.sees.size == DataCategory.entries.size -> stringResource(Res.string.access_full)
                        else -> stringResource(Res.string.access_part, m.sees.size, DataCategory.entries.size)
                    },
                ) { if (m.isRecipient) onBapak() else onMember(m) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(Res.string.more_ways))
            Card {
                listOfNotNull(
                    Triple(stringResource(Res.string.bp_mode, recipientName), stringResource(Res.string.bp_mode_sub, recipientName), onBapak),
                    Triple(stringResource(Res.string.contacts), stringResource(Res.string.contacts_sub), onContacts),
                    Triple(stringResource(Res.string.notes_title), stringResource(Res.string.notes_sub), onNotes),
                    Triple(stringResource(Res.string.export_title), stringResource(Res.string.export_short), onExport),
                    onDigest?.let { Triple(stringResource(Res.string.digest_tool), stringResource(Res.string.digest_tool_sub), it) },
                    Triple(stringResource(Res.string.a11y_title), stringResource(Res.string.a11y_sub), onDisplay),
                ).forEach { (title, sub, open) ->
                    Row(Modifier.fillMaxWidth().tap(open).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(sub, fontSize = 12.sp, color = Kf.Muted)
                        }
                        Text("›", color = Kf.Muted, fontSize = 18.sp)
                    }
                    Hairline()
                }
            }
        }
        // design: #FBF8F2, radius 18, padding 16; 16/600 over 13px muted, "›"
        Row(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap(onPlan).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.plan), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(plan, fontSize = 13.sp, color = Kf.Muted)
            }
            Text("›", color = Kf.Muted, fontSize = 18.sp)
        }
        // design: border:none;background:none;color:#6B6A60;font-size:13px;padding:0 (centred button)
        if (onReplay != null) Text(
            stringResource(Res.string.replay_onb), fontSize = 13.sp, color = Kf.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).tap(onReplay),
        )
    }
}

/**
 * `member` from design v3. Approved in #5: "Notifikasi" is hidden (#13); admins get "Jadikan pengatur" and
 * "Keluarkan dari lingkaran" on others, everyone "Keluar dari lingkaran" on themselves, each a confirm sheet except
 * promoting. Approved in #9: "Bisa melihat" for admins and the Care Recipient, hidden while [onToggle] is null; a
 * tap passes the category and whether it was on. With it (#20), "Riwayat perubahan" ([history], newest first) and
 * the footnote. Approved in #34: for admins ([onEmergency] set), a card under the header with "Kontak darurat" in the
 * rows' style and, while on, "Jarak dari rumah" in the `channel` row's style; the distance saves when the field is left.
 */
@Composable
fun MemberScreen(
    m: CircleMember, iAmAdmin: Boolean, error: String?, onBack: () -> Unit, onPromote: () -> Unit, onRemove: () -> Unit, onLeave: () -> Unit,
    onToggle: ((DataCategory, Boolean) -> Unit)?, history: List<HistoryLine>, onEmergency: ((on: Boolean, distance: String) -> Unit)?,
) {
    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill(stringResource(Res.string.back_circle), onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name.take(1), m.color, 56.dp, 22.sp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(m.name, style = serifStyle(26f, 1.1f))
                Text(
                    if (m.isMe) m.sub() else stringResource(Res.string.member_joined, m.roleText().replaceFirstChar { it.uppercase() }, dayMonth(m.joined)),
                    fontSize = 13.sp, color = Kf.Muted,
                )
            }
        }
        if (onEmergency != null) {
            var distance by remember(m.id, m.distance) { mutableStateOf(m.distance) }
            Card {
                Row(
                    Modifier.fillMaxWidth().tap { onEmergency(!m.emergency, distance) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(Res.string.emergency_contact), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(Res.string.emergency_contact_sub), fontSize = 12.sp, color = Kf.Muted)
                    }
                    Switch(m.emergency)
                }
                // design (`channel` row): padding 14px 16px, 15px label, value 14/600
                if (m.emergency) {
                    Hairline()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.jarak), fontSize = 15.sp)
                        val focus = LocalFocusManager.current
                        HintedInput(
                            distance, { distance = it }, stringResource(Res.string.jarak_hint),
                            LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = Kf.Ink),
                            Modifier.weight(1f).onFocusChanged { if (!it.isFocused && m.emergency && distance.trim() != m.distance) onEmergency(true, distance.trim()) },
                            singleLine = true, keyboardActions = KeyboardActions { focus.clearFocus() },
                        )
                    }
                }
            }
        }
        // design: rows padding 14px 16px, gap 12; label 15/500, desc 12 muted
        if (onToggle != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(Res.string.can_see))
            Card {
                DataCategory.entries.forEach { c ->
                    val on = c in m.sees
                    Row(
                        Modifier.fillMaxWidth().tap { onToggle(c, on) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(c.label), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(c.desc), fontSize = 12.sp, color = Kf.Muted)
                        }
                        Switch(on)
                    }
                    Hairline()
                }
            }
        }
        if (onToggle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.change_history))
                // design: rows padding 12px 16px, gap 2; text 14, meta 12 muted
                Card {
                    history.forEach { l ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(if (l.hidden) Res.string.change_hidden else Res.string.change_shown, stringResource(l.category.label)), fontSize = 14.sp)
                            Text(l.meta, fontSize = 12.sp, color = Kf.Muted)
                        }
                        Hairline()
                    }
                    if (history.isEmpty()) Text(
                        stringResource(Res.string.no_changes), fontSize = 14.sp, color = Kf.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            // design: 13px/1.5 muted
            Text(stringResource(Res.string.perm_foot), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                m.isMe -> Link(stringResource(Res.string.leave), Kf.Sos, onLeave)
                iAmAdmin -> {
                    if (m.role != Role.admin) Link(stringResource(Res.string.make_admin), Kf.Ink, onPromote)
                    Link(stringResource(Res.string.remove_member), Kf.Sos, onRemove)
                }
            }
            error?.let { ErrorText(it) }
        }
    }
}
