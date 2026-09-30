// Thin web page behind the WhatsApp invitation link: accept without installing the app. Layout and values from the
// v3 `invitee` screen; button and result copy approved by the owner in #4 (docs/screen-map.md).
import { env, rpc } from "../_shared/send.ts";

const escape = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

function page(head: string, sub: string | null, action: string) {
  return new Response(`<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Kinfolk</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400;500;600&family=Newsreader:opsz,wght@6..72,500;6..72,600&display=swap">
<style>
body{margin:0;background:#F3EEE4;color:#22261F;font-family:'Instrument Sans',system-ui,sans-serif}
main{max-width:440px;margin:0 auto;padding:24px 20px 30px;box-sizing:border-box;display:flex;flex-direction:column;gap:18px}
.brand{font:600 20px 'Newsreader',Georgia,serif}
.head{display:flex;flex-direction:column;gap:8px}
h1{margin:0;font:500 32px/1.1 'Newsreader',Georgia,serif}
p{margin:0;font-size:15px;line-height:1.5;color:#44463E}
form{margin:0;display:flex}
button{flex:1;height:54px;border:none;border-radius:16px;background:#2F5D4A;color:#F3EEE4;font:600 16px 'Instrument Sans',system-ui,sans-serif;cursor:pointer}
.done{font-size:14px;font-weight:600;color:#2F5D4A}
</style></head>
<body><main>
<div class="brand">Kinfolk</div>
<div class="head"><h1>${escape(head)}</h1>${sub ? `<p>${escape(sub)}</p>` : ""}</div>
${action}
</main></body></html>`, { headers: { "content-type": "text/html; charset=utf-8" } });
}

Deno.serve(async (req) => {
  const link = new URL(req.url).searchParams.get("t") ?? "";
  const found = await rpc("invitation_card", { link }); // a malformed link is a 400 here
  const card = found.ok ? found.body[0] : undefined;
  const welcome = (action: string) =>
    page(`Selamat datang, ${card.name}`, `${card.inviter} menambahkan Anda ke lingkaran perawatan ${card.circle}. Ini keadaan terkini.`, action);
  const joined = () => welcome(`<div class="done">Anda sudah bergabung. Kabar berikutnya dikirim lewat WhatsApp.</div>`);
  if (req.method === "POST" && card?.accepted) return joined(); // a double tap or a re-sent form
  if (!card?.pending) return page("Undangan ini sudah tidak berlaku", null, "");

  if (req.method !== "POST") {
    return welcome(`<form method="post" action="?t=${escape(link)}"><button>Terima undangan</button></form>`);
  }
  // Someone who never signed in gets their Auth user now, so signing in later with this number lands in the circle.
  const service = env("SUPABASE_SERVICE_ROLE_KEY");
  await fetch(`${env("SUPABASE_URL")}/auth/v1/admin/users`, {
    method: "POST",
    headers: { apikey: service, authorization: `Bearer ${service}`, "content-type": "application/json" },
    body: JSON.stringify({ phone: card.phone, phone_confirm: true }),
  }); // already registered: fine
  const accepted = await rpc("accept_invitation_link", { link });
  if (!accepted.ok || !accepted.body) return page("Undangan ini sudah tidak berlaku", null, "");
  return joined();
});
