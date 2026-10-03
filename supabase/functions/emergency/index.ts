// Emergency Info page behind the QR card (ADR 0003): no login, only the fixed fields. Layout and values from the v3
// `emergency` screen; empty sections hidden and the revoked-link heading approved by the owner in #12 (docs/screen-map.md).
import { rpc } from "../_shared/send.ts";
import { ageLine, bloodLine, type Info, sub } from "../_shared/emergency.ts";

const escape = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

const tile = (label: string, value: string, cls: string) =>
  value ? `<div class="tile ${cls}"><div class="label">${label}</div><div class="value">${escape(value)}</div></div>` : "";

function body(info: Info | null) {
  if (!info) return `<div class="eyebrow">Info darurat</div><h1>Tautan ini sudah tidak berlaku</h1>`;
  const contacts = info.contacts.map((c) => `<div class="contact"><div class="who"><div class="name">${escape(c.name)}</div>` +
    `<div class="sub">${escape(sub(c))}</div></div>` +
    `<a class="call" href="tel:${escape(c.phone)}">Telepon</a></div>`).join("");
  const age = ageLine(info), blood = bloodLine(info);
  const pair = tile("Alergi", info.allergies, "big") + tile("Keinginan", info.wishes, "big");
  return `<div class="eyebrow">Info darurat</div><div class="who-is"><h1>${escape(info.name)}</h1>` +
    (age ? `<div class="age">${escape(age)}</div>` : "") + `</div>` +
    (blood ? `<div class="blood">${escape(blood)}</div>` : "") +
    (pair ? `<div class="pair">${pair}</div>` : "") + tile("Kondisi", info.conditions, "text") +
    tile("Obat saat ini", info.medications.join(" · "), "text meds") +
    (contacts ? `<div class="contacts">${contacts}</div>` : "");
}

function page(info: Info | null) {
  // ponytail: fonts load without blocking the first paint (media=print swap); the page reads fine in the fallbacks on bad signal.
  return `<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><meta name="referrer" content="no-referrer">
<title>Info darurat</title>
<link rel="stylesheet" media="print" onload="this.media='all'" href="https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400;500;600&family=Newsreader:opsz,wght@6..72,500&display=swap">
<style>
body{margin:0;background:#15130F;color:#FFF8EE;font-family:'Instrument Sans',system-ui,sans-serif}
main{max-width:440px;margin:0 auto;padding:24px 20px 40px;box-sizing:border-box;display:flex;flex-direction:column;gap:14px}
.eyebrow{font-size:12px;font-weight:600;letter-spacing:.1em;text-transform:uppercase;color:#F2A27C}
h1{margin:0;font:500 40px/1.05 'Newsreader',Georgia,serif}
.who-is{display:flex;flex-direction:column;gap:2px}
.age{font-size:17px;opacity:.85}
.blood{background:#C4471F;border-radius:14px;padding:14px 16px;font-size:19px;font-weight:600;line-height:1.3}
.pair{display:flex;gap:8px}.pair>.tile{flex:1;min-width:0}
.tile{background:rgba(255,248,238,.08);border-radius:14px;padding:14px;display:flex;flex-direction:column;gap:4px}
.label{font-size:12px;opacity:.7}
.big .value{font-size:18px;font-weight:600}
.text{padding:14px 16px;gap:6px}
.text .value{font-size:16px;line-height:1.45;white-space:pre-line}
.meds .value{line-height:1.55}
.contacts{display:flex;flex-direction:column;gap:8px}
.contact{display:flex;align-items:center;gap:12px;background:rgba(255,248,238,.08);border-radius:14px;padding:12px 12px 12px 16px}
.who{flex:1;display:flex;flex-direction:column;gap:2px}
.name{font-size:16px;font-weight:600}
.sub{font-size:13px;opacity:.7}
.call{display:flex;align-items:center;border-radius:999px;background:#FFF8EE;color:#15130F;height:44px;padding:0 18px;font-size:14px;font-weight:600;text-decoration:none}
</style></head>
<body><main>${body(info)}</main></body></html>`;
}

Deno.serve(async (req) => {
  const token = new URL(req.url).searchParams.get("t") ?? "";
  // ponytail: known link-preview fetchers by user agent; a scan that slips through only moves "terakhir dipindai".
  const preview = /whatsapp|facebookexternalhit|telegrambot|slackbot|twitterbot|discordbot|bot\b/i.test(req.headers.get("user-agent") ?? "");
  const found = await rpc("emergency_info", { token, scanned: !preview }); // a malformed token is a 400 here
  const info: Info | null = found.ok ? found.body : null;
  const html = page(info);
  // Cacheable, but always revalidated, so a revoked link dies at once; an unchanged page costs a 304.
  const etag = `"${[...new Uint8Array(await crypto.subtle.digest("SHA-1", new TextEncoder().encode(html)))].map((b) => b.toString(16).padStart(2, "0")).join("")}"`;
  const headers = { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache", etag };
  if (info && req.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers });
  return new Response(html, { status: info ? 200 : 404, headers });
});
