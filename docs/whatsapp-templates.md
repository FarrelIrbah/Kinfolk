# Template WhatsApp

Semua pesan keluar lewat WhatsApp Business Cloud API memakai template yang disetujui Meta (ADR 0002).

## `kinfolk_otp`: kode masuk

Kategori AUTHENTICATION, bahasa `id`. Isi template otentikasi ditetapkan Meta (teks kode verifikasi + saran keamanan + masa berlaku + tombol salin kode), tidak bisa diubah. Dikirim oleh Edge Function `send-otp`.

Ajukan sekali per WhatsApp Business Account:

```sh
curl -X POST "https://graph.facebook.com/v23.0/$WABA_ID/message_templates" \
  -H "Authorization: Bearer $WHATSAPP_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "kinfolk_otp", "language": "id", "category": "AUTHENTICATION",
    "components": [
      { "type": "BODY", "add_security_recommendation": true },
      { "type": "FOOTER", "code_expiration_minutes": 10 },
      { "type": "BUTTONS", "buttons": [{ "type": "OTP", "otp_type": "COPY_CODE" }] }
    ]
  }'
```

SMS cadangan (Twilio), copy disetujui pemilik di #3: `Kode masuk Kinfolk Anda: 123456. Jangan bagikan kode ini ke siapa pun.`

## `kinfolk_invite`: undangan

Kategori UTILITY, bahasa `id`. Dikirim oleh Edge Function `invite`; copy disetujui pemilik di #4. Parameter: nama pengundang, nama Care Circle, tautan halaman web undangan.

```sh
curl -X POST "https://graph.facebook.com/v23.0/$WABA_ID/message_templates"   -H "Authorization: Bearer $WHATSAPP_TOKEN" -H "Content-Type: application/json"   -d '{
    "name": "kinfolk_invite", "language": "id", "category": "UTILITY",
    "components": [{
      "type": "BODY",
      "text": "{{1}} mengundang Anda ke lingkaran perawatan {{2}} di Kinfolk. Buka tautan ini untuk bergabung, tanpa perlu pasang app: {{3}}",
      "example": { "body_text": [["Sri", "Tukiman", "https://kinfolk.id/undangan?t=contoh"]] }
    }]
  }'
```

SMS cadangan memakai teks yang sama.

## Notifikasi dan balasan (#13)

Kategori UTILITY, bahasa `id`, disetujui pemilik di #13 (bentuk pesan `swap`, `yesR`, `med` di layar `sms` prototype, disesuaikan: giliran mingguan, WhatsApp, nama dari data). Isi template juga ada di `template_body()` (migrasi `20261001000000_whatsapp.sql`); SMS cadangan memakai teks yang sama, terisi. Dikirim oleh Edge Function `whatsapp` tiap menit.

| Template | Isi | Kapan, ke siapa |
|---|---|---|
| `kinfolk_swap_ask` | `{{1}} bertanya: bisa ambil {{2}} minggu {{3}}? Balas YA atau TIDAK.` | Minta tukar, ke yang diminta. Contoh: "Sri", "telepon cek malam", "28 Sept – 4 Okt, 19.00" |
| `kinfolk_swap_yes` | `{{1}} pegang {{2}} minggu ini.` | Ke peminta, setelah diterima (di app atau WhatsApp) |
| `kinfolk_swap_no` | `{{1}} tidak bisa ambil {{2}} minggu ini.` | Ke peminta, setelah ditolak |
| `kinfolk_drive_ask` | `{{1}} bertanya: bisa mengantar {{2}} ke {{3}}, {{4}}? Balas YA atau TIDAK.` | Member lain menjadikan dia Driver. Contoh: "Sri", "Tukiman", "Kontrol neurologi", "Kam, 1 Okt · 09.00, berangkat 08.15" |
| `kinfolk_drive_yes` | `{{1}} mengantar {{2}} {{3}}.` | Ke yang menugaskan, setelah YA |
| `kinfolk_drive_no` | `{{1}} tidak bisa mengantar {{2}} {{3}}.` | Ke yang menugaskan, setelah TIDAK (Driver dilepas) |
| `kinfolk_drive_reminder` | `Hari ini: antar {{1}} ke {{2}} jam {{3}}.` | Driver, 2 jam sebelum berangkat (atau jam janji). {{3}}: "09.00, berangkat 08.15. Bawa: KTP, kartu BPJS" |
| `kinfolk_duty_reminder` | `Hari ini: {{1}} jam {{2}}.` | Pemegang Duty, 1 jam sebelum jamnya, tiap hari minggunya |
| `kinfolk_visit_note` | `{{1}} menulis catatan kunjungan {{2}}. {{3}}.` | Visit Note pertama kali tersimpan, ke Member yang boleh membacanya (bukan penulisnya). {{3}} = teks Linimasa, mis. "Kontrol neurologi: fisioterapi 2x/minggu, MRI ulang 3 bulan lagi" |

