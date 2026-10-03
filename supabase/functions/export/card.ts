// "Cetak kartu" (#34): Emergency Info on A4, approved by the owner in #34. Page 1: two v3 `qr` cards at wallet size
// (85.6 × 54 mm) with dashed cut lines. Page 2, for the fridge: the v3 `emergency` layout in dark ink on white paper,
// without "Tutup", "Ubah", "Telepon" and the offline line, its QR tile last. Sizes are the prototype's px times a scale.
import { PDFDocument, PDFFont, PDFPage, rgb } from "npm:pdf-lib@1.17.1";
import qrcode from "npm:qrcode-generator@1.4.4";
import { ageLine, bloodLine, type Info, sub } from "../_shared/emergency.ts";

type Fonts = { sans: PDFFont; serif: PDFFont };
type Color = ReturnType<typeof rgb>;

const hex = (h: string) => rgb(parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255);
const NIGHT = hex("#15130F"), CREAM = hex("#FFF8EE"), PEACH = hex("#F2A27C"), RED = hex("#C4471F"), CUT = hex("#6B6A60");
const W = 595, H = 842, M = 56; // A4 in points

/** A rounded rectangle with its top-left corner at (x, top). */
function box(p: PDFPage, x: number, top: number, w: number, h: number, r: number, color: Color, opacity = 1) {
  const path = `M ${r} 0 H ${w - r} A ${r} ${r} 0 0 1 ${w} ${r} V ${h - r} A ${r} ${r} 0 0 1 ${w - r} ${h} H ${r} ` +
    `A ${r} ${r} 0 0 1 0 ${h - r} V ${r} A ${r} ${r} 0 0 1 ${r} 0 Z`;
  p.drawSvgPath(path, { x, y: top, color, opacity, borderWidth: 0 });
}

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

/** Wrapped text from [top] down; returns its height. */
function text(p: PDFPage, s: string, font: PDFFont, size: number, lh: number, x: number, top: number, width: number, color: Color, opacity = 1) {
  const lines = wrap(s, font, size, width);
  lines.forEach((l, i) => p.drawText(l, { x, y: top - size * lh * (i + 1) + size * (lh - 1) / 2 + size * .22, size, font, color, opacity }));
  return lines.length * size * lh;
}
const height = (s: string, font: PDFFont, size: number, lh: number, width: number) => wrap(s, font, size, width).length * size * lh;

/** "INFO DARURAT": uppercase, letter-spacing .1em. */
function eyebrow(p: PDFPage, f: Fonts, x: number, top: number, size: number, color: Color) {
  let cx = x;
  for (const ch of "INFO DARURAT") {
    p.drawText(ch, { x: cx, y: top - size, size, font: f.sans, color });
    cx += f.sans.widthOfTextAtSize(ch, size) + size * .1;
  }
}

/** The card's QR, [size] square, dark modules on a cream square. */
function qr(p: PDFPage, url: string, x: number, top: number, size: number) {
  const q = qrcode(0, "M");
  q.addData(url);
  q.make();
  const n = q.getModuleCount(), cell = size / n;
  // One rectangle per run of dark modules in a row: a third of the drawing, under the worker's CPU limit.
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
    if (!q.isDark(r, c)) continue;
    let end = c;
    while (end + 1 < n && q.isDark(r, end + 1)) end++;
    p.drawRectangle({ x: x + c * cell, y: top - (r + 1) * cell, width: (end - c + 1) * cell + .02, height: cell + .02, color: NIGHT });
    c = end;
  }
}

