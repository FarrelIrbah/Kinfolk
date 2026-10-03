// Emergency Info as public.emergency_summary returns it, and its lines in the app's words (EmergencyInfo.kt), for the
// QR page and the printed card.

export type Contact = { name: string; relationship: string; distance: string; phone: string };
export type Info = {
  name: string; born_on: string | null; weight_kg: number | null; allergies: string; wishes: string; conditions: string;
  blood_thinners: string[]; medications: string[]; contacts: Contact[];
};

const months = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sept", "Okt", "Nov", "Des"];

/** "+6281234567890" → "0812 3456 7890", as on `contacts`. */
export const localPhone = (e164: string) => {
  const d = e164.replace(/\D/g, "").replace(/^62/, "");
  return "0" + [d.slice(0, 3), d.slice(3, 7), d.slice(7)].filter(Boolean).join(" ");
};

/** "78 · lahir 12 Mar 1948 · 64 kg", aged as of today in WIB; "" when neither is filled in. */
export function ageLine(info: Info, now = new Date()): string {
  const parts: string[] = [];
  if (info.born_on) {
    const [y, m, d] = info.born_on.split("-").map(Number);
    const t = new Date(now.getTime() + 7 * 3600_000);
    const age = t.getUTCFullYear() - y - (t.getUTCMonth() + 1 < m || (t.getUTCMonth() + 1 === m && t.getUTCDate() < d) ? 1 : 0);
    parts.push(`${age} · lahir ${d} ${months[m - 1]} ${y}`);
  }
  if (info.weight_kg) parts.push(`${info.weight_kg} kg`);
  return parts.join(" · ");
}

/** "Minum pengencer darah: Clopidogrel, Warfarin"; "" without one. */
export const bloodLine = (info: Info) => info.blood_thinners.length ? `Minum pengencer darah: ${info.blood_thinners.join(", ")}` : "";

/** "Anak · 10 menit · 0812 3456 7890" */
export const sub = (c: Contact) => [c.relationship, c.distance, localPhone(c.phone)].filter(Boolean).join(" · ");
