// Auth "Send SMS" hook: delivers the sign-in OTP over WhatsApp (Meta template), or SMS via Twilio when
// the Member asked for SMS ("Kirim lewat SMS") or WhatsApp refuses the message.
import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";
import { env, rpc, viaSms, viaWhatsApp } from "../_shared/send.ts";

const error = (status: number, message: string) =>
  new Response(JSON.stringify({ error: { http_code: status, message } }), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req) => {
  const payload = await req.text();
  let input: { sms: { otp: string; phone: string } };
  try {
    input = new Webhook(env("SEND_SMS_HOOK_SECRET").replace("v1,whsec_", "")).verify(payload, Object.fromEntries(req.headers)) as typeof input;
  } catch {
    return error(401, "Invalid hook signature");
  }
  const { otp, phone } = input.sms;
  const smsRequested = await rpc("sign_in_via_sms", { phone }).then((r) => r.ok && r.body === true, () => false);
  // ponytail: fallback only on a synchronous WhatsApp refusal; a message Meta accepts but can't deliver
  // is covered by the Member tapping "Kirim lewat SMS". Add the status webhook if that proves too slow.
  const whatsApp = () =>
    viaWhatsApp(phone, "kinfolk_otp", [ // docs/whatsapp-templates.md
      { type: "body", parameters: [{ type: "text", text: otp }] },
      { type: "button", sub_type: "url", index: "0", parameters: [{ type: "text", text: otp }] },
    ]);
  const sent = (!smsRequested && await whatsApp()) ||
    await viaSms(phone, `Kode masuk Kinfolk Anda: ${otp}. Jangan bagikan kode ini ke siapa pun.`);
  return sent ? new Response("{}", { headers: { "content-type": "application/json" } }) : error(502, "Could not send the sign-in code");
});
