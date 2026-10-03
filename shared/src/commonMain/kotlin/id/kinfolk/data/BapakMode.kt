package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Mode Bapak API (#37): presses written as the Care Recipient from the signed-in Member's phone.

private fun args(recipientId: String) = buildJsonObject { put("recipient", recipientId) }

/** "Saya baik": texts every Member but the Care Recipient on WhatsApp. Returns how many ("Terkirim ke 5 anak"). */
suspend fun SupabaseClient.sayFine(recipientId: String): Int = postgrest.rpc("say_fine", args(recipientId)).decodeAs()

/** "Butuh bantuan": alerts every Member but the Care Recipient. Returns who is being called, "Sri dan Budi". */
suspend fun SupabaseClient.askHelp(recipientId: String): String = postgrest.rpc("ask_help", args(recipientId)).decodeAs()

/** The organizer's number, "+62…", for "Telepon Sri"; null without one. */
suspend fun SupabaseClient.organizerPhone(recipientId: String): String? =
    postgrest.rpc("organizer_phone", args(recipientId)).decodeAs<String?>()
