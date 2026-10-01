package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Expense API (see CONTEXT.md): v3's Biaya, under the Tagihan & uang Data Category.

@Serializable
data class Expense(
    val id: String,
    val what: String,
    val amount: Long, // whole Rupiah
    @SerialName("paid_by") val paidBy: String,
    val day: LocalDate,
)

/** Every Expense in [circleId] the signed-in Member may see, newest first. */
suspend fun SupabaseClient.expenses(circleId: String): List<Expense> =
    from("expenses").select {
        filter { eq("circle_id", circleId) }
        order("day", Order.DESCENDING)
        order("at", Order.DESCENDING)
    }.decodeList()

/** "Simpan" under "Tambah pengeluaran"; [day] is the writer's today. */
suspend fun SupabaseClient.addExpense(circleId: String, recipientId: String, what: String, amount: Long, paidBy: String, day: LocalDate) {
    from("expenses").insert(buildJsonObject {
        put("circle_id", circleId); put("recipient_id", recipientId); put("what", what.trim())
        put("amount", amount); put("paid_by", paidBy); put("day", day.toString())
    })
}