/** v3 `qr`'s card, 362 px wide in the prototype, at wallet size with its top-left at (x, top). */
function card(p: PDFPage, f: Fonts, info: Info, url: string, x: number, top: number) {
  const w = 85.6 / 25.4 * 72, h = w / 1.586, k = w / 362;
  p.drawRectangle({ x: x - 6, y: top - h - 6, width: w + 12, height: h + 12, borderColor: CUT, borderWidth: .5, borderDashArray: [3, 3] });
  box(p, x, top, w, h, 16 * k, NIGHT);
  const pad = 16 * k, qrBox = 96 * k + 12 * k, left = w - 2 * pad - 12 * k - qrBox;
  // Left: eyebrow, name, age line at the top; blood thinner banner and allergies at the bottom.
  eyebrow(p, f, x + pad, top - pad, 10 * k, PEACH);
  let y = top - pad - 10 * k * 1.3 - 3 * k;
  y -= text(p, info.name, f.serif, 24 * k, 1.1, x + pad, y, left, CREAM);
  const age = ageLine(info);
  if (age) text(p, age, f.sans, 11 * k, 1.3, x + pad, y, left, CREAM, .8);
  const blood = bloodLine(info), allergy = info.allergies ? `Alergi: ${info.allergies}` : "";
  let bottom = top - h + pad;
  if (allergy) {
    const ah = height(allergy, f.sans, 11 * k, 1.3, left);
    text(p, allergy, f.sans, 11 * k, 1.3, x + pad, bottom + ah, left, CREAM);
    bottom += ah + (blood ? 5 * k : 0);
  }
  if (blood) {
    const bh = height(blood, f.sans, 11 * k, 1.3, left - 16 * k) + 10 * k;
    box(p, x + pad, bottom + bh, left, bh, 6 * k, RED);
    text(p, blood, f.sans, 11 * k, 1.3, x + pad + 8 * k, bottom + bh - 5 * k, left - 16 * k, CREAM);
  }
  // Right: the QR on cream, "Pindai · tanpa login" under it, centred.
  const scan = "Pindai · tanpa login", col = h - 2 * pad, block = qrBox + 6 * k + 10 * k * 1.3;
  const qx = x + w - pad - qrBox, qtop = top - pad - (col - block) / 2;
  box(p, qx, qtop, qrBox, qrBox, 6 * k, CREAM);
  qr(p, url, qx + 6 * k, qtop - 6 * k, 96 * k);
  p.drawText(scan, { x: qx + (qrBox - f.sans.widthOfTextAtSize(scan, 10 * k)) / 2, y: qtop - qrBox - 6 * k - 10 * k, size: 10 * k, font: f.sans, color: CREAM, opacity: .75 });
  return h;
}

type Block = { h: (k: number) => number; draw: (k: number, top: number) => void };

