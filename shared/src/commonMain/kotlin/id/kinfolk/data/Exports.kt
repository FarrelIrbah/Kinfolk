package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

// Export API (see CONTEXT.md): v3's `export`, one PDF for a new doctor, built by the export Edge Function.

/** v3's six, in its order. [docs] holds the Documents picked for it. */
@Suppress("EnumEntryName") enum class ExportSection { history, meds, trends, visits, allergies, docs }

/**
 * What each section would hold, as the exporting Member sees it: Emergency Info's conditions and allergies, its
 * emergency contacts, all Medications (stopped ones are the "perubahan terbaru"), recent Check-ins, Visit Notes, and
 * the page count of each picked Document.
 */
data class ExportContent(
    val conditions: String,
    val allergies: String,
    val emergencyContacts: Int,
    val medications: Int,
    val checkIns: Int,
    val visitNotes: Int,
    val documentPages: List<Int>,
)

/** Medications on one page of the PDF. */
const val MEDS_PER_PAGE = 12

/**
 * Pages per section (owner-approved in #33): each section starts its own page; Medications 1 per 12, a page per
 * Visit Note (the latest 3), Documents as many as they have, the rest 1. Nothing to show: 0. The export Edge
 * Function lays the PDF out the same way, so this is its page count.
 */
fun ExportContent.pages(): Map<ExportSection, Int> = mapOf(
    ExportSection.history to if (conditions.isBlank()) 0 else 1,
    ExportSection.meds to (medications + MEDS_PER_PAGE - 1) / MEDS_PER_PAGE,
    ExportSection.trends to if (checkIns == 0) 0 else 1,
    ExportSection.visits to minOf(visitNotes, 3),
    ExportSection.allergies to if (allergies.isBlank() && emergencyContacts == 0) 0 else 1,
    ExportSection.docs to documentPages.sum(),
)

/** "Buat PDF · N halaman" */
fun ExportContent.total(ticked: Set<ExportSection>) = pages().filterKeys { it in ticked }.values.sum()

@Serializable
private data class Exported(val token: String)

/**
 * "Buat PDF": the PDF of [sections] (with [documentIds] for Dokumen, in order) for [recipientId], prepared for
 * [preparedFor], logged on the Timeline. [line] heads each page under the name, as on the preview. Returns its link,
 * which works for 7 days without login.
 */
suspend fun SupabaseClient.export(recipientId: String, preparedFor: String, line: String, sections: Set<ExportSection>, documentIds: List<String>): String {
    val res = functions.invoke("export", buildJsonObject {
        put("recipient_id", recipientId)
        put("prepared_for", preparedFor.trim())
        put("line", line)
        putJsonArray("sections") { sections.forEach { add(it.name) } }
        putJsonArray("documents") { documentIds.forEach { add(it) } }
    })
    return "$supabaseHttpUrl/functions/v1/export?t=${Json.decodeFromString<Exported>(res.bodyAsText()).token}"
}
