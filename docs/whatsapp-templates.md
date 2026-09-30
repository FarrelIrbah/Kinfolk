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

## Produksi

1. Deploy `send-otp` tanpa verifikasi JWT (Auth memanggilnya dengan tanda tangan webhook): `npx supabase functions deploy send-otp --no-verify-jwt`.
2. Dashboard → Authentication → Hooks → Send SMS: HTTPS ke `https://<project>.supabase.co/functions/v1/send-otp`, buat secret.
3. Dashboard → Authentication → Phone: aktifkan, masa berlaku OTP 600 detik (sama dengan footer template).
4. `npx supabase secrets set SEND_SMS_HOOK_SECRET=… WHATSAPP_PHONE_NUMBER_ID=… WHATSAPP_TOKEN=… TWILIO_ACCOUNT_SID=… TWILIO_AUTH_TOKEN=… TWILIO_FROM=…`
