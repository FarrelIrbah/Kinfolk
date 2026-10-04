// WhatsApp both ways (ADR 0002). Meta calls this webhook with replies: a YA or TIDAK, typed or tapped on a quick-reply
// button, answers the ask it was sent for (a swap or a drive); a typed 1 marks the dose reminded given; "T: …" adds a Question to the next Appointment. The minute job (cron, docs/whatsapp-templates.md) calls
// it with NOTIFY_SECRET to queue due reminders. Either way it then sends everything waiting in the database, over
// WhatsApp, or SMS when WhatsApp refuses it.
import { env, rpc, viaSms, viaWhatsApp, viaWhatsAppText } from "../_shared/send.ts";

const reply = (status: number, body = "") => new Response(body, { status });

/** Meta's X-Hub-Signature-256: the body's HMAC with the app secret, compared in constant time. */
async function signedByMeta(body: string, header: string | null) {
  const hex = header?.match(/^sha256=([0-9a-f]{64})$/)?.[1];
  if (!hex) return false;
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env("WHATSAPP_APP_SECRET")), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  return crypto.subtle.verify("HMAC", key, new Uint8Array(hex.match(/../g)!.map((b) => parseInt(b, 16))), new TextEncoder().encode(body));
}

// ponytail: Meta may deliver a reply twice; the second one is answered "Permintaan ini sudah tidak berlaku.", or for a
// "T:" adds the Question twice (dedupe on Meta's message id if that shows up).
async function answer(message: { from: string; type: string; button?: { payload?: string }; text?: { body?: string } }) {
  const [word, id] = message.type === "button" ? (message.button?.payload ?? "").split(":") : [message.text?.body ?? "", null];
  const yes = { ya: true, tidak: false }[word.trim().toLowerCase()];
  const given = message.type === "text" && word.trim() === "1"; // a dose reminder's "Balas 1"
  const question = message.type === "text" ? word.match(/^\s*t\s*:\s*(\S[\s\S]*?)\s*$/i)?.[1] : undefined; // "T: …"
  if (yes === undefined && !given && !question) return;
  const { ok, body } = question ? await rpc("whatsapp_question", { phone: message.from, text: question })
    : given ? await rpc("whatsapp_given", { phone: message.from })
    : await rpc("whatsapp_reply", { phone: message.from, message: id, yes });
  if (ok && body) await viaWhatsAppText(message.from, body);
}

async function deliver() {
  const { body } = await rpc("claim_messages", {});
  for (const m of body ?? []) {
    if (!m.phone) continue;
    const buttons = m.answerable
      ? ["ya", "tidak"].map((word, index) => ({ type: "button", sub_type: "quick_reply", index, parameters: [{ type: "payload", payload: `${word}:${m.id}` }] }))
      : [];
    const params = m.params.map((text: string) => ({ type: "text", text }));
    const sent = await viaWhatsApp(m.phone, m.template, [{ type: "body", parameters: params }, ...buttons]);
    if (!sent) await viaSms(m.phone, m.body);
    else if (sent.id) await rpc("sent_on_whatsapp", { message: m.id, wa_id: sent.id });
  }
}

Deno.serve(async (req) => {
  const url = new URL(req.url);
  if (req.method === "GET") { // Meta's check when the webhook is set up
    const ok = url.searchParams.get("hub.mode") === "subscribe" && url.searchParams.get("hub.verify_token") === env("WHATSAPP_VERIFY_TOKEN");
    return ok ? reply(200, url.searchParams.get("hub.challenge") ?? "") : reply(403);
  }
  const raw = await req.text();
  if (req.headers.has("x-hub-signature-256")) {
    if (!await signedByMeta(raw, req.headers.get("x-hub-signature-256"))) return reply(401);
    const values = (JSON.parse(raw).entry ?? []).flatMap((e: any) => e.changes ?? []).map((c: any) => c.value ?? {});
    for (const m of values.flatMap((v: any) => v.messages ?? [])) await answer(m);
    for (const s of values.flatMap((v: any) => v.statuses ?? []).filter((s: any) => s.status === "failed")) {
      for (const m of (await rpc("whatsapp_failed", { wa_id: s.id })).body ?? []) if (m.phone) await viaSms(m.phone, m.body);
    }
  } else if (req.headers.get("authorization") === `Bearer ${env("NOTIFY_SECRET")}`) {
    await rpc("queue_reminders", { at: JSON.parse(raw || "{}").at ?? new Date().toISOString() });
  } else return reply(401);
  await deliver();
  return reply(200, "ok");
});
