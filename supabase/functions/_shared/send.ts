// Outbound WhatsApp (Meta Cloud API template) and SMS (Twilio). Credentials are Edge Function secrets only
// (config.toml for the local stack, `supabase secrets set` in production).

export const env = (key: string) => Deno.env.get(key) ?? "";

/** Sends a Meta-approved template (docs/whatsapp-templates.md) to [phone], digits with country code. */
export const viaWhatsApp = (phone: string, name: string, components: unknown[]) =>
  whatsApp({ to: phone, type: "template", template: { name, language: { code: "id" }, components } });

/** Free-form text: only within 24 hours of the person's last message. */
export const viaWhatsAppText = (phone: string, body: string) => whatsApp({ to: phone, type: "text", text: { body } });

/** Accepted by Meta, with its message id; null when refused. */
async function whatsApp(message: object): Promise<{ id?: string } | null> {
  const url = env("WHATSAPP_API_URL") || "https://graph.facebook.com/v23.0";
  try {
    const res = await fetch(`${url}/${env("WHATSAPP_PHONE_NUMBER_ID")}/messages`, {
      method: "POST",
      headers: { authorization: `Bearer ${env("WHATSAPP_TOKEN")}`, "content-type": "application/json" },
      body: JSON.stringify({ messaging_product: "whatsapp", ...message }),
    });
    if (!res.ok) console.error("WhatsApp refused", res.status, await res.text());
    return res.ok ? { id: (await res.json().catch(() => ({}))).messages?.[0]?.id } : null;
  } catch (e) {
    console.error("WhatsApp unreachable", e);
    return null;
  }
}

export async function viaSms(phone: string, body: string): Promise<boolean> {
  const url = env("TWILIO_API_URL") || "https://api.twilio.com";
  const sid = env("TWILIO_ACCOUNT_SID");
  try {
    const res = await fetch(`${url}/2010-04-01/Accounts/${sid}/Messages.json`, {
      method: "POST",
      headers: { authorization: `Basic ${btoa(`${sid}:${env("TWILIO_AUTH_TOKEN")}`)}` },
      body: new URLSearchParams({ To: `+${phone}`, From: env("TWILIO_FROM"), Body: body }),
    });
    if (!res.ok) console.error("SMS refused", res.status, await res.text());
    return res.ok;
  } catch (e) {
    console.error("SMS unreachable", e);
    return false;
  }
}

/** Calls a Postgres function through PostgREST: as the caller when given their [authorization] header, else as the service role. */
export async function rpc(name: string, args: unknown, authorization?: string) {
  const service = env("SUPABASE_SERVICE_ROLE_KEY");
  const res = await fetch(`${env("SUPABASE_URL")}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { apikey: authorization ? env("SUPABASE_ANON_KEY") : service, authorization: authorization ?? `Bearer ${service}`, "content-type": "application/json" },
    body: JSON.stringify(args),
  });
  const text = await res.text(); // empty for a void function
  return { ok: res.ok, status: res.status, body: res.ok && text ? JSON.parse(text) : text };
}