/** v3 `emergency`, 362 px of content in the prototype, on white: blocks 14 px apart, all shrunk together to fit the page. */
function fridge(p: PDFPage, f: Fonts, info: Info, url: string) {
  const width = W - 2 * M, x = M;
  const tileText = (label: string, value: string, size: number, lh: number, padX: number, gap: number, k: number, w = width) => ({
    h: 14 * k * 2 + 12 * k * 1.3 + gap * k + height(value, f.sans, size * k, lh, w - 2 * padX * k),
    draw: (top: number, tx = x) => {
      const th = 14 * k * 2 + 12 * k * 1.3 + gap * k + height(value, f.sans, size * k, lh, w - 2 * padX * k);
      box(p, tx, top, w, th, 14 * k, NIGHT, .08);
      text(p, label, f.sans, 12 * k, 1.3, tx + padX * k, top - 14 * k, w - 2 * padX * k, NIGHT, .7);
      text(p, value, f.sans, size * k, lh, tx + padX * k, top - 14 * k - 12 * k * 1.3 - gap * k, w - 2 * padX * k, NIGHT);
    },
  });
  const age = ageLine(info), blood = bloodLine(info), meds = info.medications.join(" · ");
  const pair = [["Alergi", info.allergies], ["Keinginan", info.wishes]].filter(([, v]) => v);
  const blocks: Block[] = [
    { h: (k) => 12 * k * 1.3, draw: (k, top) => eyebrow(p, f, x, top, 12 * k, PEACH) },
    {
      h: (k) => height(info.name, f.serif, 40 * k, 1.05, width) + (age ? 2 * k + 17 * k * 1.3 : 0),
      draw: (k, top) => {
        const nh = text(p, info.name, f.serif, 40 * k, 1.05, x, top, width, NIGHT);
        if (age) text(p, age, f.sans, 17 * k, 1.3, x, top - nh - 2 * k, width, NIGHT, .85);
      },
    },
  ];
  if (blood) blocks.push({
    h: (k) => 28 * k + height(blood, f.sans, 19 * k, 1.3, width - 32 * k),
    draw: (k, top) => {
      box(p, x, top, width, 28 * k + height(blood, f.sans, 19 * k, 1.3, width - 32 * k), 14 * k, RED);
      text(p, blood, f.sans, 19 * k, 1.3, x + 16 * k, top - 14 * k, width - 32 * k, CREAM);
    },
  });
  if (pair.length) blocks.push({
    h: (k) => Math.max(...pair.map(([l, v]) => tileText(l, v, 18, 1.3, 14, 4, k, (width - 8 * k * (pair.length - 1)) / pair.length).h)),
    draw: (k, top) => {
      const w = (width - 8 * k * (pair.length - 1)) / pair.length;
      pair.forEach(([l, v], i) => tileText(l, v, 18, 1.3, 14, 4, k, w).draw(top, x + i * (w + 8 * k)));
    },
  });
  if (info.conditions) blocks.push({ h: (k) => tileText("Kondisi", info.conditions, 16, 1.45, 16, 6, k).h, draw: (k, top) => tileText("Kondisi", info.conditions, 16, 1.45, 16, 6, k).draw(top) });
  if (meds) blocks.push({ h: (k) => tileText("Obat saat ini", meds, 16, 1.55, 16, 6, k).h, draw: (k, top) => tileText("Obat saat ini", meds, 16, 1.55, 16, 6, k).draw(top) });
  for (const c of info.contacts) {
    const line = sub(c), rowH = (k: number) => 24 * k + 16 * k * 1.3 + 2 * k + 13 * k * 1.3;
    blocks.push({
      h: rowH,
      draw: (k, top) => {
        box(p, x, top, width, rowH(k), 14 * k, NIGHT, .08);
        text(p, c.name, f.sans, 16 * k, 1.3, x + 16 * k, top - 12 * k, width - 28 * k, NIGHT);
        text(p, line, f.sans, 13 * k, 1.3, x + 16 * k, top - 12 * k - 16 * k * 1.3 - 2 * k, width - 28 * k, NIGHT, .7);
      },
    });
  }
  // The QR tile: 72 px code, "QR untuk paramedis" and its sub beside it.
  const title = "QR untuk paramedis", qsub = "Membuka halaman ini tanpa login. Untuk dompet dan kulkas.";
  blocks.push({
    h: (k) => 28 * k + 72 * k,
    draw: (k, top) => {
      box(p, x, top, width, 28 * k + 72 * k, 14 * k, NIGHT, .08);
      qr(p, url, x + 14 * k, top - 14 * k, 72 * k);
      const tx = x + 14 * k + 72 * k + 14 * k, tw = width - (tx - x) - 14 * k;
      const th = 16 * k * 1.3 + 4 * k + height(qsub, f.sans, 13 * k, 1.4, tw);
      const ty = top - 14 * k - (72 * k - th) / 2;
      text(p, title, f.sans, 16 * k, 1.3, tx, ty, tw, NIGHT);
      text(p, qsub, f.sans, 13 * k, 1.4, tx, ty - 16 * k * 1.3 - 4 * k, tw, NIGHT, .75);
    },
  });
  const total = (k: number) => blocks.reduce((a, b) => a + b.h(k), 0) + 14 * k * (blocks.length - 1);
  let k = width / 362;
  while (k > .5 && total(k) > H - 2 * M) k -= .02;
  let top = H - M;
  for (const b of blocks) { b.draw(k, top); top -= b.h(k) + 14 * k; }
}

/** The two pages; [f]'s variable fonts print at their default weight. */
export function cardPages(pdf: PDFDocument, f: Fonts, info: Info, url: string) {
  const cards = pdf.addPage([W, H]);
  const w = 85.6 / 25.4 * 72, x = (W - w) / 2;
  const h = card(cards, f, info, url, x, H - M);
  card(cards, f, info, url, x, H - M - h - 36);
  fridge(pdf.addPage([W, H]), f, info, url);
}
