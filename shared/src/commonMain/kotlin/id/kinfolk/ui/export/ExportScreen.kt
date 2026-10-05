package id.kinfolk.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Document
import id.kinfolk.data.ExportContent
import id.kinfolk.data.ExportSection
import id.kinfolk.data.pages
import id.kinfolk.ui.Card
import id.kinfolk.ui.Hairline
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.appointment.dayMonth
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.export_cta
import kinfolk.shared.generated.resources.export_for
import kinfolk.shared.generated.resources.export_for_line
import kinfolk.shared.generated.resources.export_note
import kinfolk.shared.generated.resources.export_pages
import kinfolk.shared.generated.resources.export_sub
import kinfolk.shared.generated.resources.export_title
import kinfolk.shared.generated.resources.rec_docs
import kinfolk.shared.generated.resources.sec_allergies
import kinfolk.shared.generated.resources.sec_history
import kinfolk.shared.generated.resources.sec_meds
import kinfolk.shared.generated.resources.sec_trends
import kinfolk.shared.generated.resources.sec_visits
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** [text] inside a sentence: its first letter lowered ("laporan MRI"), unless an acronym starts it ("MRI otak"), like the Timeline. */
fun inSentence(text: String) =
    if (text.length > 1 && text[1].isUpperCase()) text else text.replaceFirstChar { it.lowercase() }

/** The Dokumen row (owner-approved in #33): the picked names as v3 writes them, "Ringkasan pulang RS & laporan MRI"; null for none. */
fun docsLabel(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names[0]
    else -> (listOf(names[0]) + names.drop(1).map(::inSentence)).let { it.dropLast(1).joinToString(", ") + " & " + it.last() }
}

/** "29 Sept 2026" */
fun exportDate(d: LocalDate) = "${dayMonth(d)} ${d.year}"

private val ExportDefaults = ExportSection.entries - ExportSection.docs // v3's exportSel
private val Labels = mapOf(
    ExportSection.history to Res.string.sec_history, ExportSection.meds to Res.string.sec_meds, ExportSection.trends to Res.string.sec_trends,
    ExportSection.visits to Res.string.sec_visits, ExportSection.allergies to Res.string.sec_allergies,
)
private val InputLine = Color(0x2422261F) // rgba(34,38,31,.14)
private val BoxLine = Color(0x4D22261F) // rgba(34,38,31,.3)
private val Bar = Color(0xFFEDE7DB)

/**
 * v3 `export`: "Disiapkan untuk", the six sections with their pages ([content] as the Member sees it), the paper
 * preview, "Buat PDF · N halaman" and the note. Owner-approved in #33: a section with nothing in it shows "0 hlm" and
 * can't be ticked; the Dokumen row opens a list of [docs] (newest versions, not legal) to pick from; the preview
 * header is the Care Recipient's name only and "Disiapkan untuk" starts empty. [picked]: the Document ids from
 * [DocsPicker], which [onPickDocs] opens. [create] gets what to put in the PDF.
 */
