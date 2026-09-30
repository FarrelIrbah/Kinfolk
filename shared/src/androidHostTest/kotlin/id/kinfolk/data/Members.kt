package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlin.random.Random

// Test seam: the Supabase API of the local stack (`npx supabase start`), used as a specific Member.

private const val URL = "http://127.0.0.1:54321"

private val jvmAuth: AuthConfig.() -> Unit = {
    sessionManager = MemorySessionManager()
    codeVerifierCache = MemoryCodeVerifierCache()
    alwaysAutoRefresh = false
    autoLoadFromStorage = false
    enableLifecycleCallbacks = false
}

// ponytail: test users sign up by email (confirmations are off locally), so tests need no secret key;
// the app itself signs in by phone.
/** Someone who has just signed in and belongs to no Care Circle yet. */
suspend fun signedInNewcomer(): SupabaseClient = kinfolkClient(URL, jvmAuth).apply {
    auth.signUpWith(Email) {
        email = "member-${Random.nextLong(1, Long.MAX_VALUE)}@kinfolk.test"
        password = "local-test-password"
    }
}

/** Nobody signed in. */
fun signedOut(): SupabaseClient = kinfolkClient(URL, jvmAuth)
