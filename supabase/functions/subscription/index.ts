// RevenueCat's webhook (#50, ADR 0006). RevenueCat sends the Authorization header set in its dashboard:
// "Bearer <REVENUECAT_WEBHOOK_SECRET>". The app logs in to RevenueCat with the circle's id, so app_user_id is the
// Care Circle. Events can arrive late or out of order, and two Members may each hold a store subscription, so each
// event only says which circle changed: its state is read back from RevenueCat (entitlement "family" is the plan,
// "transcription" the plan with the add-on) and saved.
import { env, rpc } from "../_shared/send.ts";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Entitlement = { expires_date: string; product_identifier: string };

Deno.serve(async (req) => {
  if (!env("REVENUECAT_WEBHOOK_SECRET") || req.headers.get("authorization") !== `Bearer ${env("REVENUECAT_WEBHOOK_SECRET")}`) {
    return json(401, { error: "unauthorized" });
  }
  const circle = (await req.json().catch(() => ({}))).event?.app_user_id ?? "";
  if (!uuid.test(circle)) return json(200, {}); // TEST events and anonymous ids aren't circles

  const res = await fetch(`${env("REVENUECAT_API_URL") || "https://api.revenuecat.com/v1"}/subscribers/${circle}`, {
    headers: { authorization: `Bearer ${env("REVENUECAT_API_KEY")}` },
  });
  if (!res.ok) return json(502, { error: `RevenueCat ${res.status}` }); // RevenueCat retries
  const { subscriber } = await res.json();
  const ents: Record<string, Entitlement> = subscriber.entitlements ?? {};
  const live = (e?: Entitlement) => !!e && Date.parse(e.expires_date) > Date.now();
  // The add-on while it lasts, else whichever runs (or ran) longest.
  const main = live(ents.transcription) ? ents.transcription
    : [ents.family, ents.transcription].filter(Boolean).sort((a, b) => Date.parse(b.expires_date) - Date.parse(a.expires_date))[0];
  const saved = await rpc("save_subscription", {
    circle,
    transcription: live(ents.transcription),
    trial: subscriber.subscriptions?.[main?.product_identifier]?.period_type === "trial",
    expires_at: main?.expires_date ?? null,
  });
  // An unknown circle (deleted, or a sandbox id) is not RevenueCat's to retry.
  return json(saved.ok || saved.status === 409 ? 200 : 500, saved.body);
});
