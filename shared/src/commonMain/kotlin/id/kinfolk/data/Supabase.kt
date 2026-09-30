package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

// ponytail: local stack only (supabase/config.toml); the hosted project URL and key come with the first release build.
/** Default publishable key of every local Supabase stack; not a secret. */
const val LOCAL_PUBLISHABLE_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"

/** Where the local stack is reachable from this platform (Android emulator uses 10.0.2.2 for the host). */
expect val localSupabaseUrl: String

fun kinfolkClient(url: String = localSupabaseUrl, auth: AuthConfig.() -> Unit = {}): SupabaseClient =
    createSupabaseClient(url, LOCAL_PUBLISHABLE_KEY) {
        // Updates send the whole draft, so a field set back to its default (active = true, bring = "") must be sent too.
        defaultSerializer = KotlinXSerializer(Json { encodeDefaults = true; ignoreUnknownKeys = true })
        install(Auth, auth)
        install(Postgrest)
        install(Functions)
    }
