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

fun SupabaseClient.me(): String = auth.currentUserOrNull()!!.id

// ponytail: joins straight in the local database until Invitation (#4) gives Members a real way in; switch to that then.
/** Someone who has joined [circleId] as a sibling. Returns their user id. */
suspend fun joinedMember(circleId: String): String = signedInSibling(circleId).me()

/** Someone who has joined [circleId] as a sibling, signed in. */
suspend fun signedInSibling(circleId: String): SupabaseClient {
    val client = signedInNewcomer()
    val id = client.me()
    val sql = "insert into public.members (circle_id, user_id, role) values ('$circleId', '$id', 'sibling')"
    val psql = ProcessBuilder("docker", "exec", "supabase_db_Kinfolk", "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1", "-c", sql)
        .redirectErrorStream(true).start()
    check(psql.waitFor() == 0) { psql.inputStream.bufferedReader().readText() }
    return client
}
