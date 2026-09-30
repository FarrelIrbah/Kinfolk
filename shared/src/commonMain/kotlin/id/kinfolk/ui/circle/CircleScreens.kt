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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Role
import id.kinfolk.ui.Avatar
import id.kinfolk.ui.Card
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Link
import id.kinfolk.ui.Pill
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back_circle
import kinfolk.shared.generated.resources.circle_name
import kinfolk.shared.generated.resources.contacts
import kinfolk.shared.generated.resources.contacts_sub
import kinfolk.shared.generated.resources.leave
import kinfolk.shared.generated.resources.make_admin
import kinfolk.shared.generated.resources.member_joined
import kinfolk.shared.generated.resources.member_you
import kinfolk.shared.generated.resources.more_ways
import kinfolk.shared.generated.resources.remove_member
import kinfolk.shared.generated.resources.role_admin
import kinfolk.shared.generated.resources.role_parent
import kinfolk.shared.generated.resources.role_sibling
import kinfolk.shared.generated.resources.role_viewer
import kinfolk.shared.generated.resources.sos
import kinfolk.shared.generated.resources.tab_circle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** A current Member as `circle` and `member` show them. */
class CircleMember(val id: String, val name: String, val color: Color, val role: Role, val joined: LocalDate, val isMe: Boolean)

@Composable
private fun CircleMember.roleText() = stringResource(
    when (role) {
        Role.admin -> Res.string.role_admin
        Role.sibling -> Res.string.role_sibling
        Role.parent -> Res.string.role_parent
        Role.viewer -> Res.string.role_viewer
    },
)

/** "Anda · pengatur", "Anak" (approved in #5). */
@Composable
private fun CircleMember.sub() = if (isMe) stringResource(Res.string.member_you, roleText()) else roleText().replaceFirstChar { it.uppercase() }

/**
 * `circle` from design v3: header, Members, "Cara lain". Approved in #5: the permission intro, access column and
 * Care Recipient row wait for #9; "Paket" and "Ulangi onboarding" are hidden.
 */
@Composable
fun CircleScreen(circleName: String, members: List<CircleMember>, onSos: () -> Unit, onMember: (CircleMember) -> Unit, onContacts: () -> Unit) {
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
        if (members.isNotEmpty()) Card {
            members.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().tap { onMember(m) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m.name.take(1), m.color, 38.dp, 14.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(m.sub(), fontSize = 13.sp, color = Kf.Muted)
                    }
                    Text("›", color = Kf.Muted, fontSize = 18.sp)
                }
                Hairline()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(Res.string.more_ways))
            Card {
                Row(Modifier.fillMaxWidth().tap(onContacts).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(Res.string.contacts), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(Res.string.contacts_sub), fontSize = 12.sp, color = Kf.Muted)
                    }
                    Text("›", color = Kf.Muted, fontSize = 18.sp)
                }
                Hairline()
            }
        }
    }
}

/**
 * `member` from design v3, header only. Approved in #5: "Bisa melihat" (#9), "Notifikasi" (#13), the change history
 * and footnote are hidden; admins get "Jadikan pengatur" and "Keluarkan dari lingkaran" on others, everyone
 * "Keluar dari lingkaran" on themselves, each a confirm sheet except promoting.
 */
@Composable
fun MemberScreen(m: CircleMember, iAmAdmin: Boolean, error: String?, onBack: () -> Unit, onPromote: () -> Unit, onRemove: () -> Unit, onLeave: () -> Unit) {
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
