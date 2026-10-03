package id.kinfolk.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.CareContact
import id.kinfolk.data.ContactGroup
import id.kinfolk.ui.DashedButton
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Card
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Link
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.SvgPath
import id.kinfolk.ui.Toggle
import id.kinfolk.ui.onboarding.PhoneGrouping
import id.kinfolk.ui.onboarding.e164
import id.kinfolk.ui.onboarding.groupDigits
import id.kinfolk.ui.onboarding.localDigits
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_contact
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.contact_form_head
import kinfolk.shared.generated.resources.contact_name
import kinfolk.shared.generated.resources.contact_phone
import kinfolk.shared.generated.resources.contacts
import kinfolk.shared.generated.resources.emergency_contact
import kinfolk.shared.generated.resources.group_emergency
import kinfolk.shared.generated.resources.group_home
import kinfolk.shared.generated.resources.group_medical
import kinfolk.shared.generated.resources.med_note
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.relationship
import kinfolk.shared.generated.resources.remove_contact
import kinfolk.shared.generated.resources.save
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val ContactGroup.label: StringResource
    get() = when (this) {
        ContactGroup.Medical -> Res.string.group_medical
        ContactGroup.Home -> Res.string.group_home
        ContactGroup.Emergency -> Res.string.group_emergency
    }

/** Digits after +62 as typed. Only a pasted +62 number loses its 62, so landlines like 0622 (Pematangsiantar) can be typed. */
fun typedPhone(input: String) = (if (input.startsWith("+")) localDigits(input) else input.filter(Char::isDigit).removePrefix("0")).take(13)

/** "0812 3456 7890" */
fun localPhone(e164: String) = "0" + groupDigits(localDigits(e164))

/** `contacts` from design v3; the form is approved in #11. */
@Composable
fun ContactsScreen(contacts: List<CareContact>, onBack: () -> Unit, onOpen: (CareContact?) -> Unit) {
    val uri = LocalUriHandler.current
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.contacts), style = serifStyle(30f, 1.1f))
        contacts.groupBy { it.group }.entries.sortedBy { it.key }.forEach { (group, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(group.label))
                Card {
                    items.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().tap { onOpen(c) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(listOf(c.relationship, localPhone(c.phone)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = Kf.Muted)
                                // design: 12px/1.4 #44463E on #E9E2D4, radius 8, padding 4px 8px, align-self:flex-start
                                if (c.note.isNotBlank()) Text(
                                    c.note, fontSize = 12.sp, lineHeight = (12 * 1.4).sp, color = Kf.Ink2,
                                    modifier = Modifier.background(Kf.CardAlt, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Box(Modifier.size(44.dp).background(Kf.GreenTint, CircleShape).tap { uri.openUri("tel:${c.phone}") }, contentAlignment = Alignment.Center) {
                                SvgPath("M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z", 18.dp, Kf.Green)
                            }
                        }
                        Hairline()
                    }
                }
            }
        }
        DashedButton(stringResource(Res.string.add_contact)) { onOpen(null) }
    }
}

data class ContactForm(val name: String, val relationship: String, val phone: String, val group: ContactGroup, val emergency: Boolean, val note: String)

/** Approved deviation (#11): add, edit or remove a Care Contact on the onb1 layout. Actions return false when offline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactFormScreen(editing: CareContact?, onBack: () -> Unit, save: suspend (ContactForm) -> Boolean, remove: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var relationship by rememberSaveable { mutableStateOf(editing?.relationship.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(editing?.phone?.removePrefix("+62").orEmpty()) }
    var group by rememberSaveable { mutableStateOf(editing?.group ?: ContactGroup.Medical) }
    var emergency by rememberSaveable { mutableStateOf(editing?.emergency ?: false) }
    var note by rememberSaveable { mutableStateOf(editing?.note.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)
    fun run(action: suspend () -> Boolean) {
        if (!busy) scope.launch { busy = true; failed = !action(); busy = false }
    }

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.contact_form_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.contact_name)) { BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.relationship)) { BasicTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.contact_phone)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+62", fontSize = 17.sp)
                BasicTextField(
                    phone, { phone = typedPhone(it) }, Modifier.weight(1f).padding(start = 12.dp), textStyle = input,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), visualTransformation = PhoneGrouping, singleLine = true,
                )
            }
        }
        Field(stringResource(Res.string.med_note)) { BasicTextField(note, { note = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactGroup.entries.forEach { g -> PickChip(stringResource(g.label), g == group) { group = g } }
        }
        Toggle(stringResource(Res.string.emergency_contact), emergency) { emergency = it }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                // ponytail: 6 digits admits short landlines; no stricter check until a wrong number bites.
                if (name.isNotBlank() && phone.length >= 6) run { save(ContactForm(name.trim(), relationship.trim(), e164(phone), group, emergency, note.trim())) }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
            if (editing != null) Link(stringResource(Res.string.remove_contact), Kf.Sos, { run(remove) }, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
