package id.kinfolk.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kinfolk.data.Medication
import id.kinfolk.ui.DashedButton
import id.kinfolk.ui.ErrorText
import id.kinfolk.ui.Field
import id.kinfolk.ui.Kf
import id.kinfolk.ui.Pill
import id.kinfolk.ui.PrimaryButton
import id.kinfolk.ui.SectionLabel
import id.kinfolk.ui.Toggle
import id.kinfolk.ui.appointment.TimeInput
import id.kinfolk.ui.appointment.hm
import id.kinfolk.ui.appointment.parseHm
import id.kinfolk.ui.serifStyle
import id.kinfolk.ui.tap
import kinfolk.shared.generated.resources.Res
import kinfolk.shared.generated.resources.add_med
import kinfolk.shared.generated.resources.back
import kinfolk.shared.generated.resources.med_dose
import kinfolk.shared.generated.resources.med_form_head
import kinfolk.shared.generated.resources.med_name
import kinfolk.shared.generated.resources.med_schedule
import kinfolk.shared.generated.resources.no_connection
import kinfolk.shared.generated.resources.save
import kinfolk.shared.generated.resources.still_taking
import kinfolk.shared.generated.resources.stopped_meds
import kinfolk.shared.generated.resources.tab_records
import kinfolk.shared.generated.resources.time
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/** "75 mg · pagi, sesudah makan" */
fun Medication.doseLine() = listOf(dose, schedule).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * `records`, Obat tab only (docs/screen-map.md). Approved deviation (#11): no tab switcher, dose hint, "Tandai"
 * button or note line; "+ Tambah obat" and a "Tidak diminum lagi" group for stopped Medications.
 */
@Composable
fun RecordsScreen(meds: List<Medication>, onOpen: (Medication?) -> Unit) {
    // design: padding:4px 20px; gap:18px
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(Res.string.tab_records), style = serifStyle(30f, 1.1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            meds.filter { it.active }.forEach { MedCard(it, onOpen) }
            DashedButton(stringResource(Res.string.add_med)) { onOpen(null) }
        }
        val stopped = meds.filterNot { it.active }
        if (stopped.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(Res.string.stopped_meds))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { stopped.forEach { MedCard(it, onOpen) } }
        }
    }
}

@Composable
private fun MedCard(m: Medication, onOpen: (Medication) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Kf.Card, RoundedCornerShape(18.dp)).tap { onOpen(m) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(m.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (m.active) Kf.Ink else Kf.Muted)
        m.doseLine().takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = if (m.active) Kf.Ink2 else Kf.Muted) }
    }
}

data class MedForm(val name: String, val dose: String, val schedule: String, val timeOfDay: LocalTime, val active: Boolean)

/** Approved deviation (#11): add or edit a Medication on the onb1 layout. [save] returns false when offline. */
@Composable
fun MedFormScreen(editing: Medication?, onBack: () -> Unit, save: suspend (MedForm) -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var dose by rememberSaveable { mutableStateOf(editing?.dose.orEmpty()) }
    var schedule by rememberSaveable { mutableStateOf(editing?.schedule.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(editing?.timeOfDay?.let { hm(it).replace(".", "") }.orEmpty()) }
    var active by rememberSaveable { mutableStateOf(editing?.active ?: true) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val input = LocalTextStyle.current.copy(fontSize = 17.sp)

    // design (onb1): padding 12px 24px, gap 26
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Pill(stringResource(Res.string.back), onBack)
        Text(stringResource(Res.string.med_form_head), style = serifStyle(30f, 1.1f))
        Field(stringResource(Res.string.med_name)) { BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.med_dose)) { BasicTextField(dose, { dose = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.med_schedule)) { BasicTextField(schedule, { schedule = it }, Modifier.fillMaxWidth(), textStyle = input, singleLine = true) }
        Field(stringResource(Res.string.time)) { TimeInput(time, { time = it }) }
        if (editing != null) Toggle(stringResource(Res.string.still_taking), active) { active = it }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.save), Modifier.padding(top = 12.dp)) {
                val at = parseHm(time)
                if (name.isNotBlank() && at != null && !busy) scope.launch {
                    busy = true
                    failed = !save(MedForm(name.trim(), dose.trim(), schedule.trim(), at, active))
                    busy = false
                }
            }
            if (failed) ErrorText(stringResource(Res.string.no_connection))
        }
    }
}
