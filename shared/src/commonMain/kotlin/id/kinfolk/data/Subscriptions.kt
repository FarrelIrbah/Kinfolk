package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

// Subscription API (see CONTEXT.md, ADR 0006): written by RevenueCat's webhook, read by every Member.

/** "Paket": [transcription] is the add-on; [trial] runs until [expiresAt]. */
@Serializable
data class Subscription(
    val transcription: Boolean,
    val trial: Boolean,
    @SerialName("expires_at") val expiresAt: Instant,
) {
    val active get() = expiresAt > Clock.System.now()
}

/** The circle's Subscription while paid up; null when free. */
suspend fun SupabaseClient.subscription(circleId: String): Subscription? =
    from("subscriptions").select { filter { eq("circle_id", circleId) } }.decodeSingleOrNull<Subscription>()?.takeIf { it.active }
