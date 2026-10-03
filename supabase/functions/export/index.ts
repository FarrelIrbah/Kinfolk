// Export (#33): v3's `export`. POST, as a signed-in Member: builds one PDF from what they may see, stores it, logs it
// on the Timeline and answers the link's token. GET ?t=token, without login: the PDF for 7 days.
// Pages follow ExportContent.pages() in the app (shared/…/data/Exports.kt): each section starts its own page,
// Medications 12 a page, a page per Visit Note (the latest 3), Documents as they are, the rest 1.
// POST {card: true} (#34): "Cetak kartu", Emergency Info's wallet cards and fridge sheet (card.ts), answered as the PDF.
import { PDFDocument, PDFFont, PDFPage, rgb } from "npm:pdf-lib@1.17.1";
import fontkit from "npm:@pdf-lib/fontkit@1.1.1";
import { env, rpc } from "../_shared/send.ts";
import { cardPages } from "./card.ts";

type Med = { name: string; dose: string; schedule: string; note: string; blood_thinner: boolean; active: boolean };
type CheckIn = { day: string; sys: number; dia: number; ate: string; walked: boolean; mood: string };
type Visit = { title: string; provider: string; starts_at: string; notes: string; steps: string[] };
type Data = {
  circle_id: string; name: string; conditions: string; allergies: string;
  contacts: { name: string; relationship: string; phone: string }[];
  medications: Med[]; check_ins: CheckIn[]; visits: Visit[]; documents: { name: string; ext: string; path: string }[];
};

const MEDS_PER_PAGE = 12;
// v3's labels, as on `export`.
const LABELS: Record<string, string> = {
  history: "Riwayat medis & kondisi", meds: "Obat + perubahan terbaru", trends: "Tren tensi & cek malam 30 hari",
  visits: "Ringkasan 3 kunjungan terakhir", allergies: "Alergi & info darurat",
};
const ORDER = ["history", "meds", "trends", "visits", "allergies", "docs"];

const hex = (h: string) => rgb(parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255);
const INK = hex("#22261F"), INK2 = hex("#44463E"), MUTED = hex("#6B6A60"), SAND = hex("#E4DDD0"), GREEN = hex("#2F5D4A"),
  TAN = hex("#C9A77C"), RUST = hex("#C4471F"), SOS = hex("#9E3B1E");
const W = 595, H = 842, M = 56; // A4 in points

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const storage = (path: string, init: RequestInit = {}) =>
  fetch(`${env("SUPABASE_URL")}/storage/v1/object/${path}`, { ...init, headers: { authorization: `Bearer ${env("SUPABASE_SERVICE_ROLE_KEY")}`, ...init.headers } });

