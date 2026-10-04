package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The last weekly digest WhatsApp sent me in this Care Circle (#40), as its text; null before the first. */
suspend fun SupabaseClient.lastDigest(circleId: String): String? =
    postgrest.rpc("my_digest", buildJsonObject { put("circle", circleId) }).decodeAs<String?>()