`kinfolk_swap_ask` dan `kinfolk_drive_ask` punya dua tombol QUICK_REPLY "YA" dan "TIDAK" (payload diisi saat kirim); mengetik "ya"/"tidak" juga dihitung, untuk permintaan terbaru yang masih terbuka. Kalau Meta menerima pesan tapi kemudian melaporkan gagal kirim (status `failed` di webhook, mis. nomor tanpa WhatsApp), teksnya dikirim lewat SMS. Balasan bebas (dalam 24 jam):

- Tukar YA: `Terima kasih, Budi. Anda pegang telepon cek malam minggu ini. Sri sudah diberi tahu.`
- Tukar TIDAK: `Ditolak. Sri akan bertanya ke yang lain.`
- Antar YA: `Terima kasih, Budi. Anda mengantar Tukiman Kam, 1 Okt · 09.00. Sri sudah diberi tahu.`
- Antar TIDAK: `Tidak apa-apa. Sri akan bertanya ke yang lain.`
- Lewat 24 jam, sudah dijawab, atau tidak berlaku lagi: `Permintaan ini sudah tidak berlaku.`
- Teks lain: tidak dibalas. Balasan SMS tidak diproses.

Notifikasi tidak dikirim kalau penerima tidak boleh melihat Data Category-nya: permintaan dan pengingat antar butuh "Janji dokter", catatan kunjungan butuh "Janji dokter" dan "Catatan kunjungan".

Contoh pengajuan (tombol hanya untuk dua template permintaan):

```sh
curl -X POST "https://graph.facebook.com/v23.0/$WABA_ID/message_templates"   -H "Authorization: Bearer $WHATSAPP_TOKEN" -H "Content-Type: application/json"   -d '{
    "name": "kinfolk_swap_ask", "language": "id", "category": "UTILITY",
    "components": [
      { "type": "BODY", "text": "{{1}} bertanya: bisa ambil {{2}} minggu {{3}}? Balas YA atau TIDAK.",
        "example": { "body_text": [["Sri", "telepon cek malam", "28 Sept – 4 Okt, 19.00"]] } },
      { "type": "BUTTONS", "buttons": [{ "type": "QUICK_REPLY", "text": "YA" }, { "type": "QUICK_REPLY", "text": "TIDAK" }] }
    ]
  }'
```

## Produksi

0. `npx supabase link --project-ref <project>` lalu `npx supabase db push` (semua migrasi).
1. Deploy `send-otp` tanpa verifikasi JWT (Auth memanggilnya dengan tanda tangan webhook): `npx supabase functions deploy send-otp --no-verify-jwt`. Deploy juga `invite` dan `invitation` dengan `--no-verify-jwt` (database yang memeriksa admin; halaman web dibuka tanpa login).
2. Dashboard → Authentication → Hooks → Send SMS: HTTPS ke `https://<project>.supabase.co/functions/v1/send-otp`, buat secret.
3. Dashboard → Authentication → Phone: aktifkan, masa berlaku OTP 600 detik (sama dengan footer template).
4. `npx supabase secrets set SEND_SMS_HOOK_SECRET=… WHATSAPP_PHONE_NUMBER_ID=… WHATSAPP_TOKEN=… TWILIO_ACCOUNT_SID=… TWILIO_AUTH_TOKEN=… TWILIO_FROM=… INVITATION_URL=…`
5. Webhook: deploy `whatsapp` dengan `--no-verify-jwt`; di Meta App → WhatsApp → Configuration, callback `https://<project>.supabase.co/functions/v1/whatsapp`, verify token = `WHATSAPP_VERIFY_TOKEN`, langganan field `messages`. `npx supabase secrets set WHATSAPP_APP_SECRET=… WHATSAPP_VERIFY_TOKEN=… NOTIFY_SECRET=…`
6. Tiap menit (SQL editor, sekali; butuh ekstensi `pg_cron` dan `pg_net`): `select cron.schedule('whatsapp', '* * * * *', $$ select net.http_post('https://<project>.supabase.co/functions/v1/whatsapp', '{}'::jsonb, headers := '{"authorization": "Bearer <NOTIFY_SECRET>", "content-type": "application/json"}'::jsonb) $$);`
7. `INVITATION_URL` harus alamat domain sendiri yang meneruskan ke fungsi `invitation`: di domain `*.supabase.co` Edge Function tidak boleh menyajikan HTML (tampil sebagai teks biasa).
8. Deploy `emergency` dengan `--no-verify-jwt` (halaman QR dibuka paramedis tanpa login). Sama seperti `invitation`, butuh alamat domain sendiri yang meneruskan ke fungsi `emergency`; app memakainya untuk tautan QR.
9. App: isi `kinfolk.supabaseUrl`, `kinfolk.publishableKey`, dan `kinfolk.emergencyUrl` di `local.properties` (lihat README), lalu build ulang.