const months = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sept", "Okt", "Nov", "Des"];
/** "24 Sept 2026", in WIB like the rest of the app's server text. */
const date = (iso: string) => {
  const d = new Date(new Date(iso).getTime() + 7 * 3600_000);
  return `${d.getUTCDate()} ${months[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
};
/** "+6281234567890" → "0812 3456 7890", as on `contacts`. */
const localPhone = (e164: string) => {
  const d = e164.replace(/\D/g, "").replace(/^62/, "");
  return "0" + [d.slice(0, 3), d.slice(3, 7), d.slice(7)].filter(Boolean).join(" ");
};

type Fonts = { sans: PDFFont; serif: PDFFont };

/** Lines of [text] wrapped to [width] at [size]. */
function wrap(text: string, font: PDFFont, size: number, width: number): string[] {
  return text.split("\n").flatMap((para) => {
    const lines: string[] = [];
    let line = "";
    for (const word of para.split(/\s+/).filter(Boolean)) {
      const next = line ? `${line} ${word}` : word;
      if (line && font.widthOfTextAtSize(next, size) > width) { lines.push(line); line = word; } else line = next;
    }
    return [...lines, line];
  });
}

/** A page with the paper preview's header: name, "Disiapkan untuk …", a rule, the section label. Returns where content starts. */
function page(pdf: PDFDocument, f: Fonts, data: Data, line: string, label: string): [PDFPage, number] {
  const p = pdf.addPage([W, H]);
  p.drawText(data.name, { x: M, y: H - M - 22, size: 22, font: f.serif, color: INK });
  p.drawText(line, { x: M, y: H - M - 42, size: 10, font: f.sans, color: MUTED });
  p.drawLine({ start: { x: M, y: H - M - 58 }, end: { x: W - M, y: H - M - 58 }, thickness: 1, color: SAND });
  p.drawText(label, { x: M, y: H - M - 82, size: 13, font: f.sans, color: INK });
  return [p, H - M - 110];
}

/** Blocks of text one under another, all shrunk together until they fit above the bottom margin ("shrinks to fit"). */
function blocks(p: PDFPage, f: Fonts, top: number, items: { text: string; size: number; color?: ReturnType<typeof rgb>; gap?: number }[]) {
  let scale = 1;
  const height = (s: number) => items.reduce((h, it) => h + wrap(it.text, f.sans, it.size * s, W - 2 * M).length * it.size * s * 1.45 + (it.gap ?? 6) * s, 0);
  while (scale > 0.3 && height(scale) > top - M) scale -= 0.05;
  let y = top;
  for (const it of items) {
    const size = it.size * scale;
    for (const l of wrap(it.text, f.sans, size, W - 2 * M)) {
      y -= size * 1.45;
      p.drawText(l, { x: M, y, size, font: f.sans, color: it.color ?? INK });
    }
    y -= (it.gap ?? 6) * scale;
  }
}

function trends(p: PDFPage, f: Fonts, top: number, ks: CheckIn[]) {
  const avg = (v: (k: CheckIn) => number) => Math.round(ks.reduce((a, k) => a + v(k), 0) / ks.length);
  const last = ks[ks.length - 1];
  p.drawText("Tekanan darah", { x: M, y: top - 14, size: 12, font: f.sans, color: INK });
  p.drawText(`${last.sys}/${last.dia}`, { x: W - M - f.serif.widthOfTextAtSize(`${last.sys}/${last.dia}`, 20), y: top - 16, size: 20, font: f.serif, color: INK });
  // v3's chart: 60–160 over the height, 140 dashed.
  const cx = M, cw = W - 2 * M, ch = 160, cb = top - 40 - ch;
  const y = (v: number) => cb + (v - 60) / 100 * ch;
  const x = (i: number) => ks.length === 1 ? cx + cw : cx + i * cw / (ks.length - 1);
  p.drawLine({ start: { x: cx, y: y(140) }, end: { x: cx + cw, y: y(140) }, thickness: 1, color: SOS, opacity: .6, dashArray: [4, 4] });
  for (const [color, v] of [[GREEN, (k: CheckIn) => k.sys], [TAN, (k: CheckIn) => k.dia]] as const) {
    if (ks.length === 1) p.drawCircle({ x: x(0), y: y(v(ks[0])), size: 2, color });
    for (let i = 1; i < ks.length; i++) p.drawLine({ start: { x: x(i - 1), y: y(v(ks[i - 1])) }, end: { x: x(i), y: y(v(ks[i])) }, thickness: 2, color });
  }
  let ly = cb - 22;
  const legend = (label: string, color: ReturnType<typeof rgb>, lx: number, dashArray?: number[]) => {
    p.drawLine({ start: { x: lx, y: ly + 3 }, end: { x: lx + 12, y: ly + 3 }, thickness: dashArray ? 1 : 2, color, dashArray });
    p.drawText(label, { x: lx + 18, y: ly, size: 9, font: f.sans, color: INK2 });
  };
  legend("Sistolik", GREEN, M); legend("Diastolik", TAN, M + 80); legend("Batas 140", SOS, M + 170, [3, 3]);
  ly -= 22;
  p.drawText(`Rata-rata 30 hari ${avg((k) => k.sys)}/${avg((k) => k.dia)} · ${ks.filter((k) => k.sys >= 140).length} kali 140 ke atas`, { x: M, y: ly, size: 10, font: f.sans, color: INK2 });
  // v3's habit grids: 30 cells, the missing ones sand on the left.
  const habits: [string, (k: CheckIn) => ReturnType<typeof rgb>, (k: CheckIn) => boolean][] = [
    ["Makan malam", (k) => k.ate === "yes" ? GREEN : k.ate === "some" ? TAN : RUST, (k) => k.ate === "yes"],
    ["Berjalan", (k) => k.walked ? GREEN : SAND, (k) => k.walked],
    ["Suasana hati", (k) => k.mood === "good" ? GREEN : k.mood === "okay" ? TAN : RUST, (k) => k.mood === "good"],
  ];
  ly -= 40;
  const cell = (cw - 29 * 2) / 30;
  for (const [label, color, good] of habits) {
    const sum = `${ks.filter(good).length} dari 30 baik`;
    p.drawText(label, { x: M, y: ly, size: 11, font: f.sans, color: INK });
    p.drawText(sum, { x: W - M - f.sans.widthOfTextAtSize(sum, 11), y: ly, size: 11, font: f.sans, color: MUTED });
    const cells = [...Array(30 - ks.length).fill(SAND), ...ks.map(color)];
    cells.forEach((c, i) => p.drawRectangle({ x: M + i * (cell + 2), y: ly - 20, width: cell, height: 12, color: c }));
    ly -= 48;
  }
  p.drawText(date(ks[0].day), { x: M, y: ly + 12, size: 9, font: f.sans, color: MUTED });
  p.drawText(date(last.day), { x: W - M - f.sans.widthOfTextAtSize(date(last.day), 9), y: ly + 12, size: 9, font: f.sans, color: MUTED });
}

async function fonts(pdf: PDFDocument): Promise<Fonts> {
  pdf.registerFontkit(fontkit);
  // ponytail: whole fonts; fontkit's subsetting throws on these variable fonts. Adds ~350 KB to each PDF.
  return {
    sans: await pdf.embedFont(await Deno.readFile(new URL("./instrument_sans.ttf", import.meta.url)), { subset: false }),
    serif: await pdf.embedFont(await Deno.readFile(new URL("./newsreader.ttf", import.meta.url)), { subset: false }),
  };
}

async function build(data: Data, sections: Set<string>, line: string): Promise<PDFDocument> {
  const pdf = await PDFDocument.create();
  const f = await fonts(pdf);
  pdf.setTitle(`${data.name} · ${line}`);
  for (const s of ORDER.filter((s) => sections.has(s))) {
    const label = LABELS[s];
    if (s === "history" && data.conditions.trim()) {
      const [p, top] = page(pdf, f, data, line, label);
      blocks(p, f, top, [{ text: data.conditions, size: 12 }]);
    }
    if (s === "meds") {
      for (let i = 0; i < data.medications.length; i += MEDS_PER_PAGE) {
        const [p, top] = page(pdf, f, data, line, label);
        blocks(p, f, top, data.medications.slice(i, i + MEDS_PER_PAGE).flatMap((m) => [
          { text: m.active ? m.name : `${m.name} (tidak diminum lagi)`, size: 12, color: m.active ? INK : MUTED, gap: 0 },
          { text: [m.dose, m.schedule].filter((t) => t.trim()).join(" · ") || " ", size: 10, color: m.active ? INK2 : MUTED, gap: 0 },
          { text: [m.blood_thinner ? "Pengencer darah." : "", m.note].filter((t) => t.trim()).join(" ") || " ", size: 9, color: m.blood_thinner ? SOS : MUTED, gap: 10 },
        ]));
      }
    }
    if (s === "trends" && data.check_ins.length) {
      const [p, top] = page(pdf, f, data, line, label);
      trends(p, f, top, data.check_ins);
    }
    if (s === "visits") {
      for (const v of data.visits) {
        const [p, top] = page(pdf, f, data, line, label);
        blocks(p, f, top, [
          { text: v.title, size: 13, gap: 0 },
          { text: `${v.provider} · ${date(v.starts_at)}`, size: 10, color: MUTED, gap: 14 },
          ...(v.notes.trim() ? [{ text: v.notes, size: 11, gap: 14 }] : []),
          ...(v.steps.length ? [{ text: "Langkah berikutnya", size: 11, color: MUTED, gap: 2 }, ...v.steps.map((t) => ({ text: `• ${t}`, size: 11, gap: 2 }))] : []),
        ]);
      }
    }
    if (s === "allergies" && (data.allergies.trim() || data.contacts.length)) {
      const [p, top] = page(pdf, f, data, line, label);
      blocks(p, f, top, [
        ...(data.allergies.trim() ? [{ text: "Alergi", size: 10, color: MUTED, gap: 0 }, { text: data.allergies, size: 14, gap: 16 }] : []),
        ...data.contacts.flatMap((c) => [
          { text: c.name, size: 12, gap: 0 },
          { text: [c.relationship, localPhone(c.phone)].filter(Boolean).join(" · "), size: 10, color: MUTED, gap: 10 },
        ]),
      ]);
    }
    if (s === "docs") {
      for (const d of data.documents) {
        const res = await storage(`documents/${d.path}`);
        if (!res.ok) throw new Error(`document ${d.path}: ${res.status}`);
        const bytes = new Uint8Array(await res.arrayBuffer());
        if (d.ext === "PDF") {
          const src = await PDFDocument.load(bytes, { ignoreEncryption: true });
          for (const p of await pdf.copyPages(src, src.getPageIndices())) pdf.addPage(p);
        } else {
          // A photo fills an A4 page inside the margins.
          const img = d.ext === "PNG" ? await pdf.embedPng(bytes) : await pdf.embedJpg(bytes);
          const s = Math.min((W - 2 * M) / img.width, (H - 2 * M) / img.height);
          pdf.addPage([W, H]).drawImage(img, { x: (W - img.width * s) / 2, y: (H - img.height * s) / 2, width: img.width * s, height: img.height * s });
        }
      }
    }
  }
  return pdf;
}

// Plain text: hosted *.supabase.co serves function HTML as text/plain anyway (see EmergencyInfo.kt).
const expired = () => new Response("Tautan ini sudah tidak berlaku", {
  status: 404, headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
});

Deno.serve(async (req) => {
  if (req.method === "GET") {
    const found = await rpc("export_file", { token: new URL(req.url).searchParams.get("t") ?? "" }); // a malformed token is a 400 here
    if (!found.ok || !found.body) return expired();
    const file = await storage(`exports/${found.body}`);
    if (!file.ok) return expired();
    return new Response(file.body, {
      headers: { "content-type": "application/pdf", "content-disposition": "inline; filename=\"kinfolk.pdf\"", "cache-control": "private, no-store", "x-robots-tag": "noindex" },
    });
  }

  const member = req.headers.get("authorization");
  if (!member) return json(401, { error: "Not signed in" });
  const { card, url, recipient_id, prepared_for, line, sections, documents } = await req.json();
  if (card) {
    const info = await rpc("emergency_info_of", { recipient: recipient_id }, member);
    if (!info.ok || !info.body) return json(403, { error: info.body || "Not a member" });
    const pdf = await PDFDocument.create();
    cardPages(pdf, await fonts(pdf), info.body, String(url));
    pdf.setTitle(`Info darurat · ${info.body.name}`);
    return new Response(await pdf.save({ useObjectStreams: false }), { headers: { "content-type": "application/pdf", "cache-control": "no-store" } });
  }
  const found = await rpc("export_data", { recipient: recipient_id, docs: documents ?? [] }, member);
  if (!found.ok || !found.body) return json(403, { error: found.body || "Not a member" });
  const data: Data = found.body;

  const pdf = await build(data, new Set(sections), line ?? "");
  const pages = pdf.getPageCount();
  if (!pages) return json(400, { error: "Nothing to export" });
  const bytes = await pdf.save({ useObjectStreams: false }); // plain objects: the seam tests count pages by "/Type /Page"
  const id = crypto.randomUUID();
  const put = await storage(`exports/${data.circle_id}/${id}.pdf`, { method: "POST", headers: { "content-type": "application/pdf" }, body: bytes });
  if (!put.ok) return json(502, { error: await put.text() });
  const logged = await rpc("log_export", { id, recipient: recipient_id, prepared_for: prepared_for ?? "", pages }, member);
  return logged.ok ? json(200, { token: logged.body }) : json(403, { error: logged.body });
});
