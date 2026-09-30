# Langganan lewat RevenueCat, satu per Care Circle

Paywall v3 menjual "Kinfolk Family" dan add-on "Transkripsi & ringkasan kunjungan" per keluarga, bukan per orang. Pembelian lewat Play Store dan App Store dengan harga lokal toko (tier setara $15 dan $8, uji coba 14 hari lewat introductory offer). Kami memakai RevenueCat: satu SDK untuk kedua toko, dan webhook-nya ke Edge Function menulis status langganan ke Care Circle. Member mana pun boleh membayar. Hanya rekaman dan transkripsi yang butuh langganan + add-on; fitur lain gratis.

## Considered Options

- **Play Billing + StoreKit langsung**: tanpa dependensi, tapi validasi struk dua toko harus ditulis dan dirawat sendiri.

## Consequences

- Status langganan tinggal di database (diisi webhook), jadi RLS dan Edge Function bisa memeriksanya tanpa memanggil toko.
- Harga yang tampil di paywall berasal dari toko, bukan dari copy v3 ("$15").
