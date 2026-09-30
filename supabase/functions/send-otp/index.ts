// Auth "Send SMS" hook: delivers the sign-in OTP over WhatsApp (Meta template), or SMS via Twilio when
// the Member asked for SMS ("Kirim lewat SMS") or WhatsApp refuses the message. Credentials are
// Edge Function secrets only (config.toml for the local stack, `supabase secrets set` in production).
import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

const env = (key: string) => Deno.env.get(key) ?? "";

async function viaWhatsApp(phone: string, otp: string): Promise<boolean> {
  const url = env("WHATSAPP_API_URL") || "https://graph.facebook.com/v23.0";
  const res = await fetch(`${url}/${env("WHATSAPP_PHONE_NUMBER_ID")}/messages`, {
    method: "POST",
    headers: { authorization: `Bearer ${env("WHATSAPP_TOKEN")}`, "content-type": "application/json" },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      to: phone,
      type: "template",
      template: {
        name: "kinfolk_otp", // docs/whatsapp-templates.md
        language: { code: "id" },
        components: [
          { type: "body", parameters: [{ type: "text", text: otp }] },
          { type: "button", sub_type: "url", index: "0", parameters: [{ type: "text", text: otp }] },
        ],
      },
    }),
  });
  if (!res.ok) console.error("WhatsApp refused", res.status, await res.text());
  return res.ok;
}

async function viaSms(phone: string, otp: string): Promise<boolean> {
  const url = env("TWILIO_API_URL") || "https://api.twilio.com";
  const sid = env("TWILIO_ACCOUNT_SID");
  const res = await fetch(`${url}/2010-04-01/Accounts/${sid}/Messages.json`, {
    method: "POST",
    headers: { authorization: `Basic ${btoa(`${sid}:${env("TWILIO_AUTH_TOKEN")}`)}` },
    body: new URLSearchParams({
      To: `+${phone}`,
      From: env("TWILIO_FROM"),
      Body: `Kode masuk Kinfolk Anda: ${otp}. Jangan bagikan kode ini ke siapa pun.`,
    }),
  });
  if (!res.ok) console.error("SMS refused", res.status, await res.text());
  return res.ok;
}

async function smsRequested(phone: string): Promise<boolean> {
  const res = await fetch(`${env("SUPABASE_URL")}/rest/v1/rpc/sign_in_via_sms`, {
    method: "POST",
    headers: { apikey: env("SUPABASE_SERVICE_ROLE_KEY"), authorization: `Bearer ${env("SUPABASE_SERVICE_ROLE_KEY")}`, "content-type": "application/json" },
    body: JSON.stringify({ phone }),
  });
  return res.ok && (await res.json()) === true;
}

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
  // ponytail: fallback only on a synchronous WhatsApp refusal; a message Meta accepts but can't deliver
  // is covered by the Member tapping "Kirim lewat SMS". Add the status webhook if that proves too slow.
  const whatsApp = () => viaWhatsApp(phone, otp).catch((e) => (console.error("WhatsApp unreachable", e), false));
  const sent = (!(await smsRequested(phone).catch(() => false)) && await whatsApp()) || await viaSms(phone, otp);
  return sent ? new Response("{}", { headers: { "content-type": "application/json" } }) : error(502, "Could not send the sign-in code");
});
