// An admin invites someone by phone: saves the Invitation as the admin (the database checks they may), then sends the
// link to the invitation web page over WhatsApp (template `kinfolk_invite`), or SMS when WhatsApp refuses it.
// Inviting a number that's still pending sends the same link again.
import { env, rpc, viaSms, viaWhatsApp } from "../_shared/send.ts";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req) => {
  const admin = req.headers.get("authorization");
  if (!admin) return json(401, { error: "Not signed in" });
  const { circle_id, name, phone, role, recipient_id } = await req.json();
  const saved = await rpc("invite", { circle: circle_id, invitee_name: name, invitee_phone: phone, invitee_role: role ?? "sibling", recipient: recipient_id ?? null }, admin);
  if (!saved.ok) return json(403, { error: saved.body });

  const card = (await rpc("invitation_card", { invitation: saved.body })).body[0];
  const link = `${env("INVITATION_URL")}?t=${card.link}`;
  const to = card.phone.replace(/\D/g, "");
  // Copy approved by the owner in #4 (docs/whatsapp-templates.md).
  const sent = await viaWhatsApp(to, "kinfolk_invite", [
    { type: "body", parameters: [card.inviter, card.circle, link].map((text) => ({ type: "text", text })) },
  ]) || await viaSms(to, `${card.inviter} mengundang Anda ke lingkaran perawatan ${card.circle} di Kinfolk. Buka tautan ini untuk bergabung, tanpa perlu pasang app: ${link}`);
  return sent ? json(200, { id: saved.body }) : json(502, { error: "Could not send the invitation" });
});
