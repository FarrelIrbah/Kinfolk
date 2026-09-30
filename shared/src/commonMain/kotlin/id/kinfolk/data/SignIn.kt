package id.kinfolk.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Sends a sign-in code to [phone] (E.164) over WhatsApp, or SMS when [sms]; the server falls back to SMS itself. */
suspend fun SupabaseClient.sendSignInCode(phone: String, sms: Boolean) {
    // The Auth hook can't see the requested channel, so SMS is asked for first (see send-otp).
    if (sms) postgrest.rpc("request_sms_sign_in", buildJsonObject { put("phone", phone) })
    auth.signInWith(OTP) { this.phone = phone }
}

suspend fun SupabaseClient.verifySignInCode(phone: String, code: String) =
    auth.verifyPhoneOtp(OtpType.Phone.SMS, phone, code)
