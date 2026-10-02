package id.kinfolk.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.kinfolk.data.CareContact
import id.kinfolk.data.Document
import id.kinfolk.data.Medication
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.SvgPath
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.cancel
import kinfolk.shared.generated.resources.contact_form_head
import kinfolk.shared.generated.resources.no_results
import kinfolk.shared.generated.resources.rec_docs
import kinfolk.shared.generated.resources.rec_meds
import kinfolk.shared.generated.resources.result_count
import kinfolk.shared.generated.resources.search_ph
import kinfolk.shared.generated.resources.search_try
import kinfolk.shared.generated.resources.tab_timeline
import kinfolk.shared.generated.resources.tasks_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** v3 `search` kinds, their label and colour. Transkrip comes with recording. */
enum class SearchKind(val label: StringResource, val color: Color) {
    Medicine(Res.string.rec_meds, Color(0xFF9A7A2F)),
    Document(Res.string.rec_docs, Color(0xFF6C5A8E)),
    Timeline(Res.string.tab_timeline, Color(0xFF3E6E8E)),
    Contact(Res.string.contact_form_head, Color(0xFFB0643A)),
    Task(Res.string.tasks_title, Kf.Ink),
}

class SearchRow(val kind: SearchKind, val title: String, val sub: String, val open: () -> Unit)

/** The magnifier on Home's bar and here. */
const val Magnifier = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4"

/**
 * "Coba" chips, approved in #32 in place of v3's clopidogrel, MRI, Budi, Dr. Rao: the first active Medication's name
 * (lowercase), the first word of the newest Document, the first other Member, the first Care Contact.
 */
fun suggestions(meds: List<Medication>, newest: List<Document>, others: List<String>, contacts: List<CareContact>) = listOfNotNull(
    meds.firstOrNull { it.active }?.name?.lowercase(),
    newest.firstOrNull()?.name?.substringBefore(' '),
    others.firstOrNull(),
    contacts.firstOrNull()?.name,
).filter { it.isNotBlank() }.distinct()

/** Obat sub: v3's "Pengencer darah · pagi", from the schedule. */
fun medSub(m: Medication, bloodThinner: String) =
    listOfNotNull(bloodThinner.takeIf { m.bloodThinner }, m.schedule).filter { it.isNotBlank() }.joinToString(" · ")

/** `search`: [rows] are the hits of [query] (null until the first answer); under 2 characters, the suggestions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(query: String, onQuery: (String) -> Unit, suggestions: List<String>, rows: List<SearchRow>?, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // design: padding:4px 20px; gap:16px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // design: h46, 1.5px #2F5D4A, r14, #fff, padding 0 12, gap 8; 18px magnifier muted; input 16px
            Row(
                Modifier.weight(1f).height(46.dp).background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.5.dp, Kf.Green, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                SvgPath(Magnifier, 18.dp, Kf.Muted)
                val style = LocalTextStyle.current.copy(fontSize = 16.sp, color = Kf.Ink)
                val hint = stringResource(Res.string.search_ph)
                BasicTextField(
                    query, onQuery, Modifier.weight(1f).focusRequester(focus), textStyle = style, singleLine = true,
                    cursorBrush = SolidColor(Kf.Ink),
                    decorationBox = { field -> if (query.isEmpty()) Text(hint, style = style.copy(color = Kf.Muted), maxLines = 1); field() },
                )
            }
            // design: #2F5D4A 15px 500, padding 0 4
            Text(stringResource(Res.string.cancel), color = Kf.Green, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.tap(onCancel).padding(horizontal = 4.dp))
        }
        if (query.trim().length < 2) {
            if (suggestions.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.search_try), fontSize = 13.sp, color = Kf.Muted)
                // design: 1px rgba(34,38,31,.16), #FBF8F2, r999, padding 8 14, 14px
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        Text(
                            s, fontSize = 14.sp,
                            modifier = Modifier.background(Kf.Card, CircleShape).border(1.dp, ChipLine, CircleShape).tap { onQuery(s) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        } else if (rows != null) {
            if (rows.isEmpty()) Text(stringResource(Res.string.no_results), fontSize = 14.sp, color = Kf.Muted)
            else {
                Text(stringResource(Res.string.result_count, rows.size), fontSize = 13.sp, color = Kf.Muted)
                // design: #FBF8F2, r18; rows padding 12 16, gap 3, border-bottom rgba(34,38,31,.07)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kf.Card)) {
                    rows.forEach { r ->
                        Column(Modifier.fillMaxWidth().tap(r.open).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(r.kind.label).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em, color = r.kind.color)
                            Text(r.title, fontSize = 15.sp, lineHeight = (15 * 1.4).sp)
                            Text(r.sub, fontSize = 12.sp, color = Kf.Muted)
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

private val ChipLine = Color(0x2922261F) // rgba(34,38,31,.16)
