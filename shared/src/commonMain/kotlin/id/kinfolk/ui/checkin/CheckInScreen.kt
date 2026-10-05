package id.kinfolk.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Ate
import id.kinfolk.data.CheckInDraft
import id.kinfolk.data.Mood
import id.kinfolk.ui.HintedInput
import id.kinfolk.ui.Kf
import id.kinfolk.ui.PickChip
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.serifStyle
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.bp_label
import kinfolk.shared.generated.resources.ci_ate
import kinfolk.shared.generated.resources.ci_eyebrow
import kinfolk.shared.generated.resources.ci_good
import kinfolk.shared.generated.resources.ci_low
import kinfolk.shared.generated.resources.ci_mood
import kinfolk.shared.generated.resources.ci_no
import kinfolk.shared.generated.resources.ci_note
import kinfolk.shared.generated.resources.ci_note_ph
import kinfolk.shared.generated.resources.ci_okay
import kinfolk.shared.generated.resources.ci_save
import kinfolk.shared.generated.resources.ci_share
import kinfolk.shared.generated.resources.ci_some
import kinfolk.shared.generated.resources.ci_title
import kinfolk.shared.generated.resources.ci_walked
import kinfolk.shared.generated.resources.ci_yes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** v3's toast list: "Budi", "Budi dan Dewi", "Budi, Dewi, dan Agus". */
fun names(all: List<String>): String = when (all.size) {
    0, 1 -> all.joinToString()
    2 -> "${all[0]} dan ${all[1]}"
    else -> all.dropLast(1).joinToString(", ") + ", dan " + all.last()
}

private val InputLine = Color(0x2422261F) // rgba(34,38,31,.14)

/**
 * v3 `checkin`. [saved] is tonight's, for "Ubah"; a new one starts with the blood pressure empty and Ya/Ya/Baik picked
 * (approved in #24). [save] gets null for a missing reading (v3: below 70/40, "Isi tekanan darah.").
 */
@Composable
fun CheckInScreen(at: String, recipient: String, saved: CheckInDraft?, onBack: () -> Unit, save: suspend (CheckInDraft?) -> Unit) {
    val scope = rememberCoroutineScope()
    var sys by rememberSaveable { mutableStateOf(saved?.sys?.toString().orEmpty()) }
    var dia by rememberSaveable { mutableStateOf(saved?.dia?.toString().orEmpty()) }
    var ate by rememberSaveable { mutableStateOf(saved?.ate ?: Ate.yes) }
    var walked by rememberSaveable { mutableStateOf(saved?.walked ?: true) }
    var mood by rememberSaveable { mutableStateOf(saved?.mood ?: Mood.good) }
    var note by rememberSaveable { mutableStateOf(saved?.note.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    fun digits(s: String) = s.filter { it.isDigit() }.take(3)

    // design: padding:4px 20px; gap:20px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.ci_eyebrow, at, recipient), fontSize = 13.sp, color = Kf.Muted)
            Text(stringResource(Res.string.ci_title), style = serifStyle(30f, 1.1f))
        }
        Column(
            Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.bp_label), fontSize = 13.sp, color = Kf.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BpInput(sys) { sys = digits(it) }
                Text("/", style = serifStyle(24f).copy(color = Kf.Muted))
                BpInput(dia) { dia = digits(it) }
                Text("mmHg", fontSize = 14.sp, color = Kf.Muted)
            }
        }
        Group(Res.string.ci_ate) {
            listOf(Ate.yes to Res.string.ci_yes, Ate.some to Res.string.ci_some, Ate.no to Res.string.ci_no).forEach { (v, label) ->
                PickChip(stringResource(label), ate == v) { ate = v }
            }
        }
        Group(Res.string.ci_walked) {
            listOf(true to Res.string.ci_yes, false to Res.string.ci_no).forEach { (v, label) ->
                PickChip(stringResource(label), walked == v) { walked = v }
            }
        }
        Group(Res.string.ci_mood) {
            listOf(Mood.good to Res.string.ci_good, Mood.okay to Res.string.ci_okay, Mood.low to Res.string.ci_low).forEach { (v, label) ->
                PickChip(stringResource(label), mood == v) { mood = v }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.ci_note), fontSize = 13.sp, color = Kf.Muted)
            // design: height 50 plus the 1px rgba(34,38,31,.14) border (`<input>` is content-box), radius 12, #FBF8F2, padding 0 12, 15px
            Box(
                Modifier.fillMaxWidth().height(52.dp).background(Kf.Card, RoundedCornerShape(12.dp))
                    .border(1.dp, InputLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                HintedInput(note, { note = it }, stringResource(Res.string.ci_note_ph), LocalTextStyle.current.copy(fontSize = 15.sp, color = Kf.Ink), singleLine = true)
            }
        }
        Text(stringResource(Res.string.ci_share), fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Kf.Muted)
        PrimaryButton(stringResource(Res.string.ci_save)) {
            if (!busy) scope.launch {
                busy = true
                save(CheckInDraft(sys.toIntOrNull() ?: 0, dia.toIntOrNull() ?: 0, ate, walked, mood, note.trim()).takeIf { it.hasBloodPressure })
                busy = false
            }
        }
    }
}

/** design: 84x46 content-box, so 110x48 with padding 0 12 and the 1px rgba(34,38,31,.14) border; radius 12, white, 22px centred. */
@Composable
private fun BpInput(value: String, onValue: (String) -> Unit) {
    Box(
        Modifier.width(110.dp).height(48.dp).background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, InputLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value, onValue, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, color = Kf.Ink, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun Group(label: StringResource, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), fontSize = 13.sp, color = Kf.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