@Composable
fun ExportScreen(
    recipientName: String,
    content: ExportContent,
    docs: List<Document>,
    today: LocalDate,
    picked: List<String>,
    onPickDocs: () -> Unit,
    onBack: () -> Unit,
    create: suspend (preparedFor: String, line: String, sections: Set<ExportSection>, documentIds: List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var preparedFor by rememberSaveable { mutableStateOf("") }
    var ticked by rememberSaveable { mutableStateOf(ExportDefaults.map { it.name }) }
    var busy by remember { mutableStateOf(false) }
    val chosen = picked.mapNotNull { id -> docs.firstOrNull { it.id == id } }
    val pages = content.copy(documentPages = chosen.map { it.pages }).pages()
    // Ticked and not empty; Dokumen is ticked by picking.
    val on = ExportSection.entries.filter { pages.getValue(it) > 0 && (it == ExportSection.docs || it.name in ticked) }
    val total = on.sumOf { pages.getValue(it) }
    val label = { s: ExportSection -> if (s == ExportSection.docs) null else Labels.getValue(s) }
    val docsName = docsLabel(chosen.map { it.name }) ?: stringResource(Res.string.rec_docs)
    val line = if (preparedFor.isBlank()) exportDate(today) else stringResource(Res.string.export_for_line, preparedFor.trim(), exportDate(today))

    // design: padding 4px 20px, gap 16
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        // design: gap 8; 30px/1.1 Newsreader; 14px/1.5 #44463E
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.export_title), style = serifStyle(30f, 1.1f))
            Text(stringResource(Res.string.export_sub), fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Kf.Ink2)
        }
        // design: gap 6; 13px #6B6A60; input h48 plus its 1px rgba(34,38,31,.14) border, r12, #FBF8F2, padding 0 12px, 15px
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.export_for), fontSize = 13.sp, color = Kf.Muted)
            Box(
                Modifier.fillMaxWidth().height(50.dp).background(Kf.Card, RoundedCornerShape(12.dp))
                    .border(1.dp, InputLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(preparedFor, { preparedFor = it }, Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = Kf.Ink), singleLine = true)
            }
        }
        // design: #FBF8F2, r18, overflow hidden
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kf.Card)) {
            ExportSection.entries.forEach { s ->
                TickRow(label(s)?.let { stringResource(it) } ?: docsName, pages.getValue(s), s in on) {
                    when {
                        s == ExportSection.docs -> if (docs.isNotEmpty()) onPickDocs()
                        pages.getValue(s) > 0 -> ticked = if (s.name in ticked) ticked - s.name else ticked + s.name
                    }
                }
            }
        }
        // design: #fff, r6, shadow 0 10px 28px rgba(34,38,31,.14), p18, gap 10, margin 4px 44px, aspect .77, overflow hidden
        Column(
            Modifier.padding(horizontal = 44.dp, vertical = 4.dp).fillMaxWidth().aspectRatio(.77f)
                .dropShadow(RoundedCornerShape(6.dp), Shadow(28.dp, Color(0x2422261F), offset = DpOffset(0.dp, 10.dp)))
                .clip(RoundedCornerShape(6.dp)).background(Color.White).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(recipientName, style = serifStyle(16f))
            Text(line, fontSize = 10.sp, color = Kf.Muted)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kf.Sand))
            on.forEach { s ->
                // design: gap 4; 10px 600; bars h4 r2 #EDE7DB at 92% and 68%
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label(s)?.let { stringResource(it) } ?: docsName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth(.92f).height(4.dp).background(Bar, RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(.68f).height(4.dp).background(Bar, RoundedCornerShape(2.dp)))
                }
            }
        }
        PrimaryButton(stringResource(Res.string.export_cta, total)) {
            if (!busy) scope.launch {
                busy = true
                create(preparedFor, line, on.toSet(), chosen.map { it.id })
                busy = false
            }
        }
        // design: 12px/1.5 #6B6A60
        Text(stringResource(Res.string.export_note), fontSize = 12.sp, lineHeight = (12 * 1.5).sp, color = Kf.Muted)
    }
}

/** The Dokumen sheet (owner-approved in #33): [docs] to tick, [picked] their ids in tap order. */
@Composable
fun DocsPicker(docs: List<Document>, picked: List<String>, onPicked: (List<String>) -> Unit) {
    Text(stringResource(Res.string.rec_docs), style = serifStyle(22f, 1.2f))
    Card {
        docs.forEach { d -> TickRow(d.name, d.pages, d.id in picked) { onPicked(if (d.id in picked) picked - d.id else picked + d.id) } }
    }
}

/** design: padding 13px 16px, gap 12, border-bottom rgba(34,38,31,.07); box 24, r7, 1.5px, ✓ 13px; label 15px; pages 12px #6B6A60 */
@Composable
private fun TickRow(label: String, pages: Int, on: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tap(onTap).padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // design: 24px box plus its 1.5px border (content-box)
            Modifier.size(27.dp).background(if (on) Kf.Green else Color.Transparent, RoundedCornerShape(7.dp))
                .border(1.5.dp, if (on) Kf.Green else BoxLine, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) Text("✓", color = Color.White, fontSize = 13.sp) }
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Text(stringResource(Res.string.export_pages, pages), fontSize = 12.sp, color = Kf.Muted)
    }
    Hairline()
}
