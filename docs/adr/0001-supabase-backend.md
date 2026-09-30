# Supabase sebagai backend, bukan Next.js atau Ktor

Brief awal memakai Next.js + Postgres + S3. Kami memilih Supabase (Postgres + RLS + auth + storage, region Singapore), dan app Compose Multiplatform memakai `supabase-kt`. Alasannya: model izin (keanggotaan Care Circle, Role, Data Category) harus ditegakkan di database lewat RLS, dan Supabase memberikannya tanpa server sendiri. Stack jadi satu bahasa di sisi app. Logika server yang tersisa (webhook WhatsApp/SMS, halaman Emergency Info) ditulis sebagai Edge Functions.

## Considered Options

- **Next.js** (brief): menambah bahasa dan runtime kedua di samping Kotlin.
- **Ktor + Postgres sendiri**: model domain bisa dipakai bersama dengan app, tapi auth, storage, dan deploy harus dibangun sendiri. Pindah ke sini kalau Edge Functions terbukti tidak cukup.

## Consequences

- Semua aturan akses tinggal di policy RLS. App tidak boleh menjadi satu-satunya penjaga akses.
- Data kesehatan tersimpan di luar Indonesia (Singapore). Periksa lagi kalau UU PDP atau regulasi kesehatan mewajibkan lokalisasi data.
